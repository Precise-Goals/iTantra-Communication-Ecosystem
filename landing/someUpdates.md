# iTantra — Range Implementation Gap Analysis

> Engineering companion to [`RANGE_STRATEGY.md`](RANGE_STRATEGY.md) (presentation-facing) and
> [`PRESENTATION.md`](PRESENTATION.md) (product-facing).
>
> **This document answers three questions:** what range capability exists in the code today,
> what does not, and exactly how far each proposed addition moves the number.

## How this document was verified

Every "present"/"absent" claim below was checked against the source tree, not recalled.
The audit commands are recorded so you can re-run them after any change:

```bash
grep -rln "BluetoothLeAdvertiser\|startAdvertisingSet\|PHY_LE_CODED" app/src/main/java
grep -rln "addLocalService\|DnsSdService\|discoverServices"          app/src/main/java
grep -rln "WifiAwareManager"                                          app/src/main/java
grep -rln "ttl\|relay\|forward\|dedup"                                app/src/main/java
grep -rn  "groupOwnerIntent"                                          app/src/main/java
grep -rln "SatelliteManager\|TRANSPORT_SATELLITE"                     app/src/main
grep -rln "Deflater\|GZIP\|Huffman\|codebook"                         app/src/main/java
```

⚠️ Two greps return false positives that are **not** relay logic — a doc comment in
`AudioCaptureModule.kt:23` ("forwarded to STTModule") and a UI string in `HomeScreen.kt:286`
("thermal throttling"). Confirmed by inspection.

Evidence tags used throughout: ✅ **measured** · 📐 **calculated** · 📚 **cited** ·
🔬 **projected**

---

## 1. What Is Present Today

### On `main` — shipping and device-verified

| Capability | File | Range |
| --- | --- | --- |
| Wi-Fi Direct discovery + connect | `core/network/WifiDirectManager.kt` | ✅ 100–200 m |
| TCP transport, length-prefixed frames | `core/network/SocketTransport.kt` (:8765) | — |
| Bluetooth Classic RFCOMM | `core/network/BluetoothRFCOMMManager.kt` | ✅ 10–30 m |
| Discovery coordinator for radar UI | `core/network/MeshHardwareManager.kt` | — |
| Protobuf wire format | `core/proto/ProtobufSerializer.kt` | 50–300 B/frame |
| Peer registry + authorisation | `data/PeerRegistryRepository.kt`, Room | — |
| Ping/ACK RTT measurement | `SocketTransport.startPingLoop()` | — |

**Verified end-to-end across two physical Android devices.** This is the honest baseline:
**10–200 m, single hop, no relay.**

### On branch `worktree-afsk-radio-link` — compiles, not yet tested

| Capability | File | Status |
| --- | --- | --- |
| Transport abstraction | `domain/contracts/MeshLink.kt` | Compiles; nothing implements it yet |
| HDLC framing, bit stuffing, NRZI, CRC-16 | `core/radio/HdlcFramer.kt` | Compiles; **no unit tests** |
| Bell 202 AFSK modem (mod + demod) | `core/radio/AfskModem.kt` | Compiles; **unverified** |

⚠️ `./gradlew :app:compileDebugKotlin` passes, which proves it is syntactically valid Kotlin and
nothing more. **The modem has never moved a single byte.** Until the loopback test in §4 exists,
treat this as unproven.

---

## 2. What Is Absent

All verified absent by the greps in §0.

| Missing capability | Consequence | Range cost |
| --- | --- | --- |
| **BLE / any Bluetooth LE code** | Stuck on BT Classic, the shortest-range phone radio | ~10× per hop |
| **Wi-Fi Direct service discovery** | No connectionless long-range channel | ~4× per hop |
| **Wi-Fi Aware (NAN)** | Slower mesh formation, GO election overhead | topology |
| **TTL / hop / dedup / relay logic** | Every node drops frames not addressed to it — **no multi-hop at all** | × hop count |
| **`groupOwnerIntent` pinning** | Android picks the GO arbitrarily, possibly the worst-placed node | marginal |
| **Payload compression** | 36-byte UUID *string* per frame; Indic text at 3 B/char | blocks repetition |
| **Retransmission / repetition** | One attempt per frame; marginal links simply fail | ~1.4× usable |
| **DTN store-carry-forward** | Undeliverable message is lost, not queued | unbounded |
| **Satellite capability handling** | Cannot use D2C when it reaches India | global |

Note `AndroidManifest.xml:20` already declares **`BLUETOOTH_ADVERTISE`** and never uses it —
the permission for BLE advertising is in place, the code is not.

`itantra.proto` declares `sequence = 8` (fields 1–8 used) but **nothing reads it for dedup**, and
there is no `ttl` or `msg_id` field.

