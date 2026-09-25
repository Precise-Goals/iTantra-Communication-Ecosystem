# T77 — SraVaani INT8 on the phone: hybrid vs T64

> Spec: `docs/IMPLEMENTATION_SPEC_2.md`, Group I → "T77 🔬 · SraVaani INT8 on the phone: decide
> hybrid vs T64". Depends on T76 (`docs/evaluation/sravaani/README.md`, PR #27).
> **No merged app code changed in this task.**

## Outcome up front

> **Decision 2026-09-25: the team goes with the TDT engine (T78), not T64.** This README tested the **CTC** route, and its measurements and CTC verdict stand as written. The team then chose to build SraVaani's native **TDT** decoder in Kotlin (`docs/IMPLEMENTATION_SPEC_2.md` → T78, design in `../tdt-engine-design.md`). T64 is T78's fallback. See "Final decision" at the end.

**Steps 1–4 (Colab, desktop) ran to completion. Step 5 (the on-phone swap) was not run** — it
cannot run without an `STTModule` code change, for a reason discovered in Step 2 (below), and by
the time Step 4's numbers came in, the decision was already determined by two independent hard
fails from the desktop measurements alone. Running the phone hardware step would not have changed
the outcome, only reconfirmed it, so it was skipped rather than spend two phones' worth of time on
a foregone conclusion. If Gaurav/Sarthak want the phone hardware time spent anyway (e.g. to get an
IndicConformer-only phone baseline for some other purpose), that's a separate ask from T77.

## Step 1 — finding an export route

Loaded `ARTPARK-IISc/SraVaani-1.0` at revision `f5dd5358325a5208775b91dad98918e079ea2b27` (same as
T76) via `transformers.AutoModel.from_pretrained(..., trust_remote_code=True)`. The repo is gated;
needed an HF account with access approved and a token (`huggingface_hub.notebook_login()`).

The loaded class is `SraVaaniTDTModel` — a `transformers`-custom-code wrapper, **not** a
`nemo_toolkit` `ASRModel`. Its attribute list has no `set_export_config`, `cfg`, `ctc_decoder`, or
`restore_from`. `modeling_sravaani.py`'s own docstring says *"No nemo_toolkit"*. This HF repo ships
only a combined TorchScript graph (`model-asr.fp16.ts`, exposing `.encoder` and `.decoder_joint`
submodules), `preproc.pt`, and `tokenizer.model` — no `.nemo` checkpoint, no CTC artifact. Read
literally, T77's Route A ("CTC-only ONNX ... on a NeMo hybrid model, if the wrapper exposes one")
looked dead: there is no CTC head anywhere in this specific HF repo, and the model is architecturally
a TDT (token-and-duration transducer) — `config.json` has `durations: [0,1,2,3,4]`,
`num_durations: 5`, `blank_id = vocab_size = 5000`, and the decode loop
(`SraVaaniTDTModel._greedy_one`) jointly predicts a token *and* a frame-skip duration, which is
structurally nothing like CTC.

Route B (TDT transducer via sherpa-onnx) was also checked and found dead on the same "documented,
not guessed" basis: the installed `sherpa-onnx==1.13.8`'s `OfflineRecognizer.from_transducer()`
takes a standard `encoder.onnx` + `decoder.onnx` + `joiner.onnx` (documented for "Zipformer, NeMo
transducer, etc.") with **no duration output and no TDT decoding logic** — a full grep of the
installed package's source for `"tdt"` returned zero matches anywhere.

**The actual route came from reading the model card's own README** (not read until this point in
the investigation — a real gap in the initial pass), which states the base model **is** a "hybrid
TDT-CTC decoder", documents a separate **ONNX Export** bundle (linked from the card, not the HF
`trust_remote_code` repo), and points to a GitHub repo
(`ARTPARK-Speech-Models/SraVaani`) confirming the underlying checkpoint is "Hybrid RNN-T + CTC BPE".
The linked ONNX bundle (`sravaani_onnx/`) contains, separately:

- `encoder-sravaani.onnx` (1.77 GB, FP32)
- `decoder_joint-sravaani.onnx` (43 MB — the TDT path, not used here)
- **`ctc-sravaani.onnx` (20.5 MB — a real, working CTC head)**
- `sravaani_onnx_infer.py` — the card's own documented reference inference script, with both
  `--decoder rnnt` and `--decoder ctc` modes
- `tokenizer.model`

So Route A **is** real, just shipped as a separately-linked ONNX bundle rather than exposed through
the gated HF wrapper. Credit where due: this was found only because the plan to stop at Step 1 was
questioned and re-checked against the model card's full documentation rather than the first
`AutoModel` load.

