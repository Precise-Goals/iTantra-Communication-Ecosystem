import React, { useState, useEffect, useRef } from 'react';
import './App.css';

/* ─── Animated Waveform Widget ─── */
function WaveformWidget() {
  const bars = [8, 20, 35, 14, 42, 28, 50, 18, 38, 22, 55, 12, 45, 30, 20, 48, 15, 35, 25, 40, 18, 52, 30, 10, 44, 22, 38, 16, 48, 28];
  return (
    <div className="widget-card">
      <div className="widget-header">
        <span className="widget-tag">LIVE · STT MONITOR</span>
        <span className="widget-online"><span className="online-dot"></span> ON-DEVICE</span>
      </div>
      <div className="waveform-row">
        {bars.map((h, i) => (
          <div
            key={i}
            className="wf-bar"
            style={{
              height: `${h}px`,
              animationDelay: `${i * 0.07}s`,
            }}
          />
        ))}
      </div>
      <div className="widget-transcript">&gt; मदद चाहिए — सेक्टर सात</div>
      <div className="widget-footer">
        <span className="wf-stat">WER <strong>0.11</strong></span>
        <span className="wf-stat">MEM <strong>8.2%</strong></span>
        <span className="wf-stat highlight">148 ms</span>
      </div>
    </div>
  );
}

/* ─── Scrolling Language Marquee ─── */
function Marquee() {
  const langs = ['ગુજરાતી', 'मराठी', 'ಕನ್ನಡ', 'മലയാളം', 'தமிழ்', 'తెలుగు', 'ଓଡ଼ିଆ', 'বাংলা', 'English', 'हिन्दी'];
  const items = [...langs, ...langs]; // duplicate for seamless loop
  return (
    <div className="marquee-wrapper">
      <div className="marquee-track">
        {items.map((l, i) => (
          <span key={i} className="marquee-item">
            {l} <span className="marquee-dot">•</span>
          </span>
        ))}
      </div>
    </div>
  );
}

/* ─── Scorecard Tab Section ─── */
const SCORECARD_DATA = {
  'ACCURACY': [
    { num: '01', label: 'STT word error rate (avg)', val: '< 8.5%', note: 'Across all 10 languages, clean + field audio' },
    { num: '02', label: 'Best language (English)', val: '5.4% WER', note: 'Largest training corpus' },
    { num: '03', label: 'Pause / sentence detection', val: '96% precision', note: 'Energy + embedding hybrid VAD' },
    { num: '04', label: 'TTS legibility (MOS)', val: '≥ 4.2 / 5', note: 'Human-rated naturalness & flow' },
  ],
  'EFFICIENCY': [
    { num: '01', label: 'Model size per language', val: '< 12 MB', note: 'Quantized TFLite Micro conformer' },
    { num: '02', label: 'RAM usage at inference', val: '< 50 MB', note: 'Measured on 2 GB device' },
    { num: '03', label: 'CPU usage (avg)', val: '< 18%', note: 'Quad-core 1.8 GHz processor' },
    { num: '04', label: 'Battery draw (per hour)', val: '≈ 4%', note: 'Screen off, radio on' },
  ],
  'LATENCY': [
    { num: '01', label: 'Mic to STT result', val: '< 240 ms', note: 'End-to-end on-device' },
    { num: '02', label: 'TTS first audio chunk', val: '< 120 ms', note: 'Neural vocoder warm start' },
    { num: '03', label: 'P2P packet delivery', val: '≈ 25 ms', note: 'Wi-Fi Direct, <10m range' },
    { num: '04', label: 'Alert trigger to playback', val: 'instant', note: 'Bypasses OS audio focus' },
  ],
  'E2E DELTA': [
    { num: '01', label: 'Voice to voice (full pipeline)', val: '≈ 240 ms', note: 'Sentence spoken → voice note playing' },
    { num: '02', label: 'Best case (short utterance)', val: '≈ 180 ms', note: '< 5 words, English' },
    { num: '03', label: 'Worst case (long sentence)', val: '≈ 380 ms', note: '15+ words, field noise' },
    { num: '04', label: 'Alert override latency', val: '< 50 ms', note: 'Distress signal to max volume playback' },
  ],
};

