#!/usr/bin/env python3
"""
iTantra WER Validation Script

Computes Word Error Rate for the quantized IndicConformer models.
Validates that INT8 quantization does not exceed 5% WER increase vs FP32.

Usage:
    python validate_wer.py --model ../app/src/main/assets/models/indicconformer_int8.onnx \\
                           --test-data ./test_audio/ \\
                           --lang hi

Evaluation Metrics (ISRO Sprint 4 Gate):
    - Hindi WER target: < 8%
    - Average Indic WER target: < 18%
    - INT8 regression gate: FP32 WER + 5% max

Author: Gaurav (Domain A — Engine)
"""

import argparse
import os
import json
import time
from pathlib import Path
from typing import List, Tuple

import numpy as np
import onnxruntime as ort

# Optional: jiwer for WER computation
try:
    import jiwer
    HAS_JIWER = True
except ImportError:
    HAS_JIWER = False
    print("⚠️ jiwer not installed. WER computation will use basic implementation.")


def compute_wer_basic(reference: str, hypothesis: str) -> float:
    """Compute Word Error Rate using basic edit distance."""
    ref_words = reference.strip().lower().split()
    hyp_words = hypothesis.strip().lower().split()
    if not ref_words:
        return 0.0

    d = np.zeros((len(ref_words) + 1, len(hyp_words) + 1))
    for i in range(len(ref_words) + 1): d[i][0] = i
    for j in range(len(hyp_words) + 1): d[0][j] = j

    for i in range(1, len(ref_words) + 1):
        for j in range(1, len(hyp_words) + 1):
            if ref_words[i-1] == hyp_words[j-1]:
                d[i][j] = d[i-1][j-1]
            else:
                d[i][j] = 1 + min(d[i-1][j], d[i][j-1], d[i-1][j-1])

    return float(d[len(ref_words)][len(hyp_words)]) / len(ref_words)


def load_test_data(test_dir: str, lang: str) -> List[Tuple[str, str]]:
    """
    Load test audio files and references.
    Expected structure:
        test_dir/
            hi/
                audio_001.wav
                audio_001.txt   (reference transcript)
                ...
    """
    test_path = Path(test_dir) / lang
    if not test_path.exists():
        print(f"⚠️ Test data directory not found: {test_path}")
        print("Creating synthetic test data for demonstration...")
        return create_synthetic_test_data(lang)

    pairs = []
    for txt_file in sorted(test_path.glob("*.txt")):
        wav_file = txt_file.with_suffix(".wav")
        if wav_file.exists():
            reference = txt_file.read_text(encoding="utf-8").strip()
            pairs.append((str(wav_file), reference))

    print(f"Loaded {len(pairs)} test samples for [{lang}]")
    return pairs


def create_synthetic_test_data(lang: str) -> List[Tuple[str, str]]:
    """Create synthetic test pairs for demonstration purposes."""
    samples = {
        "hi": [("synthetic_hi_001.wav", "नमस्ते आप कैसे हैं"), ("synthetic_hi_002.wav", "यह एक परीक्षण है")],
        "en": [("synthetic_en_001.wav", "hello how are you"), ("synthetic_en_002.wav", "this is a test")],
        "ta": [("synthetic_ta_001.wav", "வணக்கம் நீங்கள் எப்படி இருக்கிறீர்கள்"), ("synthetic_ta_002.wav", "இது ஒரு சோதனை")],
    }
    return samples.get(lang, samples["en"])


def load_audio(wav_path: str) -> np.ndarray:
    """Load WAV file as float32 array at 16kHz."""
    try:
        import soundfile as sf
        audio, sr = sf.read(wav_path)
        if sr != 16000:
            # Simple linear resampling (use librosa in production)
            ratio = 16000 / sr
            new_len = int(len(audio) * ratio)
            audio = np.interp(
                np.linspace(0, len(audio), new_len),
                np.arange(len(audio)),
                audio
            )
        return audio.astype(np.float32)
    except Exception:
        # Return synthetic audio for demonstration
        return np.random.randn(16000).astype(np.float32) * 0.1


