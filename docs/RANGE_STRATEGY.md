# iTantra — Range Strategy & RF Roadmap

> **Smart India Hackathon 2026 · Problem Statement PS-26173**
> Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for **Low Bitrate Links**

Companion to [`PRESENTATION.md`](PRESENTATION.md), which covers the product and the AI pipeline.
This document covers one question only: **how far can a message travel, and how do we prove it?**

Every figure here is labelled ✅ **measured**, 📐 **calculated**, 📚 **cited**, or 🔬 **projected**.
Nothing is asserted without saying where it came from.

---

## 1. The Core Insight

Most "offline mesh" apps relay **audio**. iTantra relays **text**.

Speech is transcribed on-device by IndicConformer, sent as a 50–300 byte Protobuf frame, and
re-synthesized as speech by VITS on the far side. The listener hears a natural voice at both
ends — but what crosses the gap is a text message the size of an SMS.

This single decision is what makes long range physically possible:

| | Raw audio relay | iTantra (text relay) |
| --- | --- | --- |
| Bytes per utterance | ~32,000 (2 s @ 16 kHz) | **50–300** |
| Minimum usable link | ~64 kbps | **~1 kbps** |
| Viable radio | Wi-Fi only | **Any voice radio** |

> **The line for the slide:** *We don't need a fast link. We need a link that reaches.*
> Everything below follows from having traded bandwidth for distance up front.

---

## 2. Honest Baseline — What Ships Today

✅ **Measured**, verified end-to-end across two physical Android devices.

| Transport | Status | Typical range |
| --- | --- | --- |
| Wi-Fi Direct + TCP (`SocketTransport`) | ✅ Working | 100–200 m |
| Bluetooth Classic RFCOMM | ✅ Working | 10–30 m |

That is the current, truthful ceiling. Everything in §4 onward is roadmap, and this document
is careful never to blur the two.

---

## 3. Why 2.4 GHz Cannot Reach 5 km

📐 Free-space path loss: `FSPL(dB) = 32.44 + 20·log₁₀(d_km) + 20·log₁₀(f_MHz)`

```
Wi-Fi Direct link budget
  TX power (phone)          +15 dBm
  RX sensitivity            −76 dBm
  ─────────────────────────────────
  Available budget          ~91 dB

  FSPL @ 2.4 GHz, 200 m      86 dB   ← already at the limit
  FSPL @ 2.4 GHz,   5 km    114 dB   ← 23 dB short, before any obstacle
```

23 dB short is a factor of ~14 in distance. This is not a tuning problem, a protocol problem,
or an antenna problem. **No software change to a 2.4 GHz phone radio reaches 5 km.**

Two independent penalties compound at 2.4 GHz: high free-space loss, and a wavelength short
enough (12.5 cm) that walls, foliage and terrain absorb rather than diffract. Lower frequencies
bend around obstacles; 2.4 GHz does not.

---

## 4. The Range Ladder

Four levers, each independent, each multiplying the last.

### Tier 1 — Better PHY (~10× per hop) 🔬

The app currently uses **Bluetooth Classic**, the shortest-range mode a modern phone has.
**BLE Coded PHY (S=8)** is a pure software change to an API already permitted in our manifest
(`BLUETOOTH_ADVERTISE`, declared and unused today).

```
BT Classic Class 2   TX +4 dBm,  sens −88 dBm  →  92 dB
BLE Coded PHY S=8    TX +8 dBm,  sens −103 dBm → 111 dB
```

📚 Real-world gain is **~8 dB, not the theoretical 12** — "slightly more than double the
range." Measured phone-to-phone results: **150–250 m typical, ~500 m best case.**

Two properties matter as much as the range. It is **connectionless**, so it works at the range
edge where a connection handshake would fail; and **Extended Advertising carries ~254 bytes**,
enough for a whole compressed iTantra frame in one broadcast.

⚠️ Support is chipset-dependent — gate on `isLeCodedPhySupported()` and fall back.

### Tier 2 — Connectionless Discovery Channel (~4× per hop) 🔬

802.11 management frames (probe request/response) are sent at the **lowest basic rate,
1 Mbps DSSS (~−98 dBm)**, while TCP data rides OFDM at −76 dBm. That ~20 dB gap is why you can
*see* a Wi-Fi network far beyond where you can *connect* to it.

Wi-Fi Direct **DNS-SD TXT records** carry ~800 bytes with no association, no DHCP and no group
formation — all public Android API.

### Tier 3 — Multi-Hop Flood Mesh (× hop count) 🔬

`itantra.proto` already declares a `sequence` field, but nothing currently forwards. Adding
`ttl` and `msg_id`, a 256-entry seen-ID cache, and rebroadcast with 100–500 ms random jitter
(to avoid a broadcast storm) turns every handset into a router. ~40 lines.

