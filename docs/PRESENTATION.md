# iTantra — Presentation Briefing

> **Smart India Hackathon 2026 · Problem Statement PS‑26173**
> Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links

This is a presentation-ready briefing on iTantra as it **actually runs today** (branch `fix/model-pipeline-integrity`, v2.0.0) — not the original pitch. Each section below maps to one slide/talking point. For full technical detail see [`README.md`](../README.md).

---

## 1. The One-Liner

An offline neural transceiver for zero-connectivity terrain: speech is understood **on-device**, compressed to **~200 bytes of text**, carried across an ad-hoc **Wi-Fi Direct / Bluetooth mesh**, and spoken back in the listener's own language — no towers, no cloud, no internet after setup.

**Headline numbers:**

| | |
| --- | --- |
| STT / TTS languages live | **9 / 5** |
| Mesh transport port | **TCP 8765** |
| Devices verified end-to-end | **2 (real hardware)** |
| Raw audio ever transmitted | **0 bytes** — text only |

---

## 2. The Problem

Cyclones, earthquakes, and forward tactical positions share one trait: cellular infrastructure is either destroyed or was never there. What remains is a scatter of field radios and phones that can see each other over a few hundred metres — and a population that does not share one language.

A first responder from one state and a survivor speaking a different scheduled language cannot understand each other's radio traffic. Streaming audio over the thin ad-hoc link that remains would eat the little bandwidth available in seconds.

**Field conditions:**
- No cellular backbone to route through
- 10+ scheduled languages in a single response zone
- Ad-hoc links too thin for streamed audio
- No connectivity for cloud speech APIs
- Non-literate populations excluded by text-only radios

---

## 3. The Signal Path

```
SENDER                                                          RECEIVER
Mic (16kHz PCM)                                                  Speaker
   │                                                                 ▲
   ▼                                                                 │
VAD (energy-threshold fallback, 800ms silence flush)          AudioTrack
   │                                                                 ▲
   ▼                                                                 │
STT — IndicConformer, sherpa-onnx, INT8 (per-language)     TTS — espeak-ng
   │                                                        phonemized VITS
   ▼                                                        (5 languages)
Protobuf encode — TransceiverMessage (~50–300 bytes)                ▲
   │                                                                 │
   ▼                                                                 │
   └───── Mesh link: Wi-Fi Direct :8765 / Bluetooth RFCOMM ─────────┘
                              │
                    [type == ALERT] → STREAM_ALARM, max volume,
                                       DND-bypass attempt
```

- **Sender:** mic → energy-based VAD flushes on 800ms silence → per-language IndicConformer STT (sherpa-onnx, INT8) → encoded as a `TransceiverMessage` protobuf → sent over whichever transport is up.
- **Receiver:** frame decoded → espeak-ng-phonemized VITS voice synthesizes the text → played back over `AudioTrack`.
- An `ALERT`-typed message instead forces alarm-stream volume and a Do-Not-Disturb bypass attempt.

---

## 4. Field Report — What's Actually Built

This project has been debugged against real phones over real Wi-Fi Direct and Bluetooth links. This is the honest state of every subsystem, not the aspirational one.

| Capability | Status |
| --- | --- |
| Wi-Fi Direct + Bluetooth mesh transport (discovery, group formation, TCP :8765, reconnect backoff, RFCOMM fallback) | ✅ Verified |
| Push-to-talk capture → STT → transmit, bound to a real foreground service | ✅ Verified |
| On-device speech-to-text — AI4Bharat IndicConformer, 9 of 10 scheduled languages (no Odia source found) | ✅ Verified |
| On-device text-to-speech — real espeak-ng phonemization, only 5 of 10 languages have a free offline voice | ⚠️ Partial |
| Voice activity detection — bundled Silero neural model measured non-functional on real audio; energy-threshold fallback active instead | ⚠️ Fallback |
| On-device AI Tactical Assistant — real Phi-3 (llama.cpp) when downloaded + supported; honest keyword fallback otherwise, UI discloses which | ✅ Verified |
| Model download / integrity pipeline — resumable OkHttp downloads, SHA-256 verification, tar.bz2 extraction | ✅ Verified |
| Peer authorization whitelist — Room-persisted, survives app restarts | ✅ Verified |
| Emergency / SOS broadcast screen | ❌ Not built — dropped from the original design |
| Per-peer session screen (`PeerSessionScreen`) | ❌ UI mock only — not yet wired to the real transceiver pipeline |

---

## 5. Under the Hood

**Speech & AI**
IndicConformer STT (sherpa-onnx) · ONNX Runtime 1.18.0 · sherpa-onnx 1.13.7 · Piper / Coqui / Mimic3 voices · Silero VAD v4 (loaded, disabled) · Phi-3-mini-4k GGUF q4 · llama.cpp-kotlin 0.4.0