## Step 2 — preprocessor comparison

`ctc-sravaani.onnx` shares the same encoder as the TDT path, so it needs the same input features.
Compared `preproc.pt`'s params field-by-field against `docs/evaluation/nemo_preprocessor_hi.txt`
(the real IndicConformer/NeMo config recorded for T23/T29):

| Field | IndicConformer (T23) | SraVaani (`preproc.pt`) |
| --- | --- | --- |
| sample_rate | 16000 | 16000 |
| n_fft | 512 | 512 |
| hop_length | 160 | 160 |
| win_length | 400 | 400 |
| preemph | 0.97 | 0.97 |
| mag_power | 2.0 | 2.0 |
| log_zero_guard_value | 2⁻²⁴ (5.9604645e-8) | 5.960464477539063e-08 (identical) |
| normalize | per_feature, unbiased | per_feature, `CONSTANT=1e-05` (identical formula) |
| pad_value | 0 | 0.0 |
| window | symmetric Hann (N-1) | confirmed symmetric Hann via `torch.allclose` |
| **N_MELS (feature channels)** | **80** | **128** |

Every field matches exactly **except the mel-channel count: 128 vs 80.** This is not a soft/numeric
difference — `encoder-sravaani.onnx`'s `audio_signal` input has a **hardcoded static shape**
`[dynamic, 128, dynamic]` (confirmed via `onnxruntime.InferenceSession.get_inputs()`), and
`STTModule.kt` hardcodes `N_MELS = 80` throughout its feature-array and tensor-shape construction
(`app/src/main/java/com/itantra/core/audio/STTModule.kt:41`, used to build the
`[1, N_MELS, numFrames]` input tensor). Feeding an 80-channel tensor into a graph that requires a
static 128 would fail with an ONNX Runtime shape-mismatch error on the very first inference call —
not degraded accuracy, a hard crash. This can only be fixed by changing `STTModule`'s feature
pipeline, which T77's rules place out of scope ("Do not change STTModule's feature pipeline to suit
SraVaani here").

**Consequence for Step 5:** the on-phone "swap the file, no code change" test cannot physically run.

## Step 3 — export, merge, quantize

`encoder-sravaani.onnx` (`audio_signal[*,128,*]`, `length[*]` → `outputs[*,1024,*]`,
`encoded_lengths[*]`) and `ctc-sravaani.onnx` (`encoder_output[*,1024,*]` →
`logprobs[*,*,5001]`) were merged into a single graph with `onnx.compose.merge_models`
(`io_map=[("outputs", "ctc_encoder_output")]`, after `onnx.compose.add_prefix` on the CTC graph to
resolve an internal node-name collision, and after de-duplicating the merged `opset_import` list —
both are documented `onnx.compose` mechanics, not app-specific choices). The unused
`encoded_lengths` output was dropped so the merged graph has the single CTC-log-probs output
`STTModule` expects.

Quantized with `quantize_dynamic(..., weight_type=QuantType.QUInt8)`, exactly as T64 Step 3
specifies.

- **Interface:** inputs `audio_signal` (float32) + `length` (int64), output `ctc_logprobs`
  (float32, last dim 5001). The input/output *names* match what `STTModule.resolveIoNames()`
  accepts — only the feature-channel *shape* is incompatible (Step 2).
- **Size: 481.6 MB** (sha256 `0df6334bff8c599ed316ac7eda4d9b7389b380713ad4a5c91ae8fe64ab35a4ad`) —
  within the spec's expected ~430–500 MB band, not over 600 MB.
- **tokens.txt:** generated from the model's own SentencePiece vocabulary (`<piece> <id>` per line,
  ids 0..4999, `<blk> 5000` last) — 5001 lines, matching the graph's output dimension exactly.
- Dynamic quantization only touches MatMul/Gemm weights; the many "unsupported type to quantize"
  warnings for `Slice`/`Tile` ops (attention indexing, depthwise conv slicing) are expected —
  those ops stay FP32, which is normal for `quantize_dynamic` on a Conformer-style architecture.

## Step 4 — desktop accuracy of the INT8 CTC head

Same 100 FLEURS clips per language as T76 (first 100 rows of `data/<lang>/test.tsv` sorted by
filename, column 1 = filename, column 3 = normalized transcription — verified directly against the
raw TSV rows this run, since the actual column layout did not match the wording in T76's README on
first attempt), same NFC → lowercase → strip Unicode `P*` punctuation → collapse-whitespace scoring,
`jiwer` for corpus WER/CER. Features from SraVaani's own preprocessor (Step 2), inference on the
merged INT8 graph (Step 3), greedy CTC decode (argmax, merge repeats, drop blank = id 5000), decoded
to text with the model's own SentencePiece tokenizer. Full per-language numbers in
`docs/evaluation/sravaani/results_int8.csv`.

