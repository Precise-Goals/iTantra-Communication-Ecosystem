#!/usr/bin/env python3
"""
iTantra — weight-only INT8 for VITS TTS voices (T79)

Stores every Conv/ConvTranspose weight as int8 (symmetric, per output channel) behind a
DequantizeLinear node. Activations stay FP32, so there is no ConvInteger in the graph: the runtime
turns the weights back into float and runs the normal FP32 kernels.

Why not `quantize_dynamic`: it rewrites every Conv to ConvInteger, which onnxruntime's CPU
provider runs ~3x slower than FP32 on these graphs (measured 2026-09-26, see
docs/evaluation/tts-quant/README.md). Weight-only keeps FP32 speed and still cuts the file ~4x.
The trade-off is RAM: weights are float again once loaded, so resident memory is FP32-sized.

Usage:
    python weight_only_int8.py <src_dir>/model.onnx <dst_dir> [min_elems=4096] [keep_fp32_prefixes]
e.g. keep_fp32_prefixes="/dp/" keeps the VITS duration predictor (2.2 MB) in FP32.
Copies <src_dir>/tokens.txt next to the output. Piper voices also need their espeak-ng-data/
directory copied by hand (the app ships it as a separate pack).
"""

import os
import shutil
import sys

import numpy as np
import onnx
from onnx import helper, numpy_helper


def convert(src: str, dst_dir: str, min_elems: int = 4096, keep_fp32: tuple = ()) -> None:
    """keep_fp32: node-name prefixes whose weights stay FP32 (e.g. "/dp/" = duration predictor)."""
    os.makedirs(dst_dir, exist_ok=True)
    tokens = os.path.join(os.path.dirname(src), "tokens.txt")
    if os.path.exists(tokens):
        shutil.copy(tokens, dst_dir)

    m = onnx.load(src)
    g = m.graph
    consumers = {}
    for n in g.node:
        for idx, x in enumerate(n.input):
            consumers.setdefault(x, []).append((n, idx))

    new_nodes, converted, saved = [], 0, 0
    for init in list(g.initializer):
        name = init.name
        uses = consumers.get(name, [])
        # Only weights used exclusively as the weight input (index 1) of Conv/ConvTranspose.
        if not uses or not all(n.op_type in ("Conv", "ConvTranspose") and idx == 1 for n, idx in uses):
            continue
        if any(n.name.startswith(p) for n, _ in uses for p in keep_fp32):
            continue
        w = numpy_helper.to_array(init).astype(np.float32)
        if w.size < min_elems or w.ndim < 2:
            continue
        scale = np.abs(w).max(axis=tuple(range(1, w.ndim))) / 127.0
        scale[scale == 0] = 1.0
        q = np.clip(np.round(w / scale.reshape([-1] + [1] * (w.ndim - 1))), -127, 127).astype(np.int8)
        g.initializer.remove(init)
        g.initializer.extend([
            numpy_helper.from_array(q, name + "_q"),
            numpy_helper.from_array(scale.astype(np.float32), name + "_s"),
            numpy_helper.from_array(np.zeros_like(scale, dtype=np.int8), name + "_z"),
        ])
        new_nodes.append(helper.make_node(
            "DequantizeLinear", [name + "_q", name + "_s", name + "_z"], [name], name=name + "_dq", axis=0))
        converted += 1
        saved += w.nbytes - q.nbytes

    # DequantizeLinear nodes have only initializer inputs, so putting them first keeps topological order.
    nodes = new_nodes + list(g.node)
    del g.node[:]
    g.node.extend(nodes)
    try:
        onnx.checker.check_model(m)
    except onnx.checker.ValidationError as e:
        if "metadata_props" not in str(e):  # Piper exports ship duplicate metadata keys; harmless
            raise
        print("checker: ignoring pre-existing duplicate metadata_props")
    out = os.path.join(dst_dir, "model.onnx")
    onnx.save(m, out)
    print(f"converted {converted} weights, saved {saved / 1e6:.1f} MB, file {os.path.getsize(out) / 1e6:.1f} MB")


if __name__ == "__main__":
    convert(sys.argv[1], sys.argv[2],
            int(sys.argv[3]) if len(sys.argv) > 3 else 4096,
            tuple(p for p in sys.argv[4].split(",") if p) if len(sys.argv) > 4 else ())
