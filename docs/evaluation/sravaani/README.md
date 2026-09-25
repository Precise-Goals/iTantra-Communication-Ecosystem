# T76 — SraVaani 1.0 vs IndicConformer

> Evaluation only. No app code changed in this task.
> Spec: `docs/IMPLEMENTATION_SPEC_2.md`, Group I → "T76 🔬 · Evaluate SraVaani 1.0 against the current IndicConformer models"

## Setup

- **Test data:** Google's `google/fleurs` (Hugging Face dataset), test split. First 100 clips per language, sorted by file name, taken from `data/<lang>/test.tsv` + `data/<lang>/audio/test.tar.gz`. All clips were already 16 kHz mono (no resampling needed). Languages: `hi_in, gu_in, mr_in, kn_in, ml_in, ta_in, te_in, or_in, bn_in, en_us`.
- **TSV columns used:** column 1 = audio file name, column 3 = normalised transcription (verified by inspecting raw rows — column 2 is the *raw* transcription and still contains punctuation such as parentheses; column 3 has it stripped).
- **IndicConformer:** the same files the app downloads, from `parismitaglobalsolutions/indicconformer-sherpa-onnx` (see `app/src/main/java/com/itantra/core/download/ModelRegistry.kt`), decoded with `sherpa-onnx==1.13.8`'s `OfflineRecognizer.from_nemo_ctc` (greedy search, 16 kHz, 80-dim features, `num_threads=1`, `provider="cpu"`). No Odia entry exists in this mirror, so IndicConformer has no Odia row.
- **SraVaani:** `ARTPARK-IISc/SraVaani-1.0` (MIT licence), loaded via the model card's own code (`transformers.AutoModel.from_pretrained(..., trust_remote_code=True)`), revision `f5dd5358325a5208775b91dad98918e079ea2b27`. Inference via the model card's documented `model.transcribe(paths, batch_size=1, return_hypotheses=True)`. `transcribe()`'s real signature (`audio, batch_size=8, return_hypotheses=False, timestamps=False, **kwargs`) has **no language parameter** — it is a single multilingual model that infers the language itself.
- **Versions:** Python 3.13.15, `torch==2.11.0+cpu`, `transformers==5.17.0`, `jiwer` (latest at run time), `sherpa-onnx==1.13.8`. Google Colab, CPU runtime.
- **Speed measurement:** both models were run with 1 CPU thread on the same Colab CPU runtime (`torch.set_num_threads(1)` for SraVaani; `num_threads=1` for sherpa-onnx). This is the same machine/thread-count for both, per the spec's requirement.
- **Scoring:** Unicode NFC → lowercase → strip all Unicode punctuation (category `P*`, which also covers the Devanagari danda `।`/`॥` since their category is `Po`) → collapse whitespace. Corpus WER and CER computed per language with `jiwer`.

## Results

All numbers are in `results.csv`; per-clip hypotheses are in `hypotheses/<model>_<lang>.tsv` (`clip_id`, `reference`, `hypothesis`) so any number here can be re-checked.

| Lang | IndicConformer WER | SraVaani WER | Δ (points, +ve = SraVaani better) | IndicConformer CER | SraVaani CER | IndicConformer RTF | SraVaani RTF |
| --- | --- | --- | --- | --- | --- | --- | --- |
| hi | 11.46% | 9.38% | +2.08 | 4.63% | 3.62% | 0.1721 | 0.2792 |
| gu | 19.57% | 19.52% | +0.05 | 5.53% | 6.00% | 0.1677 | 0.3078 |
| mr | 19.80% | 19.16% | +0.64 | 5.33% | 5.86% | 0.1786 | 0.2690 |
| kn | 17.40% | 17.96% | −0.56 | 4.49% | 4.19% | 0.1766 | 0.2861 |
| ml | 23.36% | 19.00% | +4.36 | 5.82% | 5.32% | 0.1772 | 0.2928 |
| ta | 31.94% | 33.03% | −1.09 | 16.55% | 17.22% | 0.1781 | 0.2952 |
| te | 21.81% | 21.10% | +0.71 | 6.32% | 6.10% | 0.1699 | 0.2963 |
| bn | 14.57% | 15.32% | −0.75 | 3.97% | 4.62% | 0.1746 | 0.2888 |
| en | 12.73% | 22.16% | −9.43 | 7.16% | 10.38% | 0.0682 | 0.3002 |
| or | — (no model) | 21.69% | — | — | 6.08% | — | 0.2947 |

