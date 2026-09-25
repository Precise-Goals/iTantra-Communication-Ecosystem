# T78 — SraVaani TDT engine

> Spec: `docs/IMPLEMENTATION_SPEC_2.md`, Group I → "T78 🔬 · SraVaani TDT engine: one on-device model
> for the nine Indic languages". Design: `docs/evaluation/sravaani/tdt-engine-design.md`.
> Depends on: T77 (PR #29), T23/T29 (PR #26).

## Step 1 — the INT8 TDT pair, measured (Colab)

**Source:** the ONNX bundle T77 found, linked from the SraVaani model card's own README
(`https://drive.google.com/file/d/1ap6qSg-DG5va-noeNY8diMVLqygutqBa/view`) — a 1.7 GB zip
containing `encoder-sravaani.onnx` (1.77 GB FP32), `decoder_joint-sravaani.onnx` (43 MB FP32),
`ctc-sravaani.onnx` (unused here), `sravaani_onnx_infer.py` (the model card's own reference
inference script, printed and read in full — its `decode_rnnt` is the source for every ONNX I/O
name below), and `tokenizer.model`.

### 1. Quantization

`quantize_dynamic(..., weight_type=QuantType.QUInt8)`, exactly as T77 Step 3, applied separately
to the encoder and decoder_joint (not merged — unlike T77's CTC route, these stay two graphs and
two ONNX sessions).

| File | Size | sha256 |
| --- | --- | --- |
| `encoder-sravaani.int8.onnx` | 454.4 MB | `3bf1c5d2cb91640135d32b87d9b1764335f356a981fa2cfeea4e5fcdfdeb19fd` |
| `decoder_joint-sravaani.int8.onnx` | 10.3 MB | `ee795c233163b21a4a051f9deeb36111f9c3872d6947382ef166aa4cddeabc4a` |
| **Pair total** | **464.7 MB** | — |

Quantization warnings (`Slice`/`Tile`/depthwise-conv ops left FP32) match exactly what T77 saw on
the CTC merge — expected for `quantize_dynamic` on a Conformer-style architecture, not a problem.

### 2. I/O verification (printed before use, never assumed)

FP32 and INT8 graphs have identical I/O names/shapes/dtypes (quantization did not rename anything):

- **Encoder:** `audio_signal[*, 128, *]` float32 + `length[*]` int64 → `outputs[*, 1024, *]`
  float32 + `encoded_lengths[*]` int64.
- **Decoder_joint:** `encoder_outputs[*,1024,*]` float32, `targets[*,*]` int32, `target_length[*]`
  int32, `input_states_1/2[1,*,640]` float32 → `outputs[*,*,*,5006]` float32 (5001 token logits +
  5 duration logits), `prednet_lengths` int32, `output_states_1/2[1,*,640]` float32.

Matches the design doc §1c and `sravaani_onnx_infer.py`'s `decode_rnnt` exactly: `BLANK_ID=5000`,
`VOCAB_SIZE=5000`, `DURATIONS=[0,1,2,3,4]`, `max_symbols=10` (hardcoded via `range(10)` in the
script, not read from a config file).

**Note for Step 3/4 (Kotlin):** `length` is **int64** on the encoder but `targets`/`target_length`
are **int32** on decoder_joint — a real dtype split to carry through exactly.

### 3. Desktop accuracy and RTF (INT8, 1 CPU thread)

Same 100 FLEURS clips per language as T76/T77 (first 100 rows of `data/<lang>/test.tsv` sorted by
filename; column 1 = filename, column 3 = normalized transcription — verified directly against
raw TSV rows). Preprocessing via `sravaani_onnx_infer.py`'s own `preprocess()` (librosa-based —
note this omits the 0.97 preemphasis step that the real `preproc.pt` applies; used here only
because it's the model card's own documented reference inference path for an end-to-end WER
number, not for the Step 2 golden test, which uses the real `preproc.pt` instead). Same
NFC → lowercase → strip Unicode `P*` punctuation → collapse-whitespace scoring as T76/T77,
`jiwer` for corpus WER/CER. Full numbers in `docs/evaluation/sravaani/results_int8_tdt.csv`.

| Lang | IndicConformer (T76) | SraVaani TDT FP32 (T76) | **SraVaani INT8 TDT** | Median RTF |
| --- | --- | --- | --- | --- |
| hi | 11.46% | 9.38% | **10.00%** | 0.167 |
| gu | 19.57% | 19.52% | **19.85%** | 0.173 |
| mr | 19.80% | 19.16% | **20.14%** | 0.168 |
| kn | 17.40% | 17.96% | **19.19%** | 0.168 |
| ml | 23.36% | 19.00% | **19.94%** | 0.169 |
| ta | 31.94% | 33.03% | **32.91%** | 0.170 |
| te | 21.81% | 21.10% | **22.22%** | 0.169 |
| bn | 14.57% | 15.32% | **16.45%** | 0.165 |
| or | — (no model) | 21.69% | **22.31%** | 0.167 |

