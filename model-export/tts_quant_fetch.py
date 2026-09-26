#!/usr/bin/env python3
"""
iTantra — T80 inputs for tts_quant_all_langs.py: download and extract the FP32 sherpa-onnx voice
bundles the app ships (hi/ml/en Piper, gu Mimic3, bn Coqui) into <work>/voices/, and one
IndicConformer ASR model per scored language into <work>/asr/<lang>/.
MMS voices (mr/kn/ta/te/or) are NOT downloaded: they are the T17b exports in
model-export/mms_work/pkg/ on the machine that made them.

Uses Python's tarfile (Git Bash's `tar` misreads "C:/..." paths as a remote host).

Usage:
    python tts_quant_fetch.py <work_dir> [--voices-only | --asr-only] [--langs hi,ml,...]
"""
import os
import sys
import tarfile
import urllib.request

REL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
HF = "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"
VOICES = {
    "hi": "vits-piper-hi_IN-pratham-medium",
    "ml": "vits-piper-ml_IN-arjun-medium",
    "en": "vits-piper-en_US-lessac-low",
    "gu": "vits-mimic3-gu_IN-cmu-indic_low",
    "bn": "vits-coqui-bn-custom_female",
}
ASR_LANGS = ["hi", "ml", "en", "gu", "bn", "mr", "kn", "ta", "te"]  # no Odia model in this mirror


def get(url, dst):
    if os.path.exists(dst) and os.path.getsize(dst) > 0:
        print("have", dst)
        return
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    print("get ", url, flush=True)
    urllib.request.urlretrieve(url, dst + ".part")
    os.replace(dst + ".part", dst)


def main():
    work = sys.argv[1]
    args = sys.argv[2:]
    langs = None
    if "--langs" in args:
        langs = set(args[args.index("--langs") + 1].split(","))
    if "--asr-only" not in args:
        for lang, bundle in VOICES.items():
            if langs and lang not in langs:
                continue
            out = os.path.join(work, "voices", bundle)
            if os.path.isdir(out):
                print("have", out)
                continue
            arc = os.path.join(work, "voices", bundle + ".tar.bz2")
            get(f"{REL}/{bundle}.tar.bz2", arc)
            with tarfile.open(arc) as t:
                t.extractall(os.path.join(work, "voices"))
            os.remove(arc)
            print("extracted", out)
    if "--voices-only" not in args:
        for lang in ASR_LANGS:
            if langs and lang not in langs:
                continue
            d = os.path.join(work, "asr", lang)
            get(f"{HF}/{lang}/model.int8.onnx", os.path.join(d, "model.int8.onnx"))
            get(f"{HF}/en/tokens.txt" if lang == "en" else f"{HF}/tokens.txt", os.path.join(d, "tokens.txt"))
    print("FETCH_DONE")


if __name__ == "__main__":
    main()