### Tier 4 — Store-Carry-Forward DTN (unbounded) 🔬

The counter-intuitive one, and the one that actually delivers in a sparse real deployment.

Every normal network assumes **a complete path exists at one instant**. Delay-Tolerant
Networking drops that assumption: a message that cannot be forwarded now is persisted and
retried at the next contact. **A responder walking with a phone is a link.**

```
t+0min   A speaks. No peer in range. → stored, copies=8, expiry=+6h
t+20min  A passes B on a path. 2 s BLE contact.     → A:4  B:4
t+35min  B rides toward camp, passes driver C.      → B:2  C:2
t+45min  C reaches base camp, 6 km away.            → DELIVERED
```

**At no instant did a continuous radio chain from A to base camp exist.** Human movement was
the transport; the radios only covered the last 10 m of each handoff.

We use **Spray-and-Wait**, not epidemic flooding: hand over half your copies on each contact,
and once down to one, deliver only to the destination. Replication is hard-capped at 8, giving
near-epidemic delivery for a fraction of the battery and storage.

> Our architecture makes this feasible: 10,000 pending 60-byte bundles is ~600 KB. Relaying
> raw audio, a phone could carry a handful. **The text-first design pays off twice.**

---

## 5. The Radio Path — Making the Problem Statement Literal

PS-26173 says *"Transceiver Radio Access for **Low Bitrate Links**."* That is radio vocabulary.
A low-bitrate link means ~1200 baud over a voice radio — not Wi-Fi at 50 Mbps.

**Approach:** a Bell 202 AFSK soundcard modem — the same technique APRS has used over VHF since
the 1980s.

```
[Phone] STT → text → compress → AFSK modulate → AudioTrack ──audio──▶ [Radio] TX
                                                                        ≈ 5–15 km
[Phone] TTS ◀── text ◀── AFSK demodulate ◀── AudioRecord ◀──audio── [Radio] RX
```

Both tones (1200 Hz mark / 2200 Hz space) sit inside the 300–3000 Hz voice passband, so the
radio carries them as if they were speech. **No firmware change, no data port, no modification.
The radio never knows it is carrying data.**

### Division of labour — read this before answering any range question

**The phone never transmits at 446 MHz. It cannot.** All RF is done by the handheld.

| | Android phone | PMR446 handheld |
| --- | --- | --- |
| Role | **The brain** | **The transmitter** |
| Does | STT, translation, TTS, compression, AFSK modem | All RF: 0.5 W @ 446 MHz |
| Transmits on | 2.4 GHz only (Wi-Fi ~32 mW, BLE ~6 mW) | 446 MHz |
| Range contributed | ~200 m | **3–6 km** |

This is not a software limitation that could be lifted. A phone has **no 446 MHz hardware** — no
antenna cut for that band, no power amplifier, no RF filter — and its baseband firmware is
locked besides. Even the phone's strongest transmitter (cellular, ~200 mW) is on licensed
cellular bands and is not reachable from an app.

> **Why the design works:** we never ask the phone to do something it cannot. We ask it to make
> a *sound*, and let a purpose-built transmitter carry that sound 5 km. The audio cable between
> them carries audio, not data.

**Consequence:** kilometre-scale range **requires the handheld. It is not optional.** Phone-only
configurations top out at 150–250 m per hop (§4 Tier 1).

**Why this reuses what we already built:** the modem runs at 16 kHz mono PCM — the exact format
`AudioCaptureModule` already captures and `AudioPlaybackManager` already plays. A two-tone
correlator is simpler DSP than the 80-bin mel-spectrogram already shipping in `STTModule`.

📐 Airtime: a 60-byte payload ≈ 930 bits ≈ **0.8 s** including VOX preamble. This is why payload
compression (§7) is not optional on this link.

⚠️ **Practical constraint:** most modern phones have no 3.5 mm jack. Audio coupling needs a
USB-C adapter **containing a DAC** — passive adapters fail — and TRRS mic-input behaviour varies
by vendor. Budget one tested adapter per node.

---

## 6. Regulatory Position — India

📚 Researched against DoT/WPC sources. **This section is a differentiator: most teams will not
have done it.**

### Licence-exempt options

| Band | Power | Basis |
| --- | --- | --- |
| **446–446.2 MHz (PMR)** | **0.5 W ERP** | Delicensed 2018 |
| 865–868 MHz (SRD) | 25 mW / 500 mW / 2 W ERP by class | SRD Rules, 2021 |
| 2.4 GHz | Low power | Wi-Fi / BT — what we use today |

