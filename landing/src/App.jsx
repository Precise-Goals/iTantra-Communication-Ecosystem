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

/* ─── Visual Sitemap & System Index Section ─── */
function SitemapSection() {
  const sitemapCategories = [
    {
      title: '01 · SYSTEM OVERVIEW',
      links: [
        { label: 'Hero Viewport & Mandate', href: '#' },
        { label: 'Live STT Waveform Monitor', href: '#' },
        { label: 'System Performance Strip', href: '#' },
        { label: 'Indic Multilingual Marquee', href: '#languages' },
      ],
    },
    {
      title: '02 · MANIFESTO & VIDEO',
      links: [
        { label: 'Chapter 01 — Blackout Crisis', href: '#manifesto' },
        { label: 'Chapter 02 — On-Device Stream', href: '#manifesto' },
        { label: 'Chapter 03 — P2P Radio Mesh', href: '#manifesto' },
        { label: 'Field Demonstration Video', href: '#manifesto' },
      ],
    },
    {
      title: '03 · INDIC LANGUAGE MATRIX',
      links: [
        { label: 'Hindi & Marathi Models', href: '#languages' },
        { label: 'Gujarati & Odia Models', href: '#languages' },
        { label: 'Kannada, Tamil, Telugu, Malayalam', href: '#languages' },
        { label: 'Bengali & English Models', href: '#languages' },
      ],
    },
    {
      title: '04 · BENCHMARKS & SPECS',
      links: [
        { label: 'Accuracy & WER Metrics', href: '#metrics' },
        { label: 'RAM & CPU Resource Efficiency', href: '#metrics' },
        { label: 'End-to-End Voice Latency', href: '#metrics' },
        { label: 'Distress Alert Override Delta', href: '#metrics' },
      ],
    },
  ];

  return (
    <section id="sitemap" className="sitemap-section">
      <div className="section-inner">
        <div className="sitemap-header">
          <p className="section-label">CHAPTER 08 · SITEMAP & SYSTEM INDEX</p>
          <h2 className="section-heading">Complete Navigation Index</h2>
        </div>

        <div className="sitemap-grid font-mono">
          {sitemapCategories.map((cat, idx) => (
            <div key={idx} className="sitemap-col">
              <h3 className="sitemap-cat-title">{cat.title}</h3>
              <ul className="sitemap-link-list">
                {cat.links.map((link, i) => (
                  <li key={i}>
                    <a href={link.href} className="sitemap-link">
                      <span className="sitemap-arrow">→</span> {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─── Main App ─── */
export default function App() {
  const manifestoVideoRef = useRef(null);
  const hasPlayedUnmutedRef = useRef(false);

  useEffect(() => {
    const video = manifestoVideoRef.current;
    if (!video) return;

    // Pre-unlock audio policy on any user gesture (scroll, click, touch)
    const unlockAudio = () => {
      if (video && video.muted) {
        video.muted = false;
      }
    };
    window.addEventListener('pointerdown', unlockAudio, { once: true });
    window.addEventListener('keydown', unlockAudio, { once: true });

    const handleIntersection = (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          // First time entering section: play from start with audio
          if (!hasPlayedUnmutedRef.current) {
            hasPlayedUnmutedRef.current = true;
            video.currentTime = 0;
          }
          video.muted = false;
          const playPromise = video.play();
          if (playPromise !== undefined) {
            playPromise.catch((err) => {
              console.log('Unmuted playback policy catch, retrying muted fallback:', err);
              video.muted = true;
              video.play();
              const enableSound = () => {
                video.muted = false;
                window.removeEventListener('click', enableSound);
                window.removeEventListener('touchstart', enableSound);
                window.removeEventListener('scroll', enableSound);
              };
              window.addEventListener('click', enableSound, { once: true });
              window.addEventListener('touchstart', enableSound, { once: true });
              window.addEventListener('scroll', enableSound, { once: true });
            });
          }
        } else {
          video.pause();
        }
      });
    };

    const observer = new IntersectionObserver(handleIntersection, { threshold: 0.25 });
    const manifestoSec = document.getElementById('manifesto');
    if (manifestoSec) observer.observe(manifestoSec);

    return () => {
      observer.disconnect();
      window.removeEventListener('pointerdown', unlockAudio);
      window.removeEventListener('keydown', unlockAudio);
    };
  }, []);

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
          <span className="nav-logo-text">iTantra</span>
        </div>
        <div className="nav-center">
          <a href="#manifesto">MANIFESTO</a>
          <a href="#languages">LANGUAGES</a>
          <a href="#metrics">METRICS</a>
          <a href="#sitemap">SITEMAP</a>
        </div>
        <div className="nav-right">
          <span className="nav-status"><span className="status-dot"></span> OFFLINE — V1.0</span>
          <a href="https://github.com/Precise-Goals/iTantra-Communication-Ecosystem/releases/download/android-app/iTantra.apk" className="btn-cta">DOWNLOAD APK</a>
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
            iTantra turns any low-end Android phone into an offline voice lifeline —
            on-device speech-to-text and text-to-speech for <span className="red-text">10 Indian languages</span>,
            streamed phone-to-phone over <span className="blue-text">Wi-Fi Direct</span> or <span className="blue-text">Bluetooth</span>. No towers. No
            internet. No literacy required.
          </p>
          <div className="hero-actions">
            <a href="https://github.com/Precise-Goals/iTantra-Communication-Ecosystem/releases/download/android-app/iTantra.apk" className="btn-cta large">DOWNLOAD APK</a>
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
          <div className="manifesto-header">
            <div>
              <p className="section-label">THE MANIFESTO</p>
              <h2 className="section-heading manifesto-h2">Three chapters. One conviction.</h2>
            </div>
            <span className="manifesto-badge font-mono">
              <span className="status-dot"></span> HIGH-RELIABILITY ARCHITECTURE
            </span>
          </div>

          <div className="manifesto-grid">
            {/* Left: Video Player */}
            <div className="manifesto-video-container">
              <video 
                ref={manifestoVideoRef}
                className="manifesto-video"
                src="/dd.mp4" 
                controls 
                loop 
                playsInline
              />
              <div className="video-overlay-tag font-mono">
                ISRO PS-26173 · FIELD DEMONSTRATION
              </div>
            </div>

            {/* Right: The 3 Chapters */}
            <div className="manifesto-chapters">
              <div className="manifesto-chapter-card">
                <div className="chapter-card-header font-mono">
                  <span className="chapter-num">CHAPTER 01</span>
                  <span className="chapter-tag">BLACKOUT CRISIS</span>
                </div>
                <h3 className="chapter-title">Infrastructure Blackout</h3>
                <p className="chapter-desc">
                  When disaster strikes, cellular towers and power grids go dark. Traditional voice communication collapses instantly.
                </p>
              </div>

              <div className="manifesto-chapter-card">
                <div className="chapter-card-header font-mono">
                  <span className="chapter-num">CHAPTER 02</span>
                  <span className="chapter-tag">LOCAL INFERENCE</span>
                </div>
                <h3 className="chapter-title">On-Device Neural Stream</h3>
                <p className="chapter-desc">
                  AI4Bharat STT & sherpa-onnx TTS convert heavy audio into micro 200-byte Protobuf payloads, 100% on-device.
                </p>
              </div>

              <div className="manifesto-chapter-card">
                <div className="chapter-card-header font-mono">
                  <span className="chapter-num">CHAPTER 03</span>
                  <span className="chapter-tag font-mono">P2P RADIO MESH</span>
                </div>
                <h3 className="chapter-title">Decentralized Mesh Relay</h3>
                <p className="chapter-desc">
                  Phone-to-phone Wi-Fi Direct & Bluetooth Classic mesh nodes relay distress signals across miles without internet.
                </p>
              </div>
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

      {/* ── SITEMAP ── */}
      <SitemapSection />

      {/* ── FOOTER ── */}
      <footer className="site-footer">
        <div className="footer-inner">
          <div className="footer-logo">
            <span className="nav-logo-icon">((•))</span>
            <span className="nav-logo-text">iTantra</span>
          </div>
          <p className="footer-tagline">
            Built for the moment everything else fails.
          </p>
        </div>
      </footer>

    </div>
  );
}

