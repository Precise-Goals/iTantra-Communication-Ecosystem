import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

// Verification evidence tags
const TAGS = {
  MEASURED: { label: 'VERIFIED ON HARDWARE', symbol: '●', color: '#16a34a', bg: 'rgba(22, 163, 74, 0.08)', border: 'rgba(22, 163, 74, 0.2)' },
  CALCULATED: { label: 'PHYSICS CALCULATED', symbol: '▲', color: '#2563eb', bg: 'rgba(37, 99, 235, 0.08)', border: 'rgba(37, 99, 235, 0.2)' },
  CITED: { label: 'FIELD CITED / ANDROID 16', symbol: '■', color: '#d97706', bg: 'rgba(217, 119, 6, 0.08)', border: 'rgba(217, 119, 6, 0.2)' },
  PROJECTED: { label: 'FIELD PROJECTED', symbol: '◆', color: '#7c3aed', bg: 'rgba(124, 58, 237, 0.08)', border: 'rgba(124, 58, 237, 0.2)' },
};

// Cumulative range progression scenarios
const SCENARIOS = [
  {
    id: 'baseline',
    tabLabel: '2 Phones Only',
    reach: '200–300 m',
    reachMultiplier: '1× Baseline',
    tag: TAGS.MEASURED,
    hops: 1,
    latency: '< 50 ms',
    channel: 'Wi-Fi Direct + BT RFCOMM',
    budget: '91–92 dB',
    precondition: 'Two physical Android devices, ground level line-of-sight. Zero extra infrastructure.',
    summary: 'The honest device-verified baseline on main. Operates without handshakes or towers.',
    barPercent: 12,
  },
  {
    id: 'search-line',
    tabLabel: '10 Phones Search Line',
    reach: '2.5–3.5 km',
    reachMultiplier: '10× Multiplier',
    tag: TAGS.PROJECTED,
    hops: 10,
    latency: '< 500 ms',
    channel: 'BLE Coded PHY + Flood Relay',
    budget: '111 dB (+19 dB)',
    precondition: '9 additional people spaced ≤250 m along search corridor (e.g. NDRF rescue line).',
    summary: 'Flood mesh with TTL decrement, 256-entry LRU deduplication, and 100–500 ms random jitter.',
    barPercent: 46,
  },
  {
    id: 'dtn-carrier',
    tabLabel: '1 Walking / Vehicle Carrier',
    reach: '5–10+ km',
    reachMultiplier: '∞ Unbounded',
    tag: TAGS.CITED,
    hops: '0 relay (store-carry)',
    latency: '15 min – 2 hrs',
    channel: 'DTN Spray-and-Wait',
    budget: 'Contact-driven',
    precondition: '1 person walking or emergency vehicle moving along the disaster route.',
    summary: 'Replication capped at 8 bundle copies. 10,000 text bundles require only ~600 KB total storage.',
    barPercent: 78,
  },
  {
    id: 'kite-fresnel',
    tabLabel: 'Kite / Balloon Elevation',
    reach: '2–3 km (Single Hop)',
    reachMultiplier: '12× Over Clutter',
    tag: TAGS.CALCULATED,
    hops: 1,
    latency: 'Real-time',
    channel: 'BLE Coded aloft',
    budget: '111 dB FSPL clearance',
    precondition: '₹200 kite, tethered balloon, or rooftop elevating a relay phone to 20–50 m height.',
    summary: 'Clears the 7.9 m Fresnel midpoint radius, converting ground diffraction loss into line-of-sight.',
    barPercent: 42,
  },
  {
    id: 'foil-dish',
    tabLabel: 'Improvised Foil Dish',
    reach: '5–8 km (Fixed Aim)',
    reachMultiplier: '25× Compound',
    tag: TAGS.CALCULATED,
    hops: 1,
    latency: 'Real-time',
    channel: '2.4 GHz Parabolic Reflector',
    budget: '+15 to +30 dB Gain',
    precondition: '40 cm foil-lined umbrella (η ≈ 0.5) pointed between base camps (22° beamwidth).',
    summary: 'Compound link with kite elevation: kitchen foil provides +17 dBi gain to punch through distance.',
    barPercent: 88,
  },
  {
    id: 'satellite-d2c',
    tabLabel: 'Direct-to-Cell Satellite',
    reach: 'Global',
    reachMultiplier: 'Planetary',
    tag: TAGS.CITED,
    hops: 1,
    latency: 'Burst (idle most time)',
    channel: 'Android 16 D2C',
    budget: 'LEO Constellation',
    precondition: 'Compatible handset + Starlink D2C constellation reaching India (~2027).',
    summary: 'PROPERTY_SATELLITE_DATA_OPTIMIZED manifest integration ready for burst text telemetry.',
    barPercent: 100,
  },
];

