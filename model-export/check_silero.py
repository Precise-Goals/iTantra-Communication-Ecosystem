"""Checks the app's Silero model with and without the v5 64-sample context (T62).

Usage:
    python check_silero.py <model.onnx> <audio_16k_mono.wav>

Prints max probability and fraction of frames > 0.5, with and without prepending the
previous window's last 64 samples. Run once on a speech sample and once on a silence/
non-speech sample: the "with context" run should read high on speech and low on
silence, while "without context" is expected to stay flat/near-zero on both (this is
exactly what VADModule.kt was missing before T62).
"""
import sys
import numpy as np
import onnxruntime as ort
import soundfile as sf

MODEL, WAV = sys.argv[1], sys.argv[2]
wav, sr = sf.read(WAV, dtype="float32")
assert sr == 16000 and wav.ndim == 1, "need 16 kHz mono"
sess = ort.InferenceSession(MODEL)


def run(with_context: bool):
    state = np.zeros((2, 1, 128), dtype=np.float32)
    ctx = np.zeros(64, dtype=np.float32)
    probs = []
    for i in range(0, len(wav) - 512, 512):
        w = wav[i:i + 512]
        x = np.concatenate([ctx, w]) if with_context else w
        out, state = sess.run(None, {
            "input": x[None, :].astype(np.float32),
            "state": state,
            "sr": np.array(16000, dtype=np.int64),
        })
        ctx = w[-64:]
        probs.append(float(out[0][0]))
    p = np.array(probs)
    return p.max(), (p > 0.5).mean()


for flag in (False, True):
    mx, frac = run(flag)
    print(f"context={flag}: max={mx:.3f} fraction>0.5={frac:.2f}")
