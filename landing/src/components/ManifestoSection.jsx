import React, { useRef } from 'react';
import { motion, useInView } from 'framer-motion';

const CHAPTERS = [
  {
    num: 'CHAPTER 01', tag: 'BLACKOUT CRISIS',
    title: 'Infrastructure Blackout',
    desc: 'When disaster strikes, cellular towers and power grids go dark. Traditional voice communication collapses instantly.',
  },
  {
    num: 'CHAPTER 02', tag: 'LOCAL INFERENCE',
    title: 'On-Device Neural Stream',
    desc: 'AI4Bharat STT & sherpa-onnx TTS convert heavy audio into micro 200-byte Protobuf payloads, 100% on-device.',
  },
  {
    num: 'CHAPTER 03', tag: 'P2P RADIO MESH',
    title: 'Decentralized Mesh Relay',
    desc: 'Phone-to-phone Wi-Fi Direct & Bluetooth Classic mesh nodes relay distress signals across miles without internet.',
  },
];

export default function ManifestoSection() {
  const headerRef = useRef(null);
  const headerInView = useInView(headerRef, { once: true, margin: '-60px' });

  return (
    <section id="manifesto" className="manifesto-section">
      <div className="section-inner">

        {/* Header */}
        <motion.div
          ref={headerRef}
          className="manifesto-header"
          initial={{ opacity: 0, y: 30 }}
          animate={headerInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
        >
          <div>
            <p className="section-label">THE MANIFESTO</p>
            <h2 className="section-heading manifesto-h2">Three chapters. One conviction.</h2>
          </div>
          <span className="manifesto-badge font-mono">
            <span className="status-dot"></span> HIGH-RELIABILITY ARCHITECTURE
          </span>
        </motion.div>

        <div className="manifesto-grid">
          {/* Video — slides in from left */}
          <motion.div
            className="manifesto-video-container"
            initial={{ opacity: 0, x: -50 }}
            whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true, margin: '-80px' }}
            transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
          >
            <video
              className="manifesto-video"
              src="/dd.mp4"
              poster="/thumbnail.png"
              controls
              loop
              playsInline
              preload="metadata"
            />
            <div className="video-overlay-tag font-mono">
              ISRO PS-26173 · FIELD DEMONSTRATION
            </div>
          </motion.div>

          {/* Chapter cards — stagger from right */}
          <div className="manifesto-chapters">
            {CHAPTERS.map((ch, i) => (
              <motion.div
                key={i}
                className="manifesto-chapter-card"
                initial={{ opacity: 0, x: 40 }}
                whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true, margin: '-40px' }}
                transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1], delay: i * 0.12 }}
                whileHover={{ scale: 1.02, boxShadow: '0 8px 32px rgba(0,0,0,0.10)' }}
              >
                <div className="chapter-card-header font-mono">
                  <span className="chapter-num">{ch.num}</span>
                  <span className="chapter-tag font-mono">{ch.tag}</span>
                </div>
                <h3 className="chapter-title">{ch.title}</h3>
                <p className="chapter-desc">{ch.desc}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