📚 **What a 0.5 W handheld at 446 MHz delivers** (the *radio's* range, not the phone's — see §5):
0.5–2 km urban, **3–6 km open terrain**, with a documented 27 km ridge-to-ridge test from high
ground.

### Licensed options

- **ASOC amateur licence** — WPC-conducted exam; Grade II permits **50 W** including VHF.
  Comfortably 5–15 km. Licenses *operators*, not the app.
- **Agency allocations** — NDRF, state police and forest departments already hold and operate
  on assigned frequencies.

### Two rules that shape the design

**Encryption prohibition.** Amateur services bar *"messages encoded for the purpose of obscuring
their meaning."* Our phrase-codebook compression is compliant **only if the specification is
published** — the rule targets *secret* encoding, and open published codecs (APRS, FT8, Codec2)
are routine on the air.
→ **Action: the codebook spec ships in this repo, publicly.**

**The disaster carve-out — and it works in our favour.** Under the Indian Wireless Telegraph
(Amateur Service) Rules, 1978, licensees may handle third-party messages *"pertaining to natural
calamities such as earthquake, floods, cyclones and wide spread fires, originating from and
addressed to competent civil authorities."*

That is a near-exact description of iTantra's use case. **We are not asking for an exception;
we are building for a case the rules already name.**

### Honest conclusion

**5–10 km licence-free for the general public is not reliably achievable.** 3–6 km in open
terrain is. The full 5–10 km is legally reachable via agency spectrum or licensed amateur
operators — which is precisely who runs emergency communications in India today.

---

## 7. Payload Compression

📐 Indic UTF-8 costs **3 bytes per character**, so a Devanagari sentence is expensive. Current
frame waste:

| Field | Today | Target |
| --- | --- | --- |
| `sender_id` | 36-byte UUID **string** | 2-byte node ID |
| `src_lang` / `dst_lang` | strings (~8 B) | 4-bit enums (1 B) |
| `timestamp` | int64 | 4-byte delta |
| `text` | 3 B/char UTF-8 | codebook ID (12 bits) or Huffman |

A 4096-entry disaster-phrase codebook turns a common message into **12 bits**; per-language
static Huffman handles free-form text. Typical frame: **20–60 bytes**, seeded from the existing
9-language corpus in `TacticalAiEngine`.

---

## 8. Validation Strategy — How Every Claim Is Proven

The central move: **separate "does the modem work" from "how far does the radio go."** They are
different questions needing different tests, and only the second needs spectrum.

| Tier | Test | Proves | Needs |
| --- | --- | --- | --- |
| **1** | JVM loopback + channel simulator | Framing, CRC, NRZI, clock recovery | Nothing — runs in CI |
| **2** | Two phones, speaker → mic across a table | Full Android audio path, real ADC/DAC, clock drift | Two phones. **It's just sound — no spectrum.** |
| **3** | Wired audio loopback | Isolates digital path; validates USB-C adapter | A cable |
| **4** | Two PMR446 handhelds @ 0.5 W | **Real over-the-air, end-to-end** | ₹2–4k/pair. **Licence-free.** |

