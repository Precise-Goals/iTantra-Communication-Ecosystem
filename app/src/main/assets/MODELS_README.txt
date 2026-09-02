This directory should contain the following ONNX model files before building.
Download instructions are in the README.md and model-export/ scripts.

Models are NOT bundled in the APK — ModelDownloadManager fetches them at runtime into
context.filesDir/models/. This file just documents what ends up there.

Required files (context.filesDir/models/):
    silero_vad_v4.onnx           (~2.2 MB)  — Silero VAD v4
    indicconformer_multilingual_int8.onnx (~188 MB) — AI4Bharat IndicConformer (Hindi model)
    espeak-ng-data/               (~7 MB)   — shared real espeak-ng phoneme data (sherpa-onnx),
                                                required by every TTS voice below
    tts/
      hi/  model + tokens.txt    (~64 MB)  — Hindi TTS (sherpa-onnx/Piper, real phonemization)
      gu/  model + tokens.txt    (~76 MB)  — Gujarati TTS (sherpa-onnx/Mimic3, low quality tier)
      ml/  model + tokens.txt    (~64 MB)  — Malayalam TTS (sherpa-onnx/Piper)
      bn/  model + tokens.txt   (~103 MB)  — Bengali TTS (sherpa-onnx/Coqui)
      en/  model + tokens.txt    (~64 MB)  — English TTS (sherpa-onnx/Piper)

  UNSUPPORTED — no free offline TTS source found (checked Piper/Coqui/Mimic3/MMS, every
  vits-* family in sherpa-onnx's tts-models release): Kannada, Tamil, Telugu, Marathi, Odia.
  Do not substitute a wrong-language model for these (e.g. Assamese for Odia) — that was a
  real bug in an earlier version of this registry.

  fonts/
    inter_regular.ttf        — Download from fonts.google.com/specimen/Inter
    inter_medium.ttf
    inter_semibold.ttf
    inter_bold.ttf

To download models, run:
  cd model-export/
  pip install -r requirements.txt
  python export_indicconformer.py --vad-only  # Export VAD first (fast)
  python export_indicconformer.py --checkpoint /path/to/checkpoint.nemo
  python quantize_models.py
