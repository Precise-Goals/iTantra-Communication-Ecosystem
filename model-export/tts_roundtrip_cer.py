#!/usr/bin/env python3
"""
iTantra — TTS intelligibility by ASR round trip (T79):
text -> TTS variant -> IndicConformer (sherpa-onnx NeMo CTC, greedy) -> CER against the text.

Runs the TTS at sherpa-onnx's default noise (0.667 / 0.8), i.e. what the app ships. Sentences
with digits are skipped: MMS voices have no digit tokens and silently drop them (a T35 issue,
not a quantization one). Normalisation matches T76: NFC, lowercase, strip P* and danda.

Usage:
    python tts_roundtrip_cer.py <variants_root> <fp32_voice_dir> <hypotheses.tsv> <asr_dir> [N=20]
<asr_dir> holds model.int8.onnx + tokens.txt from
huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx (<lang>/model.int8.onnx and
the root tokens.txt, as in IMPLEMENTATION_SPEC_2.md T76).
"""
import csv, os, re, sys, unicodedata
import numpy as np
import sherpa_onnx

root, fp32_dir, tsv, asr_dir = sys.argv[1:5]
N = int(sys.argv[5]) if len(sys.argv) > 5 else 20
# Env overrides: NOISE=0 for deterministic output, REPEATS=k to measure run-to-run spread,
# VARIANTS=a,b to run a subset (fp32 is always included).
noise = (0.0, 0.0) if os.environ.get("NOISE") == "0" else (0.667, 0.8)  # defaults = what the app runs
repeats = int(os.environ.get("REPEATS", "1"))
variants = ["fp32"] + sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
if os.environ.get("VARIANTS"):
    keep = set(os.environ["VARIANTS"].split(","))
    variants = [v for v in variants if v == "fp32" or v in keep]

rows = list(csv.DictReader(open(tsv, encoding="utf-8"), delimiter="\t"))
digits = re.compile(r"[0-9०-९]")
sents = [r["reference"] for r in rows if 30 <= len(r["reference"]) <= 120 and not digits.search(r["reference"])][:N]


def norm(s):
    s = unicodedata.normalize("NFC", s).lower()
    s = "".join(c for c in s if not unicodedata.category(c).startswith("P") and c not in "।॥")
    return " ".join(s.split())


def lev(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


asr = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
    model=f"{asr_dir}/model.int8.onnx", tokens=f"{asr_dir}/tokens.txt",
    num_threads=4, sample_rate=16000, feature_dim=80, decoding_method="greedy_search")


def transcribe(y, sr):
    st = asr.create_stream()
    st.accept_waveform(sr, y)
    asr.decode_stream(st)
    return st.result.text


for v in variants:
    d = fp32_dir if v == "fp32" else os.path.join(root, v)
    vits = sherpa_onnx.OfflineTtsVitsModelConfig(model=f"{d}/model.onnx", tokens=f"{d}/tokens.txt", data_dir="",
                                                 lexicon="", dict_dir="", noise_scale=noise[0], noise_scale_w=noise[1])
    tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(vits=vits, num_threads=4, provider="cpu")))
    runs = []
    for _ in range(repeats):
        errs = chars = 0
        for s in sents:
            a = tts.generate(s, sid=0, speed=1.0)
            hyp = norm(transcribe(np.asarray(a.samples, dtype=np.float32), a.sample_rate))
            ref = norm(s)
            errs += lev(ref, hyp)
            chars += len(ref)
        runs.append(round(100 * errs / chars, 2))
    print({"variant": v, "n": len(sents), "noise": noise, "corpus_cer_pct_per_run": runs,
           "mean": round(float(np.mean(runs)), 2)}, flush=True)
