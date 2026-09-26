#!/usr/bin/env python3
"""
iTantra — the rejected comparison variants for T79: onnxruntime `quantize_dynamic` (QUInt8 weights,
as T64 does) with different VITS modules kept FP32, plus a naive FP16 conversion. Kept so the
numbers in docs/evaluation/tts-quant/README.md can be reproduced; none of these ship.

Usage:
    python tts_dynamic_int8_variants.py <fp32 voice dir>/model.onnx <out_root>
"""
import onnx, os, sys, shutil
from onnxruntime.quantization import quantize_dynamic, QuantType

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
names = [n.name for n in onnx.load(src).graph.node]


def ex(*pre):
    return [n for n in names if any(n.startswith(p) for p in pre)]


variants = {
    "int8_all": [],
    "int8_keep_dp": ex("/dp/"),
    "int8_keep_dp_decio": ex("/dp/", "/dec/conv_pre", "/dec/conv_post"),
    "int8_keep_dp_dec": ex("/dp/", "/dec/"),
}
tok = os.path.join(os.path.dirname(src), "tokens.txt")
for v, excl in variants.items():
    d = f"{out}/{v}"
    os.makedirs(d, exist_ok=True)
    shutil.copy(tok, d)
    quantize_dynamic(src, f"{d}/model.onnx", weight_type=QuantType.QUInt8, nodes_to_exclude=excl)
    print(v, len(excl), "excluded", round(os.path.getsize(f"{d}/model.onnx") / 1e6, 1), "MB", flush=True)

try:
    from onnxconverter_common import float16
    d = f"{out}/fp16"
    os.makedirs(d, exist_ok=True)
    shutil.copy(tok, d)
    m16 = float16.convert_float_to_float16(onnx.load(src), keep_io_types=True)
    onnx.save(m16, f"{d}/model.onnx")
    print("fp16", round(os.path.getsize(f"{d}/model.onnx") / 1e6, 1), "MB")
except Exception as e:
    print("fp16 skipped", e)
