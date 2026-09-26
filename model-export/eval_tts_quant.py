#!/usr/bin/env python3
"""
iTantra — FP32 vs quantized MMS voice (T79): size, desktop RTF (1 thread), length drift,
ESTOI/STOI vs the FP32 output, and long-term spectrum difference per band.

noise_scale = noise_scale_w = 0 so VITS is deterministic (the script checks FP32 vs FP32 == 1.0).
Caveat, measured: every INT8 variant shifts durations by a few percent, so sample-aligned ESTOI
mostly measures misalignment, not quality. Use tts_roundtrip_cer.py for quality.

Usage:
    python eval_tts_quant.py <variants_root> <fp32_voice_dir> <hypotheses.tsv>
<variants_root> holds one sub-directory per variant, each with model.onnx + tokens.txt.
<hypotheses.tsv> is any docs/evaluation/sravaani/hypotheses/sravaani_<lang>.tsv (its `reference`
column is used as the sentence set).
"""
import csv, os, sys, time
import numpy as np
import sherpa_onnx
from pystoi import stoi

root, fp32_dir, tsv = sys.argv[1], sys.argv[2], sys.argv[3]
variants = ["fp32"] + sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))

rows = list(csv.DictReader(open(tsv, encoding="utf-8"), delimiter="\t"))
sents = [r["reference"] for r in rows if 40 <= len(r["reference"]) <= 120][:10]


def load(d, noise):
    vits = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=f"{d}/model.onnx", tokens=f"{d}/tokens.txt", data_dir="", lexicon="", dict_dir="",
        noise_scale=noise[0], noise_scale_w=noise[1], length_scale=1.0)
    cfg = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=vits, num_threads=1, provider="cpu"))
    return sherpa_onnx.OfflineTts(cfg)


EDGES = [100, 250, 500, 1000, 2000, 3000, 4000, 5500, 8000]


def bands(x, sr):
    spec = np.abs(np.fft.rfft(x * np.hanning(len(x)))) ** 2
    f = np.fft.rfftfreq(len(x), 1 / sr)
    return np.array([10 * np.log10(spec[(f >= a) & (f < b)].mean() + 1e-12) for a, b in zip(EDGES[:-1], EDGES[1:])])


ref = {}
results = []
for v in variants:
    d = fp32_dir if v == "fp32" else os.path.join(root, v)
    tts = load(d, (0.0, 0.0))
    tts.generate("नमस्कार", sid=0, speed=1.0)  # warm-up
    synth_s = audio_s = 0.0
    est, st, drift, bd = [], [], [], []
    for i, s in enumerate(sents):
        t = time.perf_counter()
        a = tts.generate(s, sid=0, speed=1.0)
        synth_s += time.perf_counter() - t
        y = np.asarray(a.samples, dtype=np.float64)
        sr = a.sample_rate
        audio_s += len(y) / sr
        if v == "fp32":
            ref[i] = y
            continue
        r = ref[i]
        drift.append(abs(len(y) - len(r)) / len(r))
        n = min(len(y), len(r))
        est.append(stoi(r[:n], y[:n], sr, extended=True))
        st.append(stoi(r[:n], y[:n], sr, extended=False))
        bd.append(bands(y[:n], sr) - bands(r[:n], sr))
    size = os.path.getsize(f"{d}/model.onnx") / 1e6
    res = dict(variant=v, size_mb=round(size, 1), rtf_1thread=round(synth_s / audio_s, 3), n=len(sents))
    if v != "fp32":
        res.update(estoi_med=round(float(np.median(est)), 3), estoi_min=round(min(est), 3),
                   stoi_med=round(float(np.median(st)), 3), len_drift_max_pct=round(100 * max(drift), 2),
                   band_db=" ".join(f"{x:+.1f}" for x in np.mean(bd, axis=0)))
    print(res, flush=True)
    results.append(res)

# sanity: FP32 vs itself must be ~1.0 if noise=0 is deterministic
tts = load(fp32_dir, (0.0, 0.0))
y = np.asarray(tts.generate(sents[0], sid=0, speed=1.0).samples, dtype=np.float64)
print("determinism check ESTOI fp32 vs fp32:", round(stoi(ref[0][:len(y)], y[:len(ref[0])], 16000, extended=True), 4))
print("bands (Hz):", list(zip(EDGES[:-1], EDGES[1:])))
