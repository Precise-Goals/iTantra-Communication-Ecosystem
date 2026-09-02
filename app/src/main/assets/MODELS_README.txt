This directory should contain the following ONNX model files before building.
Download instructions are in the README.md and model-export/ scripts.

Required files:
  models/
    silero_vad.onnx          (~2 MB)  — Silero VAD v4
    stt/
      hi_model.int8.onnx + hi_tokens.txt   (~188 MB) — Hindi STT (IndicConformer, sherpa-onnx)
      gu_model.int8.onnx + gu_tokens.txt   — Gujarati STT
      mr_model.int8.onnx + mr_tokens.txt   — Marathi STT
      kn_model.int8.onnx + kn_tokens.txt   — Kannada STT
      ml_model.int8.onnx + ml_tokens.txt   — Malayalam STT
      ta_model.int8.onnx + ta_tokens.txt   — Tamil STT
      te_model.int8.onnx + te_tokens.txt   — Telugu STT
      bn_model.int8.onnx + bn_tokens.txt   — Bengali STT
      en_model.int8.onnx + en_tokens.txt   — English STT
      NOTE: there is no per-language "multilingual" STT file — each language is a
      separate ONNX graph with its own tokens.txt vocabulary alongside it (source:
      huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx).
      NOTE: Odia (or) has NO model in this source repo — it only has "as" (Assamese).
      Do not substitute the Assamese model for Odia; leave Odia STT unsupported until
      a real Odia-trained model is found.
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
