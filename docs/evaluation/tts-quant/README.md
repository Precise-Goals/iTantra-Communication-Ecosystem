# TTS quantization — desktop evidence (T79, 2026-09-26)

Supports [`docs/TTS_QUANT_PLAN.md`](../../TTS_QUANT_PLAN.md). This is desktop only. Every ratio here
must be confirmed on CPH2467 (T81) before it appears on a slide.

## Environment

- Windows 11 dev machine, x86-64 CPU, Python 3 venv.
- `onnxruntime 1.19.2`, `onnx 1.19.1`, `sherpa-onnx 1.13.8` (the app bundles the
  `sherpa-onnx-static-link-onnxruntime-1.13.7` AAR), `pystoi`, `onnxconverter-common`.
- Voices:
  - `vits-mms-mar` FP32: the T17b export, `model-export/mms_work/pkg/vits-mms-mar/model.onnx`,
    114,043,828 bytes, opset 13.
  - Piper Hindi FP32 and `-int8`: `vits-piper-hi_IN-pratham-medium{,-int8}.tar.bz2` from the
    sherpa-onnx `tts-models` release. The `-int8` archive's sha256 matched the release digest
    `20f568c5…58b7`.
- Sentences: `reference` column of `docs/evaluation/sravaani/hypotheses/sravaani_{mr,hi}_in.tsv`,
  30–120 characters. Sentences containing digits are excluded from CER (§ "Coverage").