---

## 3. What To Add, And What Each Buys

Ordered by return on effort. "Effort" is rough implementation size, excluding tests.

### A1 — BLE Coded PHY link ⭐ highest value

**Add:** `core/network/BleCodedPhyLink.kt` implementing `MeshLink`.

```kotlin
AdvertisingSetParameters.Builder()
    .setLegacyMode(false)                          // unlock extended advertising (254 B)
    .setPrimaryPhy(BluetoothDevice.PHY_LE_CODED)   // S=8 long range
    .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)
    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
    .build()
```

📐 Link budget: BT Classic `+4 − (−88)` = 92 dB → BLE Coded `+8 − (−103)` = 111 dB.
📚 Real-world gain is **~8 dB, not the theoretical 12**.

| | |
| --- | --- |
| **Range: 10–30 m → 150–250 m** (500 m best case 📚) | **~10×** |
| Effort | ~250 lines |
| Depends on | `MeshLink` (done) |
| Risk | Chipset-dependent — **must** gate on `isLeCodedPhySupported()` and fall back |

Connectionless, so it works at the range edge where a handshake would fail. Extended
advertising carries ~254 B — a whole compressed frame in one broadcast.

### A2 — Flood relay (TTL + dedup) ⭐ highest value

**Add:** two proto fields and a forwarding rule.

```proto
uint32 ttl    = 9;   // decrement per hop, drop at 0 (cap 5)
uint32 msg_id = 10;  // (sender_id << 16) | sequence
```

Each node keeps a 256-entry LRU of seen `msg_id`s and rebroadcasts anything new at `ttl-1`
after **100–500 ms of random jitter** — without jitter every node retransmits simultaneously
and the collision storm destroys the mesh.

| | |
| --- | --- |
| **Range: × hop count** — 250 m × 10 hops = **2.5 km** 🔬 | **×10** |
| Effort | ~40 lines + proto change |
| Depends on | nothing (works on existing transports too) |
| Risk | Low. Requires node density: one device per hop distance |

### A3 — Payload compression

**Add:** `core/proto/FrameCodec.kt`.

| Field | Today | Target | Saving |
| --- | --- | --- | --- |
| `sender_id` | 36-byte UUID **string** | 2-byte node ID | 34 B |
| `src_lang`/`dst_lang` | strings | 4-bit enums | ~7 B |
| `timestamp` | int64 | 4-byte delta | 4 B |
| `text` | UTF-8, **3 B/char** for Indic | codebook ID (12 bits) or Huffman | 4–6× |

4096-entry disaster-phrase codebook, seeded from the 9-language corpus already in
`TacticalAiEngine`. Free-form text falls back to per-language static Huffman.

| | |
| --- | --- |
| **Range: no direct gain — but *enables* A4** | prerequisite |
| **Frame: 50–300 B → 20–60 B** | 5× |
| Effort | ~400 lines + codebook generation |
| Risk | ⚠️ **The codebook spec must be published** — amateur rules bar encoding that obscures meaning. Open codecs (APRS, FT8) are fine; secret ones are not |

### A4 — Repetition diversity

At the range edge a link is not dead, it is *probabilistic*. Sending the same frame **10 times
over 30 s** gives 10 independent fades.

📐 At 10% per-attempt success: `1 − 0.9¹⁰` = **65% delivery**. You are spending airtime to buy
link margin — the trade a 60-byte payload makes cheaply.

| | |
| --- | --- |
| **Range: ~1.4× usable distance** 🔬 (turns marginal links into working ones) | **×1.4** |
| Effort | ~80 lines |
| Depends on | A3 (only cheap once frames are small) |
| Risk | Battery. Cap repeats for `SPEECH`, allow more for `ALERT` |

### A5 — DTN store-carry-forward (Spray-and-Wait) ⭐ the unbounded one

**Add:** Room `Bundle` entity + contact-exchange handler + expiry sweeper.

```kotlin
@Entity data class Bundle(
    @PrimaryKey val msgId: Long,
    val payload: ByteArray,
    val expiryEpochMs: Long,     // drop after N hours
    val copiesRemaining: Int     // start 8; halve on handoff; deliver direct at 1
)
```

Spray-and-Wait caps replication at 8 copies, giving near-epidemic delivery for a fraction of
the battery and storage cost.

| | |
| --- | --- |
| **Range: unbounded** — 5–10 km via one walking carrier (~60 min) or a vehicle (~15 min) | **∞** |
| Effort | ~350 lines |
| Depends on | A2 (dedup), Room (already present) |
| Risk | Latency is minutes-to-hours. Needs a new UI state: `PENDING → IN FLIGHT (n) → DELIVERED` |

