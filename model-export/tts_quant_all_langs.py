#!/usr/bin/env python3
"""
iTantra — T79 across all ten TTS voices: FP32 vs weight-only INT8 (duration predictor FP32).

Phases (run `rtf` for every language on an otherwise idle machine, before any `cer` jobs):
    convert  build <work>/wo/<lang>/ with weight_only_int8.convert(..., keep_fp32=("/dp/",)),
             copy the voice's other non-espeak files, and pack a .tar.bz2 to measure download size
    rtf      synthesis RTF, 10 sentences, 2 threads (what the app uses), FP32 vs weight-only
    cer      ASR round trip (IndicConformer, sherpa-onnx NeMo CTC): 20 digit-free sentences,
             1 run at noise 0 (deterministic) + 2 runs at the app's default noise, FP32 vs weight-only
    dsp      the proposed playback fix (dsp_chain: DC block, pre-emphasis, DRC, limiter) vs raw,
             each clean and through a SIMULATED cheap loudspeaker (cheap_speaker: +14 dB volume,
             hard clip, 400 Hz high-pass); CER per condition. A simulation, not a real speaker.

Every result is appended as one JSON line to <work>/results.jsonl.

Usage:
    python tts_quant_all_langs.py <phase> <lang> <work_dir> <repo_root> [mms_pkg_dir]
    python tts_quant_all_langs.py manifest <work_dir>      # sizes + sha256 of the built archives
Inputs: model-export/tts_quant_fetch.py <work_dir> downloads the non-MMS voices and the ASR models.
<work_dir> must hold voices/<bundle>/ (extracted FP32 sherpa-onnx release bundles) and
asr/<lang>/{model.int8.onnx,tokens.txt}. MMS voices come from [mms_pkg_dir]/vits-mms-<code>/.
"""

import csv
import glob
import json
import os
import re
import shutil
import sys
import tarfile
import time
import unicodedata

import numpy as np
import sherpa_onnx

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from weight_only_int8 import convert  # noqa: E402

# lang -> (bundle dir name, source: "release" | "mms", hypotheses tsv, espeak data dir inside bundle?)
VOICES = {
    "hi": ("vits-piper-hi_IN-pratham-medium", "release", "sravaani_hi_in.tsv"),
    "ml": ("vits-piper-ml_IN-arjun-medium", "release", "sravaani_ml_in.tsv"),
    "en": ("vits-piper-en_US-lessac-low", "release", "sravaani_en_us.tsv"),
    "gu": ("vits-mimic3-gu_IN-cmu-indic_low", "release", "sravaani_gu_in.tsv"),
    "bn": ("vits-coqui-bn-custom_female", "release", "sravaani_bn_in.tsv"),
    "mr": ("vits-mms-mar", "mms", "sravaani_mr_in.tsv"),
    "kn": ("vits-mms-kan", "mms", "sravaani_kn_in.tsv"),
    "ta": ("vits-mms-tam", "mms", "sravaani_ta_in.tsv"),
    "te": ("vits-mms-tel", "mms", "sravaani_te_in.tsv"),
    "or": ("vits-mms-ory", "mms", "sravaani_or_in.tsv"),
}
DIGITS = re.compile(r"[0-9०-९০-৯૦-૯୦-୯௦-௯౦-౯೦-೯൦-൯]")


def fp32_dir(lang, work, mms_pkg):
    bundle, src, _ = VOICES[lang]
    return os.path.join(mms_pkg, bundle) if src == "mms" else os.path.join(work, "voices", bundle)


def onnx_in(d):
    return [f for f in glob.glob(os.path.join(d, "*.onnx"))][0]


def tts_for(d, espeak, threads, noise):
    kw = dict(model=onnx_in(d), tokens=os.path.join(d, "tokens.txt"), data_dir=espeak or "",
              lexicon=os.path.join(d, "lexicon.txt") if os.path.exists(os.path.join(d, "lexicon.txt")) else "",
              dict_dir="")
    if noise is not None:
        kw.update(noise_scale=noise[0], noise_scale_w=noise[1])
    vits = sherpa_onnx.OfflineTtsVitsModelConfig(**kw)
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(vits=vits, num_threads=threads, provider="cpu")))


def sentences(repo, lang, n, lo=30, hi=120):
    tsv = os.path.join(repo, "docs/evaluation/sravaani/hypotheses", VOICES[lang][2])
    rows = csv.DictReader(open(tsv, encoding="utf-8"), delimiter="\t")
    return [r["reference"] for r in rows if lo <= len(r["reference"]) <= hi and not DIGITS.search(r["reference"])][:n]


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


def emit(work, rec):
    print(json.dumps(rec, ensure_ascii=False), flush=True)
    with open(os.path.join(work, "results.jsonl"), "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")