| Lang | IndicConformer WER (T76) | SraVaani TDT WER (T76) | **SraVaani INT8 CTC WER** | CER (INT8 CTC) | RTF (INT8 CTC, 1 thread) |
| --- | --- | --- | --- | --- | --- |
| hi | 11.46% | 9.38% | **10.03%** | 3.88% | 0.160 |
| gu | 19.57% | 19.52% | **24.58%** | 8.14% | 0.161 |
| mr | 19.80% | 19.16% | **29.12%** | 9.01% | 0.161 |
| kn | 17.40% | 17.96% | **18.63%** | 4.37% | 0.161 |
| ml | 23.36% | 19.00% | **20.44%** | 5.58% | 0.158 |
| ta | 31.94% | 33.03% | **34.00%** | 17.13% | 0.160 |
| te | 21.81% | 21.10% | **23.17%** | 6.60% | 0.157 |
| bn | 14.57% | 15.32% | **19.53%** | 6.06% | 0.158 |
| or | — (no model) | 21.69% | **22.68%** | 6.38% | 0.157 |

**Average over the 8 non-English shared languages:**

| | IndicConformer | SraVaani TDT (T76) | **SraVaani INT8 CTC** |
| --- | --- | --- | --- |
| Avg WER | 19.99% | 19.31% | **22.44%** |
| Avg CER | ~6.58% | 6.62% | **7.60%** |
| Avg RTF (1 CPU thread) | ~0.174 | ~0.290 | **~0.159** |

The INT8 CTC head is **worse on WER than both** IndicConformer and the original TDT decoder,
losing on 6 of 8 non-English languages (wins only on Hindi and Malayalam). This is consistent with
CTC commonly being trained as a secondary/auxiliary head in NeMo-style hybrid RNNT+CTC models, and
with the spec's own suspicion ("The CTC head in INT8 can score differently"). One number that
genuinely favours SraVaani: desktop RTF is faster than both alternatives (~0.159 vs ~0.174 and
~0.290), consistent with CTC being a single forward pass versus the TDT decoder's per-token
autoregressive loop — reported here per the rule to report every number, including ones that would
otherwise be buried by the accuracy loss.

## Step 5 — not run

Per the rules for this task, Step 5 requires "no merged app code" and swapping only the model files
into the existing Hindi slot. Step 2 established that this is not possible: `encoder-sravaani.onnx`
has a hardcoded static 128-channel feature input, while `STTModule` can only ever produce an
80-channel tensor. The swap would fail immediately with an ONNX Runtime shape-mismatch error, before
producing any RTF, latency, or memory number. Since Step 4's desktop numbers already trip a hard
fail (below) independent of the phone, running the hardware step would not have changed the
decision — so it was not run. Phone hardware time was not spent on a foregone conclusion.

## Step 6 — decision