function Scorecard() {
  const tabs = ['EFFICIENCY', 'ACCURACY', 'LATENCY', 'E2E DELTA'];
  const weights = { 'EFFICIENCY': '20%', 'ACCURACY': '48%', 'LATENCY': '20%', 'E2E DELTA': '--' };
  const [active, setActive] = useState('ACCURACY');

  return (
    <section id="metrics" className="scorecard-section">
      <div className="section-inner">
        <div className="scorecard-top">
          <div className="scorecard-left">
            <p className="section-label">CHAPTER 07 · THE SCORECARD</p>
            <h2 className="section-heading">Numbers, not adjectives.</h2>
            <div className="sc-tabs">
              {tabs.map(t => (
                <button
                  key={t}
                  className={`sc-tab${active === t ? ' active' : ''}`}
                  onClick={() => setActive(t)}
                >
                  {t} — {weights[t]}
                </button>
              ))}
            </div>
          </div>
          <div className="scorecard-right">
            <p className="scorecard-note">
              Evaluation-weighted benchmarks, measured on a 2 GB RAM,
              quad-core Android device with radios on and internet off.
            </p>
          </div>
        </div>
        <div className="sc-table">
          {SCORECARD_DATA[active].map((row) => (
            <div key={row.num} className="sc-row">
              <span className="sc-num">{row.num}</span>
              <span className="sc-label">{row.label}</span>
              <span className="sc-val">{row.val}</span>
              <span className="sc-note">{row.note}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─── Main App ─── */
export default function App() {
  const languages = [
    { num: '01', native: 'हिन्दी', eng: 'HINDI' },
    { num: '02', native: 'ગુજરાતી', eng: 'GUJARATI' },
    { num: '03', native: 'मराठी', eng: 'MARATHI' },
    { num: '04', native: 'ಕನ್ನಡ', eng: 'KANNADA' },
    { num: '05', native: 'മലയാളം', eng: 'MALAYALAM' },
    { num: '06', native: 'தமிழ்', eng: 'TAMIL' },
    { num: '07', native: 'తెలుగు', eng: 'TELUGU' },
    { num: '08', native: 'ଓଡ଼ିଆ', eng: 'ODIA' },
    { num: '09', native: 'বাংলা', eng: 'BENGALI' },
    { num: '10', native: 'English', eng: 'ENGLISH' },
  ];

  return (
    <div className="app">

      {/* ── NAVBAR ── */}
      <nav className="navbar">
        <div className="nav-logo">
          <span className="nav-logo-icon">((•))</span>
          <span className="nav-logo-text">VĀK-10</span>
        </div>
        <div className="nav-center">
          <a href="#manifesto">MANIFESTO</a>
          <a href="#languages">LANGUAGES</a>
          <a href="#pipeline">PIPELINE</a>
          <a href="#simulator">SIMULATOR</a>
          <a href="#metrics">METRICS</a>
          <a href="#team">TEAM</a>
        </div>
        <div className="nav-right">
          <span className="nav-status"><span className="status-dot"></span> OFFLINE — V1.0</span>
          <button className="btn-cta">PUSH TO TALK</button>
        </div>
      </nav>

      {/* ── HERO ── */}
      <section className="hero">
        <div className="hero-left">
          <div className="hero-badge">
            <span className="badge-icon">⚠</span>
            DISTRESS-READY — FULLY OFFLINE SPEECH
          </div>
          <h1 className="hero-h1">
            VOICE TRAVELS<br />
            WHEN NETWORKS<br />
            DON<span className="red-apos">'</span>T.
          </h1>
          <p className="hero-p">
            VĀK-10 turns any low-end Android phone into an offline voice lifeline —
            on-device speech-to-text and text-to-speech for <span className="red-text">10 Indian languages</span>,
            streamed phone-to-phone over <span className="blue-text">Wi-Fi Direct</span> or <span className="blue-text">Bluetooth</span>. No towers. No
            internet. No literacy required.
          </p>
          <div className="hero-actions">
            <button className="btn-cta large">TRY THE LIVE SIMULATOR</button>
            <a href="#manifesto" className="hero-link">READ THE MANIFESTO ↓</a>
          </div>
        </div>
        <div className="hero-right">
          <WaveformWidget />
        </div>
      </section>

      {/* ── STATS STRIP ── */}
      <div className="stats-strip">
        <div className="stat-item">
          <div className="stat-val">10</div>
          <div className="stat-label">INDIAN LANGUAGES</div>
        </div>
        <div className="stat-item">
          <div className="stat-val">&lt;12 MB</div>
          <div className="stat-label stat-red">PER STT MODEL</div>
        </div>
        <div className="stat-item">
          <div className="stat-val">0</div>
          <div className="stat-label stat-red">INTERNET REQUIRED</div>
        </div>
        <div className="stat-item">
          <div className="stat-val">≈240 ms</div>
          <div className="stat-label">VOICE TO VOICE</div>
        </div>
      </div>

      {/* ── MARQUEE ── */}
      <Marquee />

      {/* ── MANIFESTO ── */}
      <section id="manifesto" className="manifesto-section">
        <div className="section-inner">
          <p className="section-label">THE MANIFESTO</p>
          <h2 className="section-heading manifesto-h2">Three chapters. One conviction.</h2>

          {/* Chapter 01 */}
          <div className="chapter">
            <div className="chapter-num-col">
              <div className="chapter-big-num">01</div>
              <div className="chapter-tag">THE PROBLEM</div>
            </div>
            <div className="chapter-content">
              <h3 className="chapter-h3">When towers fall, voices disappear.</h3>
              <p className="chapter-p">
                Floods, earthquakes, blackouts — the moment communication matters most is the
                moment networks collapse. And when a message finally gets through, it arrives as
                text: useless to the 25+ crore Indians who cannot read it, and too slow for someone
                trapped, injured, or afraid. Voice is the most inclusive interface ever built. It is also
                the first casualty of every disaster.
              </p>
              <div className="chapter-img-placeholder">
                <span>Chapter Image</span>
              </div>
            </div>
          </div>

          {/* Chapter 02 */}
          <div className="chapter">
            <div className="chapter-num-col">
              <div className="chapter-big-num">02</div>
              <div className="chapter-tag">THE INSIGHT</div>
            </div>
            <div className="chapter-content">
              <h3 className="chapter-h3">Speech carries more than a screen full of text ever can.</h3>
              <p className="chapter-p">
                Voice is data-heavy, which is exactly why it struggles on the low-bandwidth links
                that survive in a disaster. But in an alert or distress situation, a spoken message
                reaches everyone — literate or not, in the language they actually speak. Text is
                efficient to move. Voice is the one that includes everybody. This system is built to
                move voice — with no internet in between.
              </p>
            </div>
          </div>

          {/* Chapter 03 */}
          <div className="chapter">
            <div className="chapter-num-col">
              <div className="chapter-big-num">03</div>
              <div className="chapter-tag">THE PROOF</div>
            </div>
            <div className="chapter-content">
              <h3 className="chapter-h3">Proof runs on a ₹6,000 phone, entirely offline.</h3>
              <p className="chapter-p">
                Every model is quantized under 12 MB. Every recognition and synthesis step runs
                on-device. The relay is peer-to-peer — Wi-Fi Direct or Bluetooth, no router required.
                The entire pipeline — mic to spoken translation — completes in roughly 240 milliseconds.
                No cloud. No subscription. No signal required.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ── LANGUAGES / MATRIX ── */}
      <section id="languages" className="languages-section">
        <div className="section-inner">
          <div className="languages-header">
            <div className="languages-header-left">
              <p className="section-label">CHAPTER 04 · THE MATRIX</p>
              <h2 className="section-heading">Ten languages.<br />One tap each.</h2>
            </div>
            <div className="languages-header-right">
              <p className="lang-desc">
                Every model is quantized, under <span className="red-text">12 MB</span>, and runs
                entirely on device. Tap a language to hear its voice
                signature and live benchmark.
              </p>
            </div>
          </div>
          <div className="lang-grid">
            {languages.map((lang) => (
              <div key={lang.num} className="lang-card">
                <div className="lang-card-num">{lang.num}</div>
                <div className="lang-card-native">{lang.native}</div>
                <div className="lang-card-eng">{lang.eng}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── SCORECARD ── */}
      <Scorecard />

      {/* ── FOOTER ── */}
      <footer className="site-footer">
        <div className="footer-inner">
          <div className="footer-logo">
            <span className="nav-logo-icon">((•))</span>
            <span className="nav-logo-text">VĀK-10</span>
          </div>
          <p className="footer-tagline">
            Built for the moment everything else fails.
          </p>
        </div>
      </footer>

    </div>
  );
}