📐 Storage is a non-issue *because* frames are text: 10,000 bundles × 60 B ≈ **600 KB**.
Relaying audio, a phone could carry a handful.

### A6 — Wi-Fi Direct service discovery tier

**Add:** DNS-SD TXT records as a data channel — no association, no DHCP, no group formation.

```kotlin
WifiP2pDnsSdServiceInfo.newInstance("itantra", "_itantra._tcp", mapOf("m" to b64Frame))
```

📐 Probe frames ride the **1 Mbps DSSS basic rate (~−98 dBm)**; TCP data rides OFDM (~−76 dBm).
That ~20 dB gap is why you can *see* a Wi-Fi network far past where you can *connect*.

| | |
| --- | --- |
| **Range: 400 m – 1 km per hop** 🔬 **(unmeasured — highest-uncertainty figure here)** | **~4×** |
| Effort | ~200 lines |
| Risk | TXT capacity ~800 B; needs field measurement before being claimed |

### A7 — Satellite readiness

**Add:** manifest flag + constrained-network callback. Route the A5 bundle store through it.

```xml
<meta-data android:name="android.telephony.PROPERTY_SATELLITE_DATA_OPTIMIZED"
           android:value="com.itantra" />
```

📚 Android 16 permits **third-party apps to use satellite data**, not just SMS/RCS, with no
special permission. Google's guidance — *"burst operations, idle most of the time, low
bandwidth"* — describes iTantra exactly.

| | |
| --- | --- |
| **Range: global** | **∞** |
| Effort | **~30 lines** |
| Risk | 📚 Starlink D2C reaches **India ~2027**. Cannot be demoed in 2026 — build it as readiness, claim it as roadmap |

### A8 — Cheap Wi-Fi wins

`groupOwnerIntent = 15` on the best-placed node; force 2.4 GHz (5 GHz roughly halves range);
prompt the user to hold the phone high and away from the body (a body absorbs 3–10 dB at
2.4 GHz). ~30 lines total, marginal but free.

---

## 4. Field Expedients — No Code Required

Improvised from field materials rather than procured. These add decibels to the *same* link
budget as everything above, so they compound with it.

### F1 — Elevation (kite / balloon / rooftop)

The dominant loss at ground level is **not** free-space path loss — it is obstruction of the
**Fresnel zone**. Radio energy travels as an ellipsoid, and intrusion into ~60% of it causes
diffraction loss *even with visual line of sight*.

📐 `r = 17.32 × √(d / 4f)` (d km, f GHz) → a 2 km link at 2.4 GHz needs **7.9 m** of midpoint
clearance. Two people holding phones at chest height have none.

📐 Radio horizon `d ≈ 4.12 × √h` → a phone at 100 m sees **41 km**.

| | |
| --- | --- |
| **Range: 2–3 km single hop** 📐 (BLE Coded budget 111 dB vs FSPL 106 dB @ 2 km) | |
| Cost | ₹200 kite, or a rooftop for free |

### F2 — Improvised parabolic reflector

📐 `G = 10·log₁₀(η·(πD/λ)²)`; λ = 12.5 cm at 2.4 GHz. A 40 cm foil-lined umbrella at η ≈ 0.5
gives **~17 dBi**. +15 dB = **5.6× range**; on both ends, +30 dB.

📐 Beamwidth `θ ≈ 70λ/D` ≈ **22°** — wide enough to aim by hand.

| | |
| --- | --- |
| **Range: 5–8 km with F1** 📐 | |
| Cost | Kitchen foil |
| Limit | Directional — suits fixed anchor nodes, not people moving |

---

## 5. Cumulative Range Progression

**The core answer to "what extends range to what extent."** Each row assumes everything above
it is in place.

⚠️ **Read the two right-hand columns before quoting any range figure.** Several rows reach
kilometres only because other people are standing in between, or because something is holding a
phone in the air. A range number without its precondition is not a claim you can defend.

| Step | Addition | Per hop | Hops | End-to-end | **Requires beyond 2 phones** | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | **Today** (BT Classic / Wi-Fi Direct) | 10–200 m | 1 | **200 m** | **nothing** | ✅ measured |
| 1 | + A1 BLE Coded PHY | 150–250 m | 1 | **250 m** | **nothing** | 📚 cited |
| 2 | + A8 Wi-Fi cheap wins | 250–300 m | 1 | **300 m** | **nothing** | 📐 |
| 3 | + A2 flood relay | 250 m | **10** | **2.5 km** | **9 more people** with phones, spaced ≤250 m along the route | 🔬 |
| 4 | + A3/A4 compression + repetition | 350 m | **10** | **3.5 km** | same 9 people | 🔬 |
| 5 | + A6 Wi-Fi SD tier | 400 m–1 km | **10** | **4–10 km** | same 9 people | 🔬 **unmeasured** |
| 6 | + A5 DTN carriers | — | 0 relay | **unbounded** (min–hrs) | **1 person or vehicle travelling the route** | 📚 principle |
| 7 | + F1 kite relay | 2–3 km | 1 | **2–3 km** | kite/balloon + **a third phone aloft** | 📐 |
| 8 | + F2 foil reflectors | 5–8 km | 1 | **5–8 km** | kite + 2 foil dishes, **aimed and fixed** | 📐 |
| 9 | + A7 satellite | global | 1 | **global** | Starlink D2C **service, compatible handset, subscription** — India ~2027 | 📚 |