def manifest(work):
    """<work>/wo/*-wo-int8.tar.bz2 -> <work>/wo/manifest.csv: lang, file, bytes, sha256 (for ModelRegistry)."""
    import hashlib
    by_bundle = {b: l for l, (b, _, _) in VOICES.items()}
    rows = []
    for arc in sorted(glob.glob(os.path.join(work, "wo", "*-wo-int8.tar.bz2"))):
        name = os.path.basename(arc)
        h = hashlib.sha256()
        with open(arc, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        rows.append((by_bundle[name[: -len("-wo-int8.tar.bz2")]], name, os.path.getsize(arc), h.hexdigest()))
    out = os.path.join(work, "wo", "manifest.csv")
    with open(out, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["lang", "file", "bytes", "sha256"])
        w.writerows(rows)
    for r in rows:
        print(",".join(map(str, r)))
    print("wrote", out)


def main():
    if sys.argv[1] == "manifest":
        manifest(sys.argv[2])
        return
    phase, lang, work, repo = sys.argv[1:5]
    mms_pkg = sys.argv[5] if len(sys.argv) > 5 else ""
    src = fp32_dir(lang, work, mms_pkg)
    wo = os.path.join(work, "wo", lang)
    espeak = os.path.join(src, "espeak-ng-data") if os.path.isdir(os.path.join(src, "espeak-ng-data")) else None

    if phase == "convert":
        model = onnx_in(src)
        # Duration predictor stays FP32. Piper/MMS name it /dp/, Coqui /duration_predictor/. The
        # Mimic3 and older Piper exports (gu, ml) have anonymous node names (Conv_32 ...) so no
        # prefix matches and their duration predictor is quantized too (has_dp=false below).
        convert(model, wo, 4096, ("/dp/", "/duration_predictor/"))
        for f in os.listdir(src):  # the rest of the bundle, minus espeak (shipped once as its own pack)
            p = os.path.join(src, f)
            if f != "espeak-ng-data" and not f.endswith(".onnx") and f != "tokens.txt":
                (shutil.copytree if os.path.isdir(p) else shutil.copy)(p, os.path.join(wo, f))
        arc = os.path.join(work, "wo", f"{VOICES[lang][0]}-wo-int8.tar.bz2")
        with tarfile.open(arc, "w:bz2") as t:
            t.add(wo, arcname=f"{VOICES[lang][0]}-wo-int8")
        emit(work, dict(phase="convert", lang=lang, fp32_onnx_mb=round(os.path.getsize(model) / 1e6, 1),
                        wo_onnx_mb=round(os.path.getsize(os.path.join(wo, "model.onnx")) / 1e6, 1),
                        wo_archive_mb=round(os.path.getsize(arc) / 1e6, 1),
                        has_dp=any(n.startswith(("/dp/", "/duration_predictor/")) for n in _node_names(model))))

    elif phase == "rtf":
        sents = sentences(repo, lang, 10, 40, 120)
        out = dict(phase="rtf", lang=lang, n=len(sents))
        for label, d in (("fp32", src), ("wo", wo)):
            tts = tts_for(d, espeak, 2, None)
            tts.generate(sents[0][:20], sid=0, speed=1.0)
            synth = audio = 0.0
            for s in sents:
                t = time.perf_counter()
                a = tts.generate(s, sid=0, speed=1.0)
                synth += time.perf_counter() - t
                audio += len(a.samples) / a.sample_rate
            out[f"{label}_rtf_2thr"] = round(synth / audio, 3)
            out["sample_rate"] = a.sample_rate
        emit(work, out)

    elif phase == "cer":
        asr_dir = os.path.join(work, "asr", lang)
        if not os.path.exists(os.path.join(asr_dir, "model.int8.onnx")):
            emit(work, dict(phase="cer", lang=lang, skipped="no IndicConformer model for this language"))
            return
        asr = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
            model=os.path.join(asr_dir, "model.int8.onnx"), tokens=os.path.join(asr_dir, "tokens.txt"),
            num_threads=2, sample_rate=16000, feature_dim=80, decoding_method="greedy_search")
        sents = sentences(repo, lang, 20)
        out = dict(phase="cer", lang=lang, n=len(sents))
        for label, d in (("fp32", src), ("wo", wo)):
            for tag, noise, runs in (("noise0", (0.0, 0.0), 1), ("appnoise", (0.667, 0.8), 2)):
                tts = tts_for(d, espeak, 2, noise)
                vals = []
                for _ in range(runs):
                    errs = chars = 0
                    for s in sents:
                        a = tts.generate(s, sid=0, speed=1.0)
                        st = asr.create_stream()
                        st.accept_waveform(a.sample_rate, np.asarray(a.samples, dtype=np.float32))
                        asr.decode_stream(st)
                        ref, hyp = norm(s), norm(st.result.text)
                        errs += lev(ref, hyp)
                        chars += len(ref)
                    vals.append(round(100 * errs / chars, 2))
                out[f"{label}_{tag}"] = vals
        emit(work, out)

    elif phase == "dsp":
        run_dsp(work, repo, lang, src, espeak)