export default function RangeBentoSection() {
  const [selectedScenario, setSelectedScenario] = useState(SCENARIOS[0]);
  const [activeTabCode, setActiveTabCode] = useState('PRESENT');

  return (
    <section id="range-architecture" className="range-bento-section">
      <div className="section-inner">

        {/* Section Header */}
        <motion.div
          className="range-bento-header"
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-60px' }}
          transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
        >
          <div className="range-header-left">
            <div className="section-label-row">
              <span className="section-label">CHAPTER 05 · RANGE IMPLEMENTATION GAP ANALYSIS</span>
              <span className="live-audit-pill">
                <span className="audit-dot"></span> AUDITED AGAINST CODEBASE
              </span>
            </div>
            <h2 className="section-heading range-heading">
              Physics, ground truth,<br />and zero adjectives.
            </h2>
          </div>
          <div className="range-header-right">
            <p className="range-header-desc">
              A range number without its precondition is not a claim you can defend.
              Here is what exists in the iTantra code today, what is absent, and exactly
              how far each physics principle or protocol addition moves the line.
            </p>
          </div>
        </motion.div>

        {/* BENTO GRID */}
        <div className="range-bento-grid">

          {/* CARD 1: CUMULATIVE RANGE SIMULATOR (HERO BENTO CARD - SPANS 2 COLUMNS) */}
          <motion.div
            className="bento-card bento-hero-card"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-40px' }}
            transition={{ duration: 0.5 }}
          >
            <div className="card-top-meta">
              <span className="bento-card-category font-mono">CUMULATIVE RANGE PROGRESSION</span>
              <span
                className="evidence-tag font-mono"
                style={{
                  color: selectedScenario.tag.color,
                  backgroundColor: selectedScenario.tag.bg,
                  borderColor: selectedScenario.tag.border,
                }}
              >
                {selectedScenario.tag.symbol} {selectedScenario.tag.label}
              </span>
            </div>

            <div className="interactive-selector-wrapper">
              <label className="selector-title font-mono">SELECT WHAT YOU HAVE ON HAND IN THE FIELD:</label>
              <div className="scenario-pills">
                {SCENARIOS.map((sc) => (
                  <button
                    key={sc.id}
                    type="button"
                    className={`scenario-pill ${selectedScenario.id === sc.id ? 'active' : ''}`}
                    onClick={() => setSelectedScenario(sc)}
                  >
                    {sc.tabLabel}
                  </button>
                ))}
              </div>
            </div>

            {/* Dynamic Results Banner */}
            <AnimatePresence mode="wait">
              <motion.div
                key={selectedScenario.id}
                className="scenario-display-box"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                transition={{ duration: 0.25 }}
              >
                <div className="scenario-display-main">
                  <div className="scenario-reach-group">
                    <span className="scenario-label font-mono">ACHIEVABLE DISTANCE</span>
                    <div className="scenario-reach-val">{selectedScenario.reach}</div>
                    <span className="scenario-submultiplier font-mono">{selectedScenario.reachMultiplier}</span>
                  </div>

                  {/* Telemetry Metrics */}
                  <div className="scenario-telemetry-grid">
                    <div className="telemetry-item">
                      <span className="t-label font-mono">HOPS REQUIRED</span>
                      <span className="t-val">{selectedScenario.hops}</span>
                    </div>
                    <div className="telemetry-item">
                      <span className="t-label font-mono">LINK BUDGET</span>
                      <span className="t-val">{selectedScenario.budget}</span>
                    </div>
                    <div className="telemetry-item">
                      <span className="t-label font-mono">LATENCY PROFILE</span>
                      <span className="t-val">{selectedScenario.latency}</span>
                    </div>
                    <div className="telemetry-item">
                      <span className="t-label font-mono">PROTOCOL LAYER</span>
                      <span className="t-val t-channel">{selectedScenario.channel}</span>
                    </div>
                  </div>
                </div>

                {/* Range Bar Indicator */}
                <div className="range-bar-wrapper">
                  <div className="range-bar-track">
                    <motion.div
                      className="range-bar-fill"
                      initial={{ width: 0 }}
                      animate={{ width: `${selectedScenario.barPercent}%` }}
                      transition={{ duration: 0.6, ease: 'easeOut' }}
                    />
                  </div>
                  <div className="range-bar-scale font-mono">
                    <span>10 m</span>
                    <span>200 m (Shipping)</span>
                    <span>2.5 km (10 Hops)</span>
                    <span>8 km (Foil)</span>
                    <span>Global (Satellite)</span>
                  </div>
                </div>

                {/* Preconditions & Honest Reading */}
                <div className="scenario-footer-callout">
                  <div className="callout-icon">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="12" cy="12" r="10" />
                      <line x1="12" y1="8" x2="12" y2="12" />
                      <line x1="12" y1="16" x2="12.01" y2="16" />
                    </svg>
                  </div>
                  <div className="callout-text">
                    <strong>Precondition: </strong>{selectedScenario.precondition}
                    <div className="callout-sub">{selectedScenario.summary}</div>
                  </div>
                </div>
              </motion.div>
            </AnimatePresence>
          </motion.div>

          {/* CARD 2: FRESNEL ZONE & ELEVATION PHYSICS (1 COLUMN) */}
          <motion.div
            className="bento-card bento-fresnel-card"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-40px' }}
            transition={{ duration: 0.5, delay: 0.1 }}
          >
            <div className="card-top-meta">
              <span className="bento-card-category font-mono">PHYSICS APPENDIX · §4 & §7</span>
              <span className="evidence-tag font-mono" style={{ color: TAGS.CALCULATED.color, backgroundColor: TAGS.CALCULATED.bg }}>
                {TAGS.CALCULATED.symbol} 7.9 M CLEARANCE
              </span>
            </div>

            <h3 className="bento-card-title">Fresnel Zone & Elevation</h3>
            <p className="bento-card-desc">
              Ground loss is dominated by Fresnel diffraction, not distance. Intrusion into 60% of the ellipsoid causes steep fade even with visual line-of-sight.
            </p>

            {/* Abstract SVG of Fresnel Zone Clearance */}
            <div className="fresnel-abstract-container">
              <svg className="fresnel-svg" viewBox="0 0 360 150" fill="none">
                {/* Ground plane */}
                <line x1="10" y1="130" x2="350" y2="130" stroke="#e5e7eb" strokeWidth="2" strokeDasharray="4 4" />
                <text x="180" y="145" textAnchor="middle" fill="#9ca3af" fontSize="9" fontFamily="monospace">GROUND CLUTTER & OBSTRUCTIONS (0 m)</text>

                {/* Ground nodes holding at chest height (1.4m) */}
                <circle cx="40" cy="115" r="5" fill="#ef4444" />
                <circle cx="320" cy="115" r="5" fill="#ef4444" />
                <path d="M40 115 Q180 85 320 115" stroke="#ef4444" strokeWidth="1.5" strokeDasharray="3 3" opacity="0.6" />
                <text x="180" y="105" textAnchor="middle" fill="#ef4444" fontSize="8" fontFamily="monospace">Chest-height: Ground penetrates Fresnel zone</text>

                {/* Elevated Fresnel Ellipsoid (Kite/Rooftop at height h) */}
                <ellipse cx="180" cy="55" rx="140" ry="36" fill="rgba(37, 99, 235, 0.05)" stroke="#2563eb" strokeWidth="1.5" />
                <ellipse cx="180" cy="55" rx="140" ry="22" stroke="#2563eb" strokeWidth="1" strokeDasharray="2 2" opacity="0.5" />
                
                {/* Midpoint radius arrow */}
                <line x1="180" y1="55" x2="180" y2="19" stroke="#2563eb" strokeWidth="1.5" markerEnd="url(#arrow)" />
                <text x="188" y="38" fill="#1d4ed8" fontSize="9" fontWeight="700" fontFamily="monospace">r = 7.9 m</text>

                {/* Elevated transmitter/receiver nodes */}
                <circle cx="40" cy="55" r="5" fill="#2563eb" />
                <circle cx="320" cy="55" r="5" fill="#2563eb" />
                <text x="40" y="44" textAnchor="middle" fill="#1e3a8a" fontSize="8" fontFamily="monospace">Node A</text>
                <text x="320" y="44" textAnchor="middle" fill="#1e3a8a" fontSize="8" fontFamily="monospace">Aloft (h)</text>
              </svg>
            </div>

            <div className="physics-formula-pills">
              <div className="formula-pill font-mono">
                <span className="formula-sym">r</span> = 17.32 · √(d / 4f)
              </div>
              <div className="formula-pill font-mono">
                <span className="formula-sym">d_horizon</span> ≈ 4.12 · √h
              </div>
            </div>
          </motion.div>

          {/* CARD 3: CODEBASE AUDIT — PRESENT TODAY VS THE GAP (1 COLUMN) */}
          <motion.div
            className="bento-card bento-audit-card"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-40px' }}
            transition={{ duration: 0.5, delay: 0.15 }}
          >
            <div className="card-top-meta">
              <span className="bento-card-category font-mono">CODEBASE VERIFICATION · §1 & §2</span>
              <div className="audit-toggle-btns">
                <button
                  type="button"
                  className={`audit-tab-btn ${activeTabCode === 'PRESENT' ? 'active' : ''}`}
                  onClick={() => setActiveTabCode('PRESENT')}
                >
                  On Main (Verified)
                </button>
                <button
                  type="button"
                  className={`audit-tab-btn ${activeTabCode === 'GAP' ? 'active' : ''}`}
                  onClick={() => setActiveTabCode('GAP')}
                >
                  Implementation Gap
                </button>
              </div>
            </div>

            <h3 className="bento-card-title">
              {activeTabCode === 'PRESENT' ? 'Shipping in Kotlin Today' : 'Protocol Roadmap Gaps'}
            </h3>

            {activeTabCode === 'PRESENT' ? (
              <div className="code-audit-list">
                <div className="audit-item">
                  <div className="audit-item-header">
                    <span className="audit-name">Wi-Fi Direct P2P</span>
                    <span className="audit-range font-mono">100–200 m</span>
                  </div>
                  <div className="audit-file font-mono">WifiDirectManager.kt</div>
                  <div className="audit-desc">Autonomous group formation, no router required. Tested end-to-end.</div>
                </div>

                <div className="audit-item">
                  <div className="audit-item-header">
                    <span className="audit-name">Bluetooth RFCOMM</span>
                    <span className="audit-range font-mono">10–30 m</span>
                  </div>
                  <div className="audit-file font-mono">BluetoothRFCOMMManager.kt</div>
                  <div className="audit-desc">Reliable fallback stream when Wi-Fi is unavailable. Tested on Android.</div>
                </div>

                <div className="audit-item">
                  <div className="audit-item-header">
                    <span className="audit-name">Protobuf Wire Protocol</span>
                    <span className="audit-range font-mono">50–300 B</span>
                  </div>
                  <div className="audit-file font-mono">ProtobufSerializer.kt</div>
                  <div className="audit-desc">Micro-payload structure over TCP socket :8765 with ACK RTT measurement.</div>
                </div>
              </div>
            ) : (
              <div className="code-audit-list">
                <div className="audit-item gap-item">
                  <div className="audit-item-header">
                    <span className="audit-name">A1 · BLE Coded PHY</span>
                    <span className="audit-gain font-mono">+19 dB / ~10×</span>
                  </div>
                  <div className="audit-file font-mono">AdvertisingSetParameters (PHY_LE_CODED)</div>
                  <div className="audit-desc">Connectionless extended advertising (254 B) unlocks 150–250 m per hop.</div>
                </div>

                <div className="audit-item gap-item">
                  <div className="audit-item-header">
                    <span className="audit-name">A2 · Flood Relay + Jitter</span>
                    <span className="audit-gain font-mono">× Hop Count</span>
                  </div>
                  <div className="audit-file font-mono">ttl = 9, msg_id = 10, 100-500ms jitter</div>
                  <div className="audit-desc">256-entry LRU deduplication prevents storm collapse across 10 nodes (2.5 km).</div>
                </div>

                <div className="audit-item gap-item">
                  <div className="audit-item-header">
                    <span className="audit-name">A5 · DTN Spray-and-Wait</span>
                    <span className="audit-gain font-mono">Unbounded (∞)</span>
                  </div>
                  <div className="audit-file font-mono">Room Bundle Entity (8 copies max)</div>
                  <div className="audit-desc">Store-carry-forward survives sparse networks via walking messengers.</div>
                </div>
              </div>
            )}
          </motion.div>

          {/* CARD 4: WIRE DIET & COMPRESSION (1 COLUMN) */}
          <motion.div
            className="bento-card bento-diet-card"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-40px' }}
            transition={{ duration: 0.5, delay: 0.2 }}
          >
            <div className="card-top-meta">
              <span className="bento-card-category font-mono">WIRE OPTIMIZATION · A3 & A4</span>
              <span className="evidence-tag font-mono" style={{ color: TAGS.MEASURED.color, backgroundColor: TAGS.MEASURED.bg }}>
                5× PAYLOAD SHRINK
              </span>
            </div>

            <h3 className="bento-card-title">Disaster Codebook Compression</h3>
            <p className="bento-card-desc">
              Indic text requires 3 bytes/char. A 4096-entry phrase dictionary reduces frames from 300 B to 60 B, buying airtime for 10× repetition diversity.
            </p>

            <div className="diet-comparison-bars">
              <div className="diet-row">
                <div className="diet-row-header font-mono">
                  <span>UNCOMPRESSED PROTOBUF</span>
                  <span className="diet-val-raw">300 Bytes</span>
                </div>
                <div className="diet-bar-track">
                  <div className="diet-bar-fill raw" style={{ width: '100%' }}></div>
                </div>
                <div className="diet-notes font-mono">36-byte UUID string · 3 B/char Indic text · 8-byte timestamps</div>
              </div>

              <div className="diet-row">
                <div className="diet-row-header font-mono">
                  <span>COMPRESSED CODEBOOK FRAME</span>
                  <span className="diet-val-opt">60 Bytes</span>
                </div>
                <div className="diet-bar-track">
                  <div className="diet-bar-fill opt" style={{ width: '20%' }}></div>
                </div>
                <div className="diet-notes font-mono">2-byte node ID · 12-bit phrase ID · 4-byte delta · 5× space saved</div>
              </div>
            </div>

            <div className="repetition-box">
              <div className="rep-stat font-mono">65% DELIVERY</div>
              <p className="rep-desc">
                At marginal range edge (10% single-try fade), 10 repetitions over 30s yield <code>1 − 0.9¹⁰ = 65%</code> delivery.
              </p>
            </div>
          </motion.div>

          {/* CARD 5: 4-PHASE BUILD ORDER ROADMAP (SPANS 2 COLUMNS) */}
          <motion.div
            className="bento-card bento-roadmap-card"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-40px' }}
            transition={{ duration: 0.5, delay: 0.25 }}
          >
            <div className="card-top-meta">
              <span className="bento-card-category font-mono">DEPENDENCY-CORRECT BUILD ORDER</span>
              <span className="evidence-tag font-mono" style={{ color: '#4b5563', backgroundColor: '#f3f4f6' }}>
                §6 IMPLEMENTATION PHASES
              </span>
            </div>

            <h3 className="bento-card-title">Highest-Return Engineering Pipeline</h3>

            <div className="phases-flow-grid">
              <div className="phase-card">
                <div className="phase-badge font-mono">PHASE 01 · 2.5 KM</div>
                <h4 className="phase-title">BLE Coded PHY + Flood Relay</h4>
                <p className="phase-desc">
                  A1 + A2. Unlocks connectionless extended advertising and TTL/dedup with jitter.
                </p>
                <div className="phase-result font-mono">30 m → 2.5 km (10 hops)</div>
              </div>

              <div className="phase-arrow">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </div>

              <div className="phase-card">
                <div className="phase-badge font-mono">PHASE 02 · 3.5 KM</div>
                <h4 className="phase-title">Compression & Repetition</h4>
                <p className="phase-desc">
                  A3 + A4. 60 B payload diet. Enables 10-attempt probabilistic link margin recovery.
                </p>
                <div className="phase-result font-mono">Marginal links turn reliable</div>
              </div>

              <div className="phase-arrow">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </div>

              <div className="phase-card">
                <div className="phase-badge font-mono">PHASE 03 · UNBOUNDED</div>
                <h4 className="phase-title">DTN Store-Carry-Forward</h4>
                <p className="phase-desc">
                  A5 Spray-and-Wait. Room bundle persistence for physical carriers on foot or vehicle.
                </p>
                <div className="phase-result font-mono">5–10 km in sparse regions</div>
              </div>

              <div className="phase-arrow">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </div>

              <div className="phase-card">
                <div className="phase-badge font-mono">PHASE 04 · GLOBAL</div>
                <h4 className="phase-title">Wi-Fi SD & Satellite D2C</h4>
                <p className="phase-desc">
                  A6 DNS-SD channel (-98 dBm) + A7 Android 16 satellite optimization for 2027.
                </p>
                <div className="phase-result font-mono">Planetary telemetry</div>
              </div>
            </div>
          </motion.div>

        </div>
      </div>
    </section>
  );
}