### The same table, read by what you actually have

This is the more useful direction, and the one to rehearse for questions:

| What you have on hand | Range achievable | Which steps |
| --- | --- | --- |
| **2 phones, nothing else** | **300 m** | 0–2 |
| 2 phones + 1 person walking the route | **unbounded**, minutes–hours latency | 0–2, 6 |
| **10 phones spread along a 3 km line** | **3.5 km**, near real-time | 0–4 |
| 2 phones + kite + spare phone | **2–3 km**, single hop | 0–2, 7 |
| 2 phones + kite + kitchen foil | **5–8 km**, single hop, fixed aim | 0–2, 7–8 |
| A phone under a D2C constellation | **global** | 9 |

### Reading this honestly

- **Only steps 0–2 are truly "two phones and nothing else."** That ceiling is **~300 m**, and no
  software reaches past it on a single ground-level hop.
- **Steps 3–5 are not free range — they are range bought with people.** 2.5 km needs ten
  devices along the route. In an NDRF search line that is a realistic formation; for two
  isolated users it is unavailable.
- **Step 6 is the only unbounded option needing no extra devices** — but it requires *motion*.
  In a fully static, sparse network it adds nothing over the flood mesh.
- **Steps 7–8 need physical support** — something to hold a phone at height, and dishes aimed
  and held steady. Excellent for a fixed camp-to-camp link, useless for people moving.
- **Step 9 depends on infrastructure that does not exist in India yet** (~2027) and on the user
  having a compatible handset and subscription.
- 📐 All calculated figures assume genuine line of sight. Expect to keep **60–70%** of
  theoretical gain after imperfect aiming, kite movement, rain and battery-saver throttling.
- **Step 5 is the largest uncertainty in this table.** It could be the best channel available or
  barely better than A1. Measure before claiming.

---

## 6. Recommended Build Order

Dependency-correct, highest value first:

```
A1 BLE Coded PHY ──┐
                   ├──▶ A2 flood relay ──▶ A3 compression ──▶ A4 repetition
A8 Wi-Fi wins ─────┘                              │
                                                  ▼
                                          A5 DTN ──▶ A7 satellite
                                                  │
                                          A6 Wi-Fi SD (measure first)
```

| Phase | Contents | Outcome |
| --- | --- | --- |
| **1** | A1 + A2 | 30 m → **2.5 km**. The single biggest jump |
| **2** | A3 + A4 | Smaller frames, marginal links start working |
| **3** | A5 | Survives sparse deployment — **unbounded** |
| **4** | A6 + A7 | Longest hop; satellite-ready for 2027 |
| — | AFSK radio link | Optional agency mode; off the critical path |

**Test each phase with two phones in a field** — walk them apart until delivery fails. No
radios, no licensing question, no procurement.

---

## 7. Physics Appendix

So every figure above is checkable rather than asserted.

```
Free-space path loss     FSPL(dB) = 32.44 + 20·log₁₀(d_km) + 20·log₁₀(f_MHz)
Fresnel radius (mid)     r(m)     = 17.32 · √(d_km / 4·f_GHz)
Radio horizon            d(km)    ≈ 4.12 · √h_m
Parabolic gain           G(dBi)   = 10·log₁₀(η · (πD/λ)²)
Beamwidth                θ(°)     ≈ 70·λ/D
Range multiplier         ×        = 10^(ΔdB/20)
```

**Worked link budgets @ 2.4 GHz:**

```
FSPL @ 200 m =  86 dB      BT Classic budget    =  92 dB
FSPL @   1 km = 100 dB     BLE Coded budget     = 111 dB
FSPL @   2 km = 106 dB     Wi-Fi Direct budget  =  91 dB
FSPL @   5 km = 114 dB     ← 23 dB beyond Wi-Fi: no software fix reaches this
```

This is why §5 gets to kilometres through **hops, time and elevation** — never through a single
ground-level phone-to-phone link.

---

**Signal, where there is none.**