- ASR for the round trip: IndicConformer Marathi, `model.int8.onnx` + root `tokens.txt` from
  `huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx` (the app's STT source).
  Decoded with `sherpa_onnx.OfflineRecognizer.from_nemo_ctc`, greedy, 80-dim features.

## Commands

```bash
# variants (quantize_dynamic, same settings as T64: QUInt8 weights)
python model-export/tts_dynamic_int8_variants.py <fp32 model.onnx> <out>   # int8_all / keep_dp / keep_dp_decio / keep_dp_dec / fp16
python model-export/weight_only_int8.py <fp32 model.onnx> <out>/wo_int8
MSYS_NO_PATHCONV=1 python model-export/weight_only_int8.py <fp32 model.onnx> <out>/wo_int8_keep_dp 4096 /dp/
#   (Git Bash rewrites "/dp/" into a Windows path unless MSYS_NO_PATHCONV=1)

# size, RTF (1 thread), duration drift, ESTOI/STOI vs FP32, band spectrum diff; noise 0
python model-export/eval_tts_quant.py <out> <fp32 dir> docs/evaluation/sravaani/hypotheses/sravaani_mr_in.tsv

# ASR round-trip CER (app-default noise; NOISE=0 deterministic; REPEATS=k for spread)
python model-export/tts_roundtrip_cer.py <out> <fp32 dir> .../sravaani_mr_in.tsv <asr dir> 20
```

`tts_dynamic_int8_variants.py` builds only comparison variants. The plan rejects all of them, and
it is committed so the table below can be reproduced.

## Results — MMS Marathi

Graph weights by module: `/dec` 57.3 MB, `/flow` 28.4 MB, `/enc_p` 25.2 MB, `/dp` 2.2 MB.
There are 183 Conv plus ConvTranspose layers and zero MatMul.

| Variant | model.onnx MB | RTF (1 thr) | max duration drift | ESTOI vs FP32 (median) | band Δ dB (100 Hz → 8 kHz) |
| --- | --- | --- | --- | --- | --- |
| fp32 | 114.0 | 0.71 / 0.74 (two runs) | — | — | — |
| int8_all | 38.0 | 2.15 | 2.6 % | 0.125 | −0.1 +0.1 +0.2 +0.1 +0.1 +0.1 −0.1 +0.3 |
| int8_keep_dp | 39.6 | 2.16 | 4.4 % | 0.311 | −0.1 −0.0 +0.2 +0.0 +0.0 +0.1 −0.1 +0.3 |
| int8_keep_dp_decio | 41.6 | 2.13 | 4.3 % | 0.334 | −0.1 +0.0 +0.2 +0.0 +0.0 +0.1 −0.1 +0.1 |
| int8_keep_dp_dec | 74.4 | 0.68 | 7.0 % | 0.339 | +0.0 +0.0 +0.1 +0.1 −0.0 +0.0 −0.1 +0.1 |
| **wo_int8** | **29.7** (26.0 `.tar.bz2`) | **0.65** | 6.1 % | 0.358 | −0.1 +0.0 +0.1 +0.2 +0.1 −0.1 +0.0 +0.1 |
| **wo_int8_keep_dp** (recommended) | **31.3** | 0.657 (idle re-time) | — | — | — |
| fp16 | 57.8 | — | — | — | does not load: `Type (tensor(float16)) of output arg (/enc_p/Cast_1_output_0) … does not match expected type (tensor(float))` |

Determinism check: FP32 vs FP32 at noise 0 gives ESTOI = 1.0000. So the low ESTOI values above
come from the variants' duration drift (sample misalignment), not from randomness. That makes
aligned ESTOI unusable as a quantization gate.

The other four MMS voices converted to weight-only at the same size (29.7 MB each). Their
graphs are identical in shape.

### ASR round-trip CER (Marathi, 19 digit-free sentences)

ASR's own reference point: IndicConformer is being fed synthetic speech here, so these CERs mix
TTS error with ASR error. The comparison between variants is what counts.

| Variant | CER noise 0 | CER app noise, run 1 / 2 / 3 | mean |
| --- | --- | --- | --- |
| fp32 | 4.56 % | 5.43 / 4.77 / 6.53 (a separate earlier run: 4.72) | 5.58 % |
| int8_all | — | 6.09 (1 run) | — |
| int8_keep_dp | 4.39 % | 4.94 (1 run) | — |
| int8_keep_dp_decio | — | 5.60 (1 run) | — |
| int8_keep_dp_dec | 4.72 % | 5.49 / 5.49 / 6.53 (earlier run: 4.67) | 5.84 % |
| wo_int8 | 5.05 % | 5.82 / 5.27 / 5.87 (earlier run: 5.93) | 5.65 % |
| **wo_int8_keep_dp** | **4.39 %** | 4.77 / 5.60 / 5.16 | **5.18 %** |

Speed of the recommended variant, re-timed on an idle CPU (10 sentences): FP32 RTF 0.658 (1
thread) / 0.505 (2 threads), `wo_int8_keep_dp` 0.657 / 0.504. An earlier timing that ran
concurrently with a CER job read 1.288 / 0.834 vs 0.780 / 0.519. It was discarded for CPU
contention.

## Results — all ten voices, and the DSP chain

Run with `model-export/tts_quant_all_langs.py`, phases `convert` → `rtf` (sequential, idle CPU) →
`cer` + `dsp` (4 parallel jobs). Voices are the FP32 bundles the app downloads, plus the T17b MMS
exports. The ASR is IndicConformer per language from the same mirror; English uses `en/tokens.txt`,
and Odia has no model. Raw rows: [`results_all_langs.jsonl`](results_all_langs.jsonl). Summary tables
are in the plan, §2.3b (quantization) and §2.3c (DSP).

## Results — Piper Hindi (`hi_IN-pratham-medium`, 10 sentences)

| Variant | model.onnx MB | RTF 1 thread | RTF 2 threads |
| --- | --- | --- | --- |
| FP32 (shipped today) | 63.1 | 0.180 | 0.122 / 0.141 (two runs) |
| sherpa `-int8` (T17a) | 18.6 | 0.493 | 0.462 |
| weight-only INT8 | 16.7 | — | 0.127 |

## Coverage — characters the MMS voices cannot speak

From each `vits-mms-*/tokens.txt` (single-character tokens):

| Voice | tokens | digits in vocab | Latin letters | punctuation |
| --- | --- | --- | --- | --- |
| mar | 75 | ASCII 0 1 2 4 6 7 9 | 0 | `_ - '` |
| kan | 75 | ASCII 0–9 | 0 | `' - _` |
| tam | 59 | ASCII 0 1 2 3 4 5 6 7 9 | 2 | `_ '` |
| tel | 65 | ASCII 6 | 0 | `' _ -` (+1 non-printing) |
| ory | 76 | ASCII 0 1 2 4 5 6 7 9 3 | 0 | `' _ -` (+1 non-printing) |

No voice has native-script digits, `.` or `।`. sherpa-onnx logs `Skip unknown character` and drops
them. This was observed on real Marathi sentences: U+0967, U+096F, U+096D, U+096C and U+002F were
skipped.
