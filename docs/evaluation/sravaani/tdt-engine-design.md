# SraVaani TDT engine — design sketch

> **Scheduled 2026-09-25 as T78** (`docs/IMPLEMENTATION_SPEC_2.md` Group I), chosen by Gaurav over T64. T64 is the fallback. The spec adds the order of work and the checkpoints, and treats the sizes and effort below as estimates to be replaced by T78 Step 1's measurements.

> Written after T77 (`docs/evaluation/sravaani/phone/README.md`, PR #29) concluded "keep
> IndicConformer, start T64" on the CTC route it actually tested. This document scopes the
> **other** route T77 found and did not pursue — SraVaani's native TDT (token-and-duration
> transducer) decoder — because it is the one with a real accuracy upside. **Design only. No app
> code changes are proposed or made here.** This is input for Gaurav and Sarthak to decide whether
> it's worth scheduling in place of, or alongside, T64 — it is not a recommendation to build it.

## Why this exists

T76 (`docs/evaluation/sravaani/README.md`) measured SraVaani's TDT decoder (its native, trained
decode path) at **19.31% avg WER over the 8 non-English shared languages, beating IndicConformer's
19.99%.** T77 then measured SraVaani's *CTC* head — a secondary head on the same checkpoint — at
**22.44%, losing to IndicConformer.** T77 recommended against CTC because it wins on nothing: worse
accuracy, and (via the CTC route it built) no path around the 128-vs-80 mel-channel mismatch either.