**Hard fails (per the spec's own rule) — two independent ones apply here:**

- ~~The app is killed or shows an ANR during the 10-minute session~~ — not tested (Step 5 not run).
- ~~The RTF is ≥ 1.0~~ — not tested on-phone; desktop RTF (~0.159) is well under 1.0, but this alone
  cannot substitute for a phone measurement and doesn't matter given the next row.
- **The INT8 CTC average WER over the 8 non-English languages is worse than IndicConformer's.**
  **22.44% vs 19.99% — CONFIRMED. This alone is a hard fail per the spec's rule**, regardless of
  phone results.

Because of the mel-channel structural incompatibility (Step 2) *and* the accuracy hard fail
(Step 4), the rubric table below is filled with what's measurable and what genuinely is not:

| PS criterion (weight) | Metric | IndicConformer | Hybrid (SraVaani + IndicConformer en) | Better |
| --- | --- | --- | --- | --- |
| Accuracy (40%) | STT languages working | 9/10 (10/10 only if T64 works) | 10/10 | Hybrid, on coverage alone |
| Accuracy (40%) | WER, 8 non-English shared languages | 19.99% | 22.44% | **IndicConformer** |
| Latency (20%) | Speech end → STT complete, both phones | not measured (Step 5 not run) | not measured — cannot run without an `STTModule` code change | — |
| Latency (20%) | RTF, median, both phones | not measured on-phone | not measured on-phone; desktop RTF 0.159 (faster than IndicConformer's desktop 0.174) | inconclusive without phone data |
| Efficiency (20%) | Model flash, all 10 languages | ~1.84 GB (9 models + Odia from T64) | 481.6 MB (INT8 CTC, all 9) + 188 MB (English) = 669.6 MB | Hybrid, on size alone |
| Efficiency (20%) | Peak TOTAL PSS, both phones | not measured (Step 5 not run) | not measured — cannot run without an `STTModule` code change | — |

Team targets (`ACTION_PLAN.md` §5: RTF < 0.5, speech end → STT complete < 1.2 s, peak PSS < 700 MB)
are guides, not gates, and are moot here since the hybrid's on-phone numbers don't exist to compare
against them.

**Per the spec's outcome table:** *"A hard fail, or IndicConformer clearly wins → Keep
IndicConformer, do T64 for Odia. If T64's export fails, ship SraVaani for Odia only."* Coverage and
flash size favour the hybrid, but the hard-fail rule is explicit that accuracy (or a phone hard
fail) overrides — and here the accuracy gate fails outright, on real desktop measurements, before
the phone question even arises.

> That outcome is for the CTC route. The TDT route was not covered by this table and is now T78, which has its own checkpoints.

## Recommendation

> **Superseded 2026-09-25 by the team decision below.** Kept as written: it is the correct recommendation *for the CTC route* this task measured, and T64 is still the fallback if T78 fails.

**Keep IndicConformer. Start T64 (export Odia from AI4Bharat's checkpoint) today.** If T64's export
fails, fall back to SraVaani for Odia only, using the INT8 CTC graph and `results_int8.csv`'s Odia
row (22.68% WER) produced in this task — noting it is a few points worse than SraVaani's original
TDT-mode Odia number (21.69%, T76) and would need its own accuracy check against whatever T64
produces before being preferred.

A note for anyone revisiting the hybrid idea later: the CTC head's accuracy loss might be
improvable (e.g. if ARTPARK ever publishes a version fine-tuned with more weight on the CTC
objective), but as measured here it is not close enough to reconsider, and the 128-vs-80 mel
mismatch would still need a real `STTModule` engineering change (a second, parallel feature
pipeline) before any phone test could even run.

**The more promising route this task did not build:** SraVaani's *native TDT decoder* (not CTC)
scored better than IndicConformer in T76 (19.31% vs 19.99% avg WER, 8 non-English languages) —
T77 tested CTC instead only because sherpa-onnx has no TDT decoding support to lean on, not because
TDT itself is worse. `docs/evaluation/sravaani/tdt-engine-design.md` scopes what a hand-written
Kotlin TDT decode engine would actually require (files, algorithm, effort estimate, ~4.5–6 days) as
input for a real go/no-go decision — it is a design sketch only, not a recommendation to build it,
and no code has been written.

## Final decision

**Decided 2026-09-25 by Gaurav: build the SraVaani TDT engine (T78). T64 is the fallback.**

- **CTC route (this README): rejected.** INT8 CTC averaged 22.44% WER on the 8 non-English languages, against 19.99% for IndicConformer.
- **TDT route: chosen.** It scored 19.31% in T76 (full precision), the native decoder needs a Kotlin engine, and it is estimated at ~690 MB for all 10 languages against ~1.84 GB.
- **Stop rules (T78):** any failure below stops T78, and T64 starts the same day.
  - **C1 (day 1):** INT8 TDT WER ≤ 19.99% on the 8 non-English languages, and the pair ≤ ~550 MB.
  - **C2:** the 128-mel golden test and the decode-parity test pass.
  - **C3:** on the low-range phone, no kill or ANR, RTF < 1, and STT time ≤ ~1.5× IndicConformer.
  - **Day 7:** hard stop.
- **Last resort for Odia:** if T64 also fails, ship the INT8 CTC graph from this task (Odia 22.68% WER).

Signed: Gaurav (2026-09-25) · Sarthak: ______ (confirm in PR #29)

## Limitations

- Step 5 (on-phone RTF, latency, memory, ANR/low-memory-killer check) was not run — see above. No
  phone RTF, memory, or stability number exists for either model from this task.
- `results_int8.csv` measures the merged encoder+CTC INT8 graph on a Colab CPU runtime (1 thread,
  matching T76's protocol), not the target phone hardware.
- The CTC head shares the FP32 encoder with the TDT path; only the small CTC projection layer
  (~20 MB before quantization) is CTC-specific. If the encoder itself was trained with the CTC
  objective under-weighted relative to TDT, that would show up exactly as the accuracy pattern seen
  here and cannot be distinguished from a quantization-only effect with the tools used in this task.
- Read speech only (FLEURS), same caveat as T76.
