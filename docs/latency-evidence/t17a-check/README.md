# T17a Check: int8 vs FP32 Piper TTS Speed on Phone

**Date:** 2026-09-26  
**Task:** T17a-CHECK (P1 from [`docs/TTS_QUANT_PROMPTS.md`](../../TTS_QUANT_PROMPTS.md))  
**Device:** Xiaomi POCO X6 5G (`23122PCD1I`, Snapdragon 7s Gen 2, 8 GB RAM, Android 16)  
**Package:** `com.itantra.debug`

---

## 1. On-Device Model Sizes

Verified via `adb shell run-as com.itantra.debug ls -la files/models/tts/hi`:

| Variant | Commit / Source | `.onnx` File Size (Bytes) | Size (MB) |
| :--- | :--- | :--- | :--- |
| **Piper INT8 (Run A)** | `eval/t17a-phone-check` (`origin/main`) | **18,575,696 bytes** | **18.58 MB** |
| **Piper FP32 (Run B)** | `9d6c724` (prior to G12 merge) | **63,145,178 bytes** | **63.15 MB** |

---

## 2. Benchmark Protocol & Test Utterances

10 identical Hindi emergency and operational sentences from [`docs/evaluation/tts-listening/sentences/hi.txt`](../evaluation/tts-listening/sentences/hi.txt) were evaluated on the physical device. A single warmup sentence was synthesized and played before recording to ensure that model caching was hot and cold-start latency was excluded:

1. `"चिकित्सा आपातकाल! तत्काल सहायता की आवश्यकता है।"`
2. `"तुरंत सभी लोग सुरक्षित स्थान पर जाएं।"`
3. `"इमारत में आग लगी है, तुरंत बाहर निकलें।"`
4. `"भोजन और पीने के पानी की आपूर्ति बेस कैंप पहुंच गई है।"`
5. `"क्या आप मेरी आवाज साफ सुन सकते हैं?"`
6. `"मुख्य सड़क बंद है, कृपया वैकल्पिक मार्ग लें।"`
7. `"बचाव दल रास्ते में है, कृपया शांत रहें।"`
8. `"सभी जवान अपनी वर्तमान स्थिति की रिपोर्ट करें।"`
9. `"रेडियो की बैटरी कम है, बैकअप चैनल चालू करें।"`
10. `"टीम अल्फा सुरक्षित रूप से चेकपॉइंट पर वापस आ गई है।"`

Raw per-utterance measurements are committed alongside this document:
- [`int8_telemetry.csv`](int8_telemetry.csv)
- [`fp32_telemetry.csv`](fp32_telemetry.csv)

---

## 3. Results (Medians over n=10 Sentences)

| Metric | Piper FP32 (Run B) | Piper INT8 (Run A) | Ratio (INT8 / FP32) |
| :--- | :---: | :---: | :---: |
| **TTS Synthesis Time (`tts_synth_ms`)** | **863.00 ms** | **1772.75 ms** | **2.05× slower** |
| **Synthesized Audio Duration (`tts_audio_ms`)** | **2905.00 ms** | **2939.50 ms** | 1.01× |
| **Text In → First Audio Played (`tts_ms`)** | **941.55 ms** | **1867.70 ms** | **1.98× slower** |
| **Real Time Factor (`rtf` = synth / audio)** | **0.2970** | **0.5910** | **1.99× (~2.0× slower)** |

---

## 4. Python Computation Snippet

```python
import pandas as pd

def compute_medians(path):
    df = pd.read_csv(path)
    df = df[df['tts_ms'] > 0].copy()
    df['rtf'] = df['tts_synth_ms'] / df['tts_audio_ms']
    return {
        'count': len(df),
        'synth_ms': df['tts_synth_ms'].median(),
        'audio_ms': df['tts_audio_ms'].median(),
        'tts_ms': df['tts_ms'].median(),
        'rtf': df['rtf'].median()
    }

int8_res = compute_medians("int8_telemetry.csv")
fp32_res = compute_medians("fp32_telemetry.csv")
ratio = int8_res['rtf'] / fp32_res['rtf']
print(f"INT8 median RTF: {int8_res['rtf']:.4f}")
print(f"FP32 median RTF: {fp32_res['rtf']:.4f}")
print(f"RTF Ratio: {ratio:.2f}x")
```

---

## 5. Verdict

**T17a SLOWS TTS — revert in favour of T80**

On physical hardware, the sherpa-onnx dynamic INT8 Piper voice (`quantize_dynamic`) doubles the synthesis time (863 ms → 1773 ms, 2.05×) and increases text-to-first-audio latency from 942 ms to 1868 ms. This confirms the CPU slowdown findings in [`docs/TTS_QUANT_PLAN.md`](../TTS_QUANT_PLAN.md) §2.2 and justifies using weight-only INT8 (T80), which preserves FP32 inference speed while achieving the full size reduction.