But T77 never tested TDT itself on-device, because sherpa-onnx has zero documented support for it
(confirmed by grepping the installed package's source for `"tdt"` — zero matches). That's a missing
*library feature*, not a missing *model capability*. SraVaani's TDT decode is a small, well-defined
algorithm (`modeling_sravaani.py::_greedy_one`, already read in full during T77) that can be
hand-written in Kotlin, the same way `CtcDecoder.kt` is hand-written today — the app doesn't use
sherpa-onnx's recognizer API for STT at all; it uses `ai.onnxruntime.OrtSession` directly with a
hand-rolled feature extractor and decoder. Adding a second hand-rolled decoder is consistent with
the existing architecture, not a new kind of dependency.

## What has to change (file by file)

### 1. `app/src/main/java/com/itantra/core/audio/STTModule.kt`

**a. A second, per-model feature-channel count.** `N_MELS = 80` is a `companion object` constant
today (line 41), used to size `powerSpectrum`, `melBank`, the scratch buffers (`fftRe`, `fftIm`),
and the final `[1, N_MELS, numFrames]` `OnnxTensor` shape (`transcribeUnlocked`, line 353). SraVaani
needs 128. This has to become a per-language (or per-model-family) value, not a class-wide constant
— `buildMelBank()` already takes no per-call parameters and is built once via `by lazy`; it would
need to become parameterized (or duplicated) so a SraVaani-backed language builds a 128-band filter
bank instead of the existing 80-band one. Every other STFT parameter (`FRAME_LENGTH=400`,
`HOP_LENGTH=160`, `N_FFT=512`, preemphasis, log-guard, per-feature normalization) is **already
identical** between SraVaani and the current NeMo/IndicConformer pipeline (verified field-by-field
in T77 Step 2) — this is a parameterization of one existing pipeline, not a second pipeline written
from scratch.

**b. A second decode path.** `resolveIoNames()` / `IoNames` / `FEATURE_INPUT_ALIASES` /
`LENGTH_INPUT_ALIASES` (lines 48–51, 285–305) assume a single-graph, single-output CTC interface.
TDT needs:
  - Two ONNX sessions per active language instead of one: an encoder session (`audio_signal[1,128,T]`
    + `length[1]` → `outputs[1,1024,T']` + `encoded_lengths[1]`) and a decoder_joint session, run
    once per emitted symbol rather than once per utterance.
  - Autoregressive per-utterance state: a running `last_token` (initialized to `blank_id`), and LSTM
    predictor hidden/cell state `(h, c)`, both `[pred_rnn_layers=1, 1, pred_hidden=640]` float32,
    zero-initialized per utterance and threaded through each decode step (never across utterances).
  - `sessionCache: LinkedHashMap<String, OrtSession>` (line 58) is single-session-per-language today,
    with LRU eviction under `MAX_CACHED_LANGUAGES = 2`. A TDT-backed language needs *two* native
    sessions resident together (encoder + decoder_joint) — the eviction accounting (currently "one
    session = ~197MB, cache 2 max") needs to change to whatever the pair's combined footprint is
    (see §4, sizes not yet measured for the quantized pair).

**c. The decode algorithm itself** (new file, e.g. `core/audio/TdtDecoder.kt`, mirroring how
`CtcDecoder.kt` is kept separate/pure for testability) — ported from `modeling_sravaani.py`'s
`_greedy_one`, confirmed against the model card's own documented reference implementation
(`sravaani_onnx_infer.py`'s `decode_rnnt`, read in full during T77 Step 1):

```
encoder_out, encoder_len = run(encoder_session, {audio_signal, length})  # once per utterance

h, c = zeros[1,1,640], zeros[1,1,640]
last_token = BLANK_ID  # 5000
tokens = []
t = 0
while t < encoder_len:
    frame = encoder_out[:, :, t:t+1]                      # [1, 1024, 1]
    for _ in range(max_symbols):                            # max_symbols = 10 (config.json)
        logits, _, h2, c2 = run(decoder_joint_session, {
            "encoder_outputs": frame,                        # float32 [1,1024,1]
            "targets":         [[last_token]],                # int32  [1,1]
            "target_length":   [1],                            # int32  [1]
            "input_states_1":  h,                               # float32 [1,1,640]
            "input_states_2":  c,                               # float32 [1,1,640]
        })
        token_logits    = logits[0,0,0][:vocab_size+1]         # 5001 wide
        duration_logits = logits[0,0,0][vocab_size+1:]         # 5 wide
        token = argmax(token_logits)
        skip  = durations[argmax(duration_logits)]             # durations = [0,1,2,3,4]
        if token == BLANK_ID:
            t += max(1, skip)
            break
        tokens.append(token)
        last_token, h, c = token, h2, c2
    else:
        t += 1                                                  # safety valve, max_symbols hit
```

The exact `decoder_joint.onnx` input/output **names** above (`encoder_outputs`, `targets`,
`target_length`, `input_states_1`, `input_states_2`) come directly from the model card's own
`sravaani_onnx_infer.py` — not guessed. Their exact declared **shapes/dtypes** were not re-verified
against the live graph with `onnxruntime.InferenceSession.get_inputs()` in T77 (only
`encoder-sravaani.onnx` and `ctc-sravaani.onnx` were inspected that way); that inspection is a
required first step before writing any code, not an assumption to carry into implementation.

**d. Tokenizer.** SraVaani ships a SentencePiece `tokenizer.model` (binary), not the app's current
`tokens.txt` format that `CtcDecoder.parseTokens` reads. Two options:
  - Bundle a SentencePiece decoder (native lib) — adds a new native dependency.
  - Export the vocabulary as a flat `<piece> <id>` list (same shape as today's `tokens.txt`,
    trivially generated from `sp.id_to_piece(i)` the same way T77's `tokens.txt` was built) and
    decode with a simple join rule: concatenate pieces, replace SentencePiece's `▁` word-boundary
    marker with a space, strip. `CtcDecoder.decodeToken` (line 29) already does exactly this
    `▁`-to-space replacement for the existing NeMo BPE vocabularies — the same rule is very likely
    sufficient for SraVaani's SentencePiece pieces too (both are BPE-family), avoiding a new native
    dependency, but this needs a side-by-side check against `sp.decode()`'s real output on a range
    of hypotheses (including languages with conjuncts/combining marks) before relying on it.

### 2. `app/src/main/java/com/itantra/core/download/ModelRegistry.kt`

New download entries for the encoder + decoder_joint pair, following the `hostedSttInfo` pattern
T64 already introduces for self-hosted models (self-hosted because, like Odia, this isn't on the
`parismitaglobalsolutions/indicconformer-sherpa-onnx` mirror). Two files instead of one per
"SraVaani-backed" language, but the same *shared* files serve all nine — not one pair per language,
unlike the existing per-language IndicConformer entries.

### 3. `app/src/main/java/com/itantra/domain/model/ModelManifest.kt`

The `STT_*` pack pattern (`sizeMb`, `isRequired`, `requiredFor`) assumes one file per language. A
SraVaani-backed pack is shared across nine languages, so either one new pack
("STT_SRAVAANI_SHARED") gates download once and `sttPackFor` maps nine language codes to it, or the
manifest model needs a real change to express "one download unlocks N languages" — worth scoping
explicitly since today's manifest has no such concept.

### 4. Sizes — measured vs. estimated

**Measured (T77):** encoder + CTC head merged, INT8: 481.6 MB.
**Not measured — estimate only:** encoder + decoder_joint (the TDT pair) has not been merged or
quantized. `ctc-sravaani.onnx` (20.5 MB FP32) is small relative to the 1.77 GB FP32 encoder, so
encoder-INT8 alone is very likely close to the measured 481.6 MB. `decoder_joint-sravaani.onnx` is
43 MB FP32 and is mostly matmul/LSTM weight, which dynamic quantization handles well — a rough
estimate is another 10–20 MB INT8, giving **~490–500 MB total for the shared TDT pair** (all nine
languages) — smaller than the 903 MB FP16 single-file figure T76 reported, because that figure
included both decode heads unquantized. **This number must be measured, not assumed, before it goes
into any decision table** — the same `onnx.compose`/`quantize_dynamic` steps used in T77 Step 3
apply directly, just without the CTC merge.

If ~500 MB holds: total hybrid-TDT flash footprint ≈ 500 MB (SraVaani, shared) + 188 MB (English,
IndicConformer) ≈ **~690 MB**, against IndicConformer's current ~1.84 GB (9 languages + Odia via
T64) — a larger reduction than T77's CTC-based estimate, and with an accuracy edge instead of a
deficit.

### 5. Testing

- **Feature-parity golden test**, same shape as T29's `MelFeatureGoldenTest.kt`: a real FLEURS clip
  run through SraVaani's actual `preproc.pt`-based preprocessor (Python, already scripted in T77),
  compared against the new Kotlin 128-mel path at the same tolerance (1e-3).
- **Decode-parity golden test**: fixed encoder output (saved once from Python) run through the new
  `TdtDecoder.kt` loop, compared token-for-token against `sravaani_onnx_infer.py`'s `decode_rnnt` on
  the same input. This is the higher-risk piece — an off-by-one in the duration/frame-skip logic or
  the `max_symbols` guard would silently produce wrong transcripts rather than crash, so parity needs
  checking directly, not inferred from end-to-end WER alone.
- **Phone run**: T77's Step 5 protocol (baseline vs. swap, both phones, RTF/PSS/ANR) becomes
  runnable for the first time, since the 128-mel mismatch that blocked it is exactly what §1a fixes.

## Effort estimate (rough — not a committed plan)

| Piece | Estimate |
| --- | --- |
| Parameterize the mel pipeline (128 vs 80) + golden test | 0.5–1 day |
| `TdtDecoder.kt` + dual-session loading in `STTModule` | 1.5–2 days |
| Tokenizer decode (piece-join, verify against `sp.decode()`) | 0.5 day |
| Registry/manifest changes (shared-pack download model) | 0.5–1 day |
| Decode-parity golden test | 0.5 day |
| Phone re-run (T77 Step 5, now runnable) | ~1 day |
| **Total** | **~4.5–6 days**, plus review |

For comparison, T64 (Odia-only export, reusing the existing CTC interface and decode path
unchanged) is a smaller, lower-risk job — no new Kotlin decode logic, no new session-management
shape, no new tokenizer handling, just an export + registry + manifest change matching a pattern
already shipped for nine languages.

## Trade-off, for Gaurav and Sarthak

| | T64 (Odia export) | TDT engine (this doc) |
| --- | --- | --- |
| Coverage | 10/10 languages | 10/10 languages |
| Accuracy (8 non-English avg, desktop) | unchanged (IndicConformer, 19.99%) | 19.31% (T76, TDT — not yet re-verified on the quantized INT8 pair) |
| Flash footprint (10 languages) | ~1.84 GB | ~690 MB (estimate, §4 — not measured) |
| New Kotlin surface | none | new decode algorithm, dual-session loading, new tokenizer path |
| Risk | low — same pattern as the other 9 languages | medium — new algorithm class, needs its own correctness verification (decode-parity test) |
| Time | small (export + registry, similar to T64 Step 1–7) | ~1 engineering-week, per the estimate above |

Both are real options. This document doesn't pick one — it exists so that choice can be made with
real numbers instead of "SraVaani doesn't work here," which was true for the CTC route T77 tested
but is not the full picture.