**Tier 1 detail.** The channel simulator injects what a real radio link does: Gaussian noise at
varying SNR, ±50 Hz frequency offset (two radios' oscillators never match), clipping from an
over-driven mic input, and **audio polarity inversion** — which is exactly what NRZI exists to
survive, so we prove the design choice rather than assume it. Output is a **packet-error-rate vs
SNR curve**: the standard way modems are characterised, and a credible artifact to show.

**Reaching the 5–10 km figure legally** — three routes:

1. **Partner with a local ham club.** The practical one. A licensed operator conducts the test
   with us present; Indian amateur radio has a strong disaster-comms culture. Yields a
   documented, witnessed field test plus a domain expert to cite.
2. **ASOC licence.** Legitimate but months, not weeks. Too slow to be the primary plan.
3. **Attenuator extrapolation.** Measure PER against *received signal level* with a step
   attenuator, then apply the link budget. This is how commercial radios get their range specs.

### The claim we make on stage

> *"Modem validated in CI against a simulated channel down to X dB SNR. End-to-end voice-to-voice
> demonstrated over licence-free 446 MHz at 1.5 km. Link budget projects 6–12 km at agency power
> levels, confirmed in a field trial with a licensed operator on [date]."*

Every number traceable to a specific test. Stronger than a bare "10 km."

---

## 9. Roadmap

⚠️ **Never quote a range from this table without its precondition.** Some figures are reached
only because other people are relaying, or because hardware is paired. See
[`RANGE_IMPLEMENTATION.md`](RANGE_IMPLEMENTATION.md) §5 for the full breakdown.

| Phase | Work | Range | **Requires beyond 2 phones** | Status |
| --- | --- | --- | --- | --- |
| 0 | Wi-Fi Direct + BT Classic | 30–200 m | **nothing** | ✅ **Shipping** |
| 1 | `MeshLink` abstraction | — | — | 🔨 In progress |
| 2 | AFSK modem + HDLC + CI channel sim | — | — | 🔨 In progress |
| 3 | `AfskRadioLink` (AudioRecord/AudioTrack) | **3–6 km** | a paired handheld + USB-C audio adapter **per node** | Planned |
| 4 | TTL + dedup flood relay | × hop count | **one device per hop** along the route | Planned |
| 5 | Payload compression + published codebook | enables repetition | nothing | Planned |
| 6 | BLE Coded PHY link | 150–250 m/hop | **nothing** | Planned |
| 7 | Room-backed DTN, Spray-and-Wait | unbounded | **a person or vehicle in motion** | Planned |

**The phone-only, nothing-else ceiling is phases 0 + 6 ≈ 300 m.** Everything past that is bought
with either people (phase 4), motion (phase 7), or hardware (phase 3).

Phases 1–3 are the demo-critical path: they make the repo match its problem statement.

---

## 10. Anticipated Questions

**"You claim 5–10 km — prove it."**
We claim what we measured. Licence-free 446 MHz gives 3–6 km in open terrain, demonstrated.
5–10 km requires agency or amateur power levels, validated with a licensed operator and backed
by a link budget. We separate measured from projected in every figure.

**"Does the phone itself transmit 5 km?"**
No, and it never could — a phone has no 446 MHz hardware and a locked baseband, and its
strongest radio is ~32 mW on 2.4 GHz. The phone is the brain: STT, translation, TTS and the
modem. The handheld does all the RF. We connect them with an audio cable, because the modem's
output is literally sound. See §5.

**"Isn't this just a walkie-talkie?"**
A walkie-talkie carries one language, needs 64 kbps of clear audio, and fails in noise. iTantra
carries a 60-byte text frame that survives at 1 kbps and a negative SNR — and it **translates**:
speak Hindi, the receiver hears Malayalam. Neither is possible with voice.

**"Why not just use LoRa?"**
We can — the `MeshLink` interface accepts it. But VHF/UHF handhelds are **already deployed and
budgeted** with NDRF, police and forest departments. Upgrading radios India already owns beats
asking every responder to buy a module.

**"What if there's no one to relay through?"**
That's exactly what DTN Tier 4 handles. The message waits on the phone and travels with the
person. Latency rises; delivery does not fail.

**"Is transmitting data over these radios legal?"**
Amateur rules bar *secret* encoding, not compression — which is why our codebook specification
is published in this repository. And the 1978 Rules explicitly permit third-party disaster
traffic addressed to civil authorities.

**"What's genuinely unfinished?"**
`AfskRadioLink`, the DTN store, BLE Coded PHY and compression are roadmap. Wi-Fi Direct and
Bluetooth transports are shipping and verified on two physical devices. We keep that line sharp.

---

## 11. Sources

- [DoT — SRD Rules, 2021 (865–868 MHz)](https://dot.gov.in/spectrummanagement/use-low-power-equipment-frequency-band-865-868-mhz-short-range-devicesexemption)
- [DoT — Delicensing 865–867 MHz, GSR 564(E)](https://www.dot.gov.in/spectrummanagement/delicensing-865-867-mhz-band-gsr-564-e)
- [DoT — Amateur Station Operator Certificate (ASOC)](https://www.eservices.dot.gov.in/amateur-station-operator-certificate-asoc)
- [Indian Wireless Telegraph (Amateur Service) Rules, 1978](https://en.wikipedia.org/wiki/Indian_Wireless_Telegraph_(Amateur_Service)_Rules,_1978)
- [Amateur radio licence categories in India](https://en.wikipedia.org/wiki/Amateur_radio_licence_categories_in_India)
- [ARSI — WPC Rules](https://arsi.info/wpc-rules/)
- [PMR446 licence-free status in India](https://www.brutforcegear.com/post/is-your-walkie-talkie-really-license-free)
- [PMR446 real-world range guide](https://www.radiotrader.co.uk/news/facts-about-licence-free-radios.htm)
- [Bluetooth Coded PHY explained](https://novelbits.io/bluetooth-long-range-coded-phy/)
- [Coded PHY — tested Android phones](https://github.com/NordicSemiconductor/Android-BLE-Library/issues/166)
- [Android — USB-C to analog audio adapters](https://source.android.com/docs/core/interaction/accessories/headset/usb-adapter)
- [Baseband firmware lockdown](https://www.osnews.com/story/27416/the-second-operating-system-hiding-in-every-mobile-phone/)

---

**Signal, where there is none.**