**Average, 8 non-English shared languages:**

| | IndicConformer | SraVaani TDT FP32 (T76) | **SraVaani INT8 TDT** |
| --- | --- | --- | --- |
| Avg WER | 19.99% | 19.31% | **20.09%** |

### Checkpoint 1 — MISSED, overridden by Gaurav

Checkpoint 1 requires **both** avg INT8 TDT WER ≤ 19.99% **and** pair size ≤ ~550 MB.

- Size: **464.7 MB — passes** (better than the design doc's ~490–500 MB estimate).
- Accuracy: **20.09% vs 19.99% — misses by 0.10 points.** INT8 quantization erased essentially
  all of TDT's FP32 accuracy advantage over IndicConformer (19.31% → 20.09%, a 0.78-point
  quantization loss) and pushed the average just past IndicConformer's number.

Per spec: *"Missed: stop and go to the fallback. The accuracy advantage is the reason for the
week."* **Gaurav explicitly overrode this stop rule** and directed the work to continue to Step 2,
judging the 0.10-point margin (driven mostly by Hindi's quantization loss, 9.38%→10.00%) close
enough to proceed given RTF and size are both strong. This is a deliberate, recorded deviation
from the spec's literal checkpoint rule, not a passing result — if T78 is ultimately not adopted,
this is the number that says why.

**Known gaps in this record:** the Colab runtime reset between Steps 1 and 2, which lost the
per-clip hypothesis TSVs (only the aggregate WER/CER/RTF numbers above survived, from console
output) and the model cold-load time (`load_ms` in `results_int8_tdt.csv` is blank, not
measured — not fabricated). The Step 1.4 parity fixture (one clip's encoder output + reference
token ids, for the Step 3 `TdtDecoderParityTest`) was also lost and was regenerated in Step 3
below.

## Step 2 — 128-mel features, with a golden test

`STTModule.kt`'s mel filterbank and `extractLogMelSpectrogram` were parameterized by `nMels`
(default `N_MELS=80`, so every existing call site — including `MelFeatureGoldenTest` — is
byte-for-byte unchanged). A new `SRAVAANI_N_MELS=128` constant and `SraVaaniMelGoldenTest` were
added, mirroring `MelFeatureGoldenTest`'s structure exactly.

**Golden reference source:** not the librosa-based `sravaani_onnx_infer.py::preprocess()` used for
Step 1's WER run (which omits preemphasis), but the model's **real, documented preprocessor** —
`SraVaaniProcessor`, loaded via `AutoProcessor.from_pretrained(..., trust_remote_code=True)` per
the model card's own "Sample Usage" section. Its `from_pretrained` expects a local directory
containing `tokenizer.model` and `preproc.pt` (both fetched directly from the HF repo
`ARTPARK-IISc/SraVaani-1.0` at revision `f5dd5358325a5208775b91dad98918e079ea2b27`, not from the
ONNX bundle). Its source (`processing_sravaani.py`, read in full) confirmed every param T77 Step 2
already found:

```
{'sample_rate': 16000, 'n_fft': 512, 'hop_length': 160, 'win_length': 400, 'preemph': 0.97,
 'mag_power': 2.0, 'log_zero_guard_value': 5.960464477539063e-08, 'normalize': 'per_feature',
 'pad_value': 0.0, 'CONSTANT': 1e-05}
```

— and its `_normalize_per_feature`/masking logic matches the Kotlin implementation's
`validLen = numFrames - 1` / last-frame-zeroed approach exactly (`seq_len = floor(wav_len/hop)`,
mean/variance over valid steps only, unbiased variance, then `masked_fill(pad_value=0.0)` beyond
`seq_len` — the same one-fewer-valid-frame convention already reproduced in
`extractLogMelSpectrogram`). `fb.shape == [1, 128, 257]` confirms the 128-band filterbank over the
512-point FFT's 257 bins.

**Fixture:** the same hi_in FLEURS clip validated end-to-end in Step 1
(`10011266027513218401.wav`, 145,920 samples @ 16kHz), run through the real `SraVaaniProcessor`.
Output `input_features` shape `[1, 128, 913]`, `feature_lengths=912` — matches the Kotlin formula
(`numFrames = audio.size/HOP_LENGTH + 1 = 912+1 = 913`, `validLen=912`) exactly.

Fixtures committed: `app/src/test/resources/fixture_sravaani.wav` (32-bit float mono WAV),
`app/src/test/resources/golden_features_sravaani.npy` (float32, shape `[128, 913]`).

**VERIFY:** `.\gradlew.bat :app:testDebugUnitTest` — both `MelFeatureGoldenTest` (unchanged,
80-mel) and `SraVaaniMelGoldenTest` (new, 128-mel) green at 1e-3 tolerance.

## Step 3 — `TdtDecoder.kt`, with a decode-parity test

New pure-Kotlin `app/src/main/java/com/itantra/core/audio/TdtDecoder.kt`, kept separate from ONNX
like `CtcDecoder.kt`, implementing the greedy TDT loop from the design doc §1c: `blank=5000`,
`durations=[0,1,2,3,4]`, `max_symbols=10`, LSTM state zeroed per utterance, token/duration logits
split at `vocab_size+1`. The `decoder_joint` ONNX call is taken as a function parameter
(`TdtDecoder.DecoderJointCall`) so the loop is unit-tested without a model.

### Parity fixture (regenerated after the runtime reset)

The encoder + decoder_joint pair was re-quantized (same method as Step 1; not re-verified against
the committed sha256s since a fresh Colab runtime produces a fresh file each time, but decoding
the same hi_in clip through them reproduced the **exact same token ids and hypothesis text** as
Step 1's original sanity check — confirming determinism). `decode_rnnt` was re-run on the same
fixture clip (`10011266027513218401.wav`) with every one of its 97 `decoder_joint` calls recorded:
the encoder time index and last-emitted-token fed in, the LSTM state in and out, and the full 5006
logits out. Fixtures committed under `app/src/test/resources/`:

| File | Shape/content |
| --- | --- |
| `parity_encoder_out.npy` | `[1024, 114]` float32 (encoder output, batch dim dropped) |
| `parity_encoder_len.txt` | `114` |
| `parity_trace_t.txt` / `parity_trace_last_token.txt` | 97 lines each — per-step encoder time index and last token |
| `parity_trace_h_in.npy` / `parity_trace_c_in.npy` | `[97, 640]` — LSTM state fed into each step |
| `parity_trace_logits.npy` | `[97, 5006]` — decoder_joint output at each step |
| `parity_trace_h_out.npy` / `parity_trace_c_out.npy` | `[97, 640]` — updated LSTM state from each step |
| `parity_reference_tokens.txt` | the 43 final emitted token ids |

`TdtDecoderParityTest` replays this trace through a stub `DecoderJointCall`: at every step it
asserts the encoder frame and last-token `TdtDecoder.decode` requests match the recorded ones
exactly (not just the final answer), then returns the recorded logits/state. **Passed** — every
step's inputs matched, and the decoded token list equals `parity_reference_tokens.txt` exactly.

### Tokenizer: piece-join rule vs `sp.decode()`

Exported SraVaani's SentencePiece vocabulary as a flat `<piece> <id>` file (`sravaani_tokens.txt`,
committed alongside this README; blank last, `<blk> 5000`, 5001 lines — same format
`CtcDecoder.parseTokens` reads). Rather than re-downloading ~4 GB of FLEURS audio across 8
languages to re-derive "every Step 1 hypothesis" (lost to the runtime reset), the piece-join rule
(`▁`→space, same as `CtcDecoder.decodeToken`) was checked against `sp.decode()` on **every one of
the 5000 vocabulary pieces individually** — broader coverage than a sample of hypotheses would
give.

**Result: 4999/5000 match exactly.** The one mismatch is id 0 (`<unk>`): `sp.decode([0])` renders
it as `" ⁇ "`, but the literal piece text is `"<unk>"`. Follow-up checks (embedding id 0 between
real tokens) showed the substitution `" ⁇ "` is correct in every position; the only remaining
divergence is a single incidental leading/trailing space when `<unk>` is the very first or last
token of the **whole utterance**, caused by `CtcDecoder`-style final `.trim()` — a whitespace-only
edge case, not a text-content one, and consistent with the trim already applied everywhere else in
this codebase.

**Decision (Gaurav):** special-case `UNK_ID=0` in `TdtDecoder.decodeToText` to emit `" ⁇ "`
directly, rather than adding a native SentencePiece dependency. Implemented and documented in
`TdtDecoder.kt`.

**Checkpoint 2 — PASSED.** `MelFeatureGoldenTest`, `SraVaaniMelGoldenTest`, and
`TdtDecoderParityTest` are all green (`.\gradlew.bat :app:testDebugUnitTest`). No override needed.
