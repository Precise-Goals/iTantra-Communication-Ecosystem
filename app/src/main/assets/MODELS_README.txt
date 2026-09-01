This directory should contain the following ONNX model files before building.
Download instructions are in the README.md and model-export/ scripts.

Required files:
  models/
    silero_vad.onnx          (~2 MB)  — Silero VAD v4
    indicconformer_int8.onnx (~150 MB) — AI4Bharat IndicConformer Multilingual INT8
    tts/
      hi_vits_int8.onnx      (~15 MB) — Hindi TTS
      gu_vits_int8.onnx      (~15 MB) — Gujarati TTS
      mr_vits_int8.onnx      (~15 MB) — Marathi TTS
      kn_vits_int8.onnx      (~15 MB) — Kannada TTS
      ml_vits_int8.onnx      (~15 MB) — Malayalam TTS
      ta_vits_int8.onnx      (~15 MB) — Tamil TTS
      te_vits_int8.onnx      (~15 MB) — Telugu TTS
      or_vits_int8.onnx      (~15 MB) — Odia TTS
      bn_vits_int8.onnx      (~15 MB) — Bengali TTS
      en_vits_int8.onnx      (~15 MB) — English TTS (Piper)

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