**Mesh & Wire**
WifiP2pManager · Bluetooth RFCOMM · Protobuf javalite 3.25.3 · TCP :8765 · OkHttp 4.12.0 · Commons Compress 1.26.2

**Platform**
Kotlin 2.3.10 · Jetpack Compose (BOM 08.2024) · Room 2.8.4 · DataStore 1.1.1 · minSdk 26 / target 35 · arm64-v8a, armeabi-v7a, x86_64

**Persistence & Data**
Room peer registry + auth whitelist · DataStore device profile · kotlinx.serialization 1.7.1 · SHA-256 model integrity store

---

## 6. On Screen

Five screens, monochrome, built for a glance in low light: **Home · Radar · Radio (Transceiver) · Downloads · Assistant.**

- **Home** — node identity, build ID, and a live spec grid; the closest thing the app has to a settings surface.
- **Downloads** — 23 model packs tracked individually, per-file size, SHA-256 verification, resumable progress.
- **Transceiver** — the real operational screen: host/search toggles, live peer list, hold-to-talk PTT wired to the foreground service.
- **Radar** — RSSI-based mesh visualization of nearby nodes.
- **AI Assistant** — discloses honestly when Phi-3 isn't loaded, and answers anyway from a real fallback, not a stub.

(See `README.md` → Screens, and the repo's `screen*.png` captures, for visuals.)

---

## 7. Language Coverage

Nine languages transcribe. Five speak back. Disclosed here the same way it's disclosed inside the app itself — the gap is real, and substituting a wrong-language voice was explicitly ruled out during development.

| Language | Script | STT | TTS |
| --- | --- | --- | --- |
| Hindi | हिन्दी | ✅ | ✅ |
| Gujarati | ગુજરાતી | ✅ | ✅ (lower quality tier) |
| Marathi | मराठी | ✅ | ❌ |
| Kannada | ಕನ್ನಡ | ✅ | ❌ |
| Malayalam | മലയാളം | ✅ | ✅ |
| Tamil | தமிழ் | ✅ | ❌ |
| Telugu | తెలుగు | ✅ | ❌ |
| Odia | ଓଡ଼ିଆ | ❌ | ❌ |
| Bengali | বাংলা | ✅ | ✅ |
| English | — | ✅ | ✅ |

---

## 8. Engineering Log

A sample of fixes pulled straight from commit history — the kind of thing that only surfaces once two physical phones try to actually talk to each other:

- Made IndicConformer STT genuinely transcribe, instead of returning fabricated placeholder text.
- Investigated the bundled neural VAD on real audio, found it non-functional, and honestly disabled it in favor of an energy-threshold fallback — rather than shipping a silently-broken model.
- Fixed model downloads silently stalling under OkHttp's default 5-connections-per-host cap once 17 packs queued in parallel.
- Wired the real PTT walkie-talkie transport into the running app — the foreground service existed but was never started or bound.
- Bounded LLM generation to a stop sequence and a 250-token cap after replies were observed running past 200 tokens with no natural stop.
- Verified a real two-device walkie-talkie session end-to-end; fixed Bluetooth server start and playback routing/volume along the way.

---

## 9. Compliance

**Open-source end to end:**

| Component | License |
| --- | --- |
| AI4Bharat IndicConformer (sherpa-onnx export) | Apache 2.0 |
| sherpa-onnx / Piper / Coqui / Mimic3 voices | Apache 2.0 / MIT |
| Silero VAD | MIT |
| ONNX Runtime Mobile | MIT |
| llama.cpp / Phi-3-mini-4k-instruct | MIT |
| Protocol Buffers (javalite) | BSD-3-Clause |
| Jetpack Compose / Room / DataStore | Apache 2.0 |
| OkHttp / Commons Compress | Apache 2.0 |

**Internet, used once:** the manifest declares `INTERNET` for exactly one purpose — the initial model download from Hugging Face and GitHub release CDNs. Every P2P message, every VAD/STT/TTS inference, and every AI Assistant reply afterward runs with zero network calls, on-device, indefinitely.

---

## 10. Sign Off

**Signal, where there is none.**

| Domain | Developer | Responsibility |
| --- | --- | --- |
| Engine | Gaurav | Audio/VAD/STT/TTS inference, mesh networking, model download pipeline, foreground service |
| Shell | Sarthak | Jetpack Compose UI, navigation, ViewModel state management |

_iTantra v2.0.0 · Smart India Hackathon 2026 · Problem Statement #26173 · Apache 2.0_