**Average over the 9 shared languages:**

| | IndicConformer | SraVaani |
| --- | --- | --- |
| Avg WER | **19.18%** | **19.63%** |
| Avg CER | 6.64% | 7.03% |
| Avg RTF (1 CPU thread) | 0.1626 | 0.2910 |
| Size on disk | ~188 MB per language (only downloaded languages) | ~903 MB (one shared multilingual file, all 65 languages) |
| Load time | 682 ms – 2.41 s (varies by language model) | not separately measured (model kept resident across languages in this run) |

SraVaani is **0.44 points worse** on average WER over the 9 shared languages, not better. It wins on 5 of 9 languages (notably Hindi +2.08 and Malayalam +4.36) but loses badly on English (−9.43 points) and slightly on Kannada, Tamil and Bengali, which erases its gains elsewhere. It is also ~1.8× slower per audio-second on CPU and ships as a single ~903 MB file versus ~188 MB per language for IndicConformer.

Language-ID check: since `transcribe()` gives no explicit detected-language field, we used a Unicode-script heuristic (flag a hypothesis if less than half its letters are in the expected script for that language) as a proxy. **0/100 mismatches on every language** — SraVaani never produced output in a grossly wrong script. This heuristic cannot detect confusion between languages that share a script (e.g. Hindi/Marathi, both Devanagari), so it is a lower bound on language-ID errors, not a full check.

## Verdict

Per the decision rule in `docs/IMPLEMENTATION_SPEC_2.md` T76:

| Result | Verdict |
| --- | --- |
| SraVaani INT8 ≥3pts better **and** ≤~500MB **and** phone RTF ≤0.5 **and** fits 4GB phone | Adopt |
| Better on accuracy, but too big/slow | Use for Odia only |
| **Not ≥3 points better** | **Keep IndicConformer** |

**Verdict: Keep IndicConformer.** SraVaani's average WER (19.63%) is not better than IndicConformer's (19.18%) — it is 0.44 points *worse* — so it fails the first gate outright. Step 6 (INT8 quantization + on-phone timing) was **not run**, since it only applies if SraVaani wins on accuracy.

This decides **G5** (`docs/WORK_SPLIT.md` §4): Gaurav should proceed with **T64** (export Odia STT from AI4Bharat's checkpoint), not the SraVaani switch design.

## Limitations

- Read speech only (FLEURS), not spontaneous/noisy speech representative of real walkie-talkie use.
- 100 clips per language — enough to rank the two models but not a tight confidence interval.
- Desktop CPU (Colab), not the target phone. Step 6's on-phone timing was skipped because the accuracy gate wasn't met, so we have no phone-side RTF or memory number for SraVaani.
- IndicConformer here uses sherpa-onnx's reference Kotlin/Python-equivalent feature extraction, not the app's own hand-written Kotlin mel pipeline — until T23/T29 land, the app's real on-device WER may be slightly worse than this number (see the note in `docs/WORK_SPLIT.md` §5 S1).
- The script-based language-ID check cannot distinguish confusions between languages sharing the same script (Hindi ↔ Marathi, both Devanagari).
- `size_mb` for SraVaani is the size of the single shared multilingual model file (~903 MB, all 65 languages), not a per-language figure — it is not directly comparable to IndicConformer's per-language 188 MB on a per-download basis, but it is the number relevant to the ≤500MB decision-rule gate, which SraVaani would fail regardless of its WER.
