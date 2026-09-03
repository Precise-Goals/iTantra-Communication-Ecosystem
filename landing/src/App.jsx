import React from 'react';
import './App.css';

function App() {
  return (
    <div className="app-container">
      {/* Header with status */}
      <header className="status-header">
        <span className="status-dot"></span>
        <span className="status-text">OFFLINE VOICE RELAY — SYSTEM OVERVIEW</span>
      </header>

      {/* Main Hero Section */}
      <main className="hero-section">
        <h1 className="hero-title">
          When the network<br />
          drops,<br />
          <span className="accent-text">the voice</span> still has to<br />
          get through.
        </h1>
        
        <p className="hero-description">
          A speech-to-speech relay for distress and alert scenarios — ten Indian
          languages, recognised and synthesised entirely on-device, carried phone
          to phone over Wi-Fi or Bluetooth with no server in between.
        </p>

        {/* Animated Waveform Graphic */}
        <div className="waveform-graphic">
          <div className="bar bar-1"></div>
          <div className="bar bar-2"></div>
          <div className="bar bar-3"></div>
          <div className="bar bar-4"></div>
          <div className="bar bar-5"></div>
          <div className="bar bar-6"></div>
          <div className="bar bar-7"></div>
          <div className="bar bar-8"></div>
          <div className="bar bar-9"></div>
          <div className="bar bar-10"></div>
          <div className="bar bar-11"></div>
        </div>
      </main>

      {/* Secondary Section */}
      <section className="secondary-section">
        <div className="section-divider"></div>
        <div className="section-meta">
          <span className="section-number">01</span>
          <span className="section-separator">·</span>
          <span className="section-title">Background</span>
        </div>
        
        <h2 className="secondary-heading">
          Speech carries more than a<br />
          screen full of text ever can.
        </h2>
        
        <p className="secondary-description">
          Voice is data-heavy, which is exactly why it struggles on the low-bandwidth links
          that survive in a disaster. But in an alert or distress situation, a spoken message
          reaches everyone — literate or not, in the language they actually speak. Text is
          efficient to move. Voice is the one that includes everybody. This system is built to...
        </p>
      </section>
    </div>
  );
}

export default App;