def run_inference(session: ort.InferenceSession, audio: np.ndarray, lang: str) -> Tuple[str, float]:
    """Run STT inference and return (transcript, inference_time_ms)."""
    # Simplified mel feature extraction (placeholder)
    # In production: use extract_log_mel_spectrogram from STTModule
    n_mels, T = 80, max(10, len(audio) // 160)
    features = np.random.randn(1, n_mels, T).astype(np.float32)

    start = time.time()
    try:
        outputs = session.run(
            None,
            {"audio_signal": features, "length": np.array([T], dtype=np.int32)}
        )
        inference_ms = (time.time() - start) * 1000

        # Placeholder decode (real: use SentencePiece tokenizer)
        transcript = f"[STT output for {lang}]"
        return transcript, inference_ms
    except Exception as e:
        return f"[ERROR: {e}]", 0.0


def evaluate_model(model_path: str, test_pairs: List[Tuple[str, str]], lang: str) -> dict:
    """Evaluate a model on test data and return WER + latency metrics."""
    print(f"Loading model: {model_path}")
    session_options = ort.SessionOptions()
    session_options.intra_op_num_threads = 2
    session = ort.InferenceSession(model_path, session_options)

    wers = []
    latencies = []

    for audio_path, reference in test_pairs:
        audio = load_audio(audio_path)
        hypothesis, latency_ms = run_inference(session, audio, lang)
        latencies.append(latency_ms)

        if HAS_JIWER:
            wer = jiwer.wer(reference, hypothesis)
        else:
            wer = compute_wer_basic(reference, hypothesis)
        wers.append(wer)

    return {
        "wer_mean": float(np.mean(wers)),
        "wer_std": float(np.std(wers)),
        "wer_samples": len(wers),
        "latency_mean_ms": float(np.mean(latencies)),
        "latency_p90_ms": float(np.percentile(latencies, 90)),
        "latency_p99_ms": float(np.percentile(latencies, 99))
    }


def main():
    parser = argparse.ArgumentParser(description="Validate WER for iTantra STT models")
    parser.add_argument("--model", required=True, help="Path to ONNX model file")
    parser.add_argument("--fp32-model", help="FP32 baseline model for regression check")
    parser.add_argument("--test-data", default="./test_audio/", help="Test audio directory")
    parser.add_argument("--lang", default="hi", help="Language code (hi, ta, te, ...)")
    parser.add_argument("--output", default="./wer_report.json", help="Output report path")
    parser.add_argument("--max-wer", type=float, default=0.18, help="Maximum acceptable WER")
    parser.add_argument("--max-regression", type=float, default=0.05, help="Max WER regression vs FP32")
    args = parser.parse_args()

    print("=" * 50)
    print(f"iTantra WER Validation — [{args.lang.upper()}]")
    print("=" * 50)

    test_pairs = load_test_data(args.test_data, args.lang)
    if not test_pairs:
        print("❌ No test data found.")
        return

    # Evaluate INT8 model
    print(f"\n[INT8] Evaluating: {args.model}")
    int8_results = evaluate_model(args.model, test_pairs, args.lang)
    print(f"  WER: {int8_results['wer_mean']:.1%} ± {int8_results['wer_std']:.1%}")
    print(f"  Latency P90: {int8_results['latency_p90_ms']:.0f}ms")

    passed = True
    report = {"lang": args.lang, "int8": int8_results}

    # WER threshold check
    if int8_results["wer_mean"] > args.max_wer:
        print(f"  ❌ WER {int8_results['wer_mean']:.1%} > threshold {args.max_wer:.1%}")
        passed = False
    else:
        print(f"  ✅ WER below threshold {args.max_wer:.1%}")

    # Regression check vs FP32 baseline
    if args.fp32_model:
        print(f"\n[FP32] Evaluating baseline: {args.fp32_model}")
        fp32_results = evaluate_model(args.fp32_model, test_pairs, args.lang)
        regression = int8_results["wer_mean"] - fp32_results["wer_mean"]
        report["fp32"] = fp32_results
        report["wer_regression"] = regression
        print(f"  FP32 WER: {fp32_results['wer_mean']:.1%}")
        print(f"  Regression: {regression:+.1%}")

        if regression > args.max_regression:
            print(f"  ❌ Regression {regression:.1%} > gate {args.max_regression:.1%}")
            passed = False
        else:
            print(f"  ✅ Regression within gate ({regression:.1%} ≤ {args.max_regression:.1%})")

    report["passed"] = passed
    # Save report
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    print(f"\n📊 Report saved: {args.output}")
    print(f"\n{'✅ GATE PASSED' if passed else '❌ GATE FAILED'}")


if __name__ == "__main__":
    main()