def dsp_chain(x, sr, alpha=0.7, thr_db=-24.0, ratio=3.0, att_ms=5.0, rel_ms=60.0, ceil_db=-1.0):
    """The proposed playback fix, as it would run in AudioPlaybackManager (T83):
    DC blocker -> pre-emphasis (y = x - a*x[n-1], blended to keep the low end) -> feed-forward
    compressor (DRC) -> peak limiter -> 5 ms fades. Returns float32 in [-1, 1]."""
    from scipy.signal import lfilter
    y = lfilter([1, -1], [1, -0.995], x)                     # DC blocker
    y = 0.5 * y + 0.5 * lfilter([1, -alpha], [1], y)          # half pre-emphasised: consonant lift
    # compressor on a smoothed level (one-pole attack/release on |y|)
    a_att, a_rel = np.exp(-1 / (sr * att_ms / 1000)), np.exp(-1 / (sr * rel_ms / 1000))
    env = np.empty_like(y)
    e = 0.0
    for i, v in enumerate(np.abs(y)):
        e = a_att * e + (1 - a_att) * v if v > e else a_rel * e + (1 - a_rel) * v
        env[i] = e
    lvl = 20 * np.log10(env + 1e-9)
    gain_db = np.where(lvl > thr_db, (thr_db - lvl) * (1 - 1 / ratio), 0.0)
    y = y * 10 ** (gain_db / 20)
    y = y / (np.max(np.abs(y)) + 1e-9) * 10 ** (ceil_db / 20)  # make-up gain to the ceiling
    y = np.clip(y, -1, 1)                                     # limiter (ceiling already applied)
    f = int(0.005 * sr)
    y[:f] *= np.linspace(0, 1, f)
    y[-f:] *= np.linspace(1, 0, f)
    return y.astype(np.float32)


def cheap_speaker(x, sr, rms_db=-20.0, boost_db=14.0, hp_hz=400.0):
    """Simulated small phone loudspeaker at high volume (NOT a measurement of a real speaker):
    level-match to rms_db, turn the volume up by boost_db, hard-clip at full scale (DAC/amp
    overload), then a 2nd-order high-pass at hp_hz (a 1-2 cm driver has no bass)."""
    from scipy.signal import butter, lfilter
    rms = np.sqrt(np.mean(x ** 2)) + 1e-9
    y = x * (10 ** (rms_db / 20) / rms) * 10 ** (boost_db / 20)
    y = np.clip(y, -1, 1)
    b, a = butter(2, hp_hz / (sr / 2), btype="high")
    return lfilter(b, a, y).astype(np.float32)


def run_dsp(work, repo, lang, src, espeak):
    asr_dir = os.path.join(work, "asr", lang)
    if not os.path.exists(os.path.join(asr_dir, "model.int8.onnx")):
        emit(work, dict(phase="dsp", lang=lang, skipped="no IndicConformer model for this language"))
        return
    asr = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=os.path.join(asr_dir, "model.int8.onnx"), tokens=os.path.join(asr_dir, "tokens.txt"),
        num_threads=2, sample_rate=16000, feature_dim=80, decoding_method="greedy_search")
    sents = sentences(repo, lang, 20)
    tts = tts_for(src, espeak, 2, (0.0, 0.0))  # FP32, deterministic: DSP is independent of quantization
    conds = {
        "clean_raw": lambda y, sr: y,
        "clean_dsp": lambda y, sr: dsp_chain(y, sr),
        "speaker_raw": lambda y, sr: cheap_speaker(y, sr),
        "speaker_dsp": lambda y, sr: cheap_speaker(dsp_chain(y, sr), sr),
    }
    errs = {k: 0 for k in conds}
    clip = {"speaker_raw": 0.0, "speaker_dsp": 0.0}
    chars = 0
    for s in sents:
        a = tts.generate(s, sid=0, speed=1.0)
        y0, sr = np.asarray(a.samples, dtype=np.float64), a.sample_rate
        ref = norm(s)
        chars += len(ref)
        for k, fn in conds.items():
            y = fn(y0, sr)
            st = asr.create_stream()
            st.accept_waveform(sr, np.asarray(y, dtype=np.float32))
            asr.decode_stream(st)
            errs[k] += lev(ref, norm(st.result.text))
        for k, pre in (("speaker_raw", y0), ("speaker_dsp", dsp_chain(y0, sr))):
            g = (10 ** (-20 / 20) / (np.sqrt(np.mean(pre ** 2)) + 1e-9)) * 10 ** (14 / 20)
            clip[k] += float(np.mean(np.abs(pre * g) >= 1.0)) / len(sents)
    emit(work, dict(phase="dsp", lang=lang, n=len(sents),
                    **{f"cer_{k}": round(100 * v / chars, 2) for k, v in errs.items()},
                    **{f"clipped_pct_{k}": round(100 * v, 2) for k, v in clip.items()}))


def _node_names(model_path):
    import onnx
    return [n.name for n in onnx.load(model_path).graph.node]


if __name__ == "__main__":
    main()
