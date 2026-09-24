import React from 'react';
import { motion } from 'framer-motion';
import WaveformWidget from './WaveformWidget';

const APK_URL =
  'https://github.com/Precise-Goals/iTantra-Communication-Ecosystem/releases/download/android-app/iTantra.apk';

const fadeUp = (delay = 0) => ({
  initial: { opacity: 0, y: 40 },
  animate: { opacity: 1, y: 0 },
  transition: { duration: 0.7, ease: [0.22, 1, 0.36, 1], delay },
});

export default function HeroSection() {
  return (
    <section className="hero">
      <div className="hero-left">
        <motion.div className="hero-badge" {...fadeUp(0.2)}>
          <span className="badge-icon">⚠</span>
          DISTRESS-READY — FULLY OFFLINE SPEECH
        </motion.div>

        <motion.h1 className="hero-h1" {...fadeUp(0.35)}>
          VOICE TRAVELS<br />
          WHEN NETWORKS<br />
          DON<span className="red-apos">'</span>T.
        </motion.h1>

        <motion.p className="hero-p" {...fadeUp(0.5)}>
          iTantra turns any low-end Android phone into an offline voice lifeline —
          on-device speech-to-text and text-to-speech for{' '}
          <span className="red-text">10 Indian languages</span>, streamed phone-to-phone over{' '}
          <span className="blue-text">Wi-Fi Direct</span> or{' '}
          <span className="blue-text">Bluetooth</span>. No towers. No internet. No literacy required.
        </motion.p>

        <motion.div className="hero-actions" {...fadeUp(0.65)}>
          <a
            href={APK_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="btn-cta large"
          >
            DOWNLOAD APK (v1.0)
          </a>
          <a href="#manifesto" className="hero-link">READ THE MANIFESTO ↓</a>
        </motion.div>
      </div>

      <motion.div
        className="hero-right"
        initial={{ opacity: 0, x: 60 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1], delay: 0.4 }}
      >
        <WaveformWidget />
      </motion.div>
    </section>
  );
}
