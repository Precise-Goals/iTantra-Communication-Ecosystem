import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

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
  'RANGE & MESH': [
    { num: '01', label: 'Ground 2-phone baseline', val: '10–200 m', note: 'Wi-Fi Direct + BT RFCOMM, hardware-verified' },
    { num: '02', label: '10-Node flood relay (A1+A2)', val: '2.5 km', note: 'BLE Coded PHY + 100–500ms random jitter' },
    { num: '03', label: 'Codebook frame compression', val: '60 Bytes', note: '50–300 B → 60 B, 4096-entry disaster corpus' },
    { num: '04', label: 'DTN physical carrier range', val: 'Unbounded', note: 'Spray-and-Wait 8 copies; 600 KB for 10k bundles' },
  ],
  'E2E DELTA': [
    { num: '01', label: 'Voice to voice (full pipeline)', val: '≈ 240 ms', note: 'Sentence spoken → voice note playing' },
    { num: '02', label: 'Best case (short utterance)', val: '≈ 180 ms', note: '< 5 words, English' },
    { num: '03', label: 'Worst case (long sentence)', val: '≈ 380 ms', note: '15+ words, field noise' },
    { num: '04', label: 'Alert override latency', val: '< 50 ms', note: 'Distress signal to max volume playback' },
  ],
};

const tableVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.08,
    },
  },
  exit: { opacity: 0, transition: { duration: 0.15 } },
};

const rowVariants = {
  hidden: { opacity: 0, x: -16 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] } },
};

export default function Scorecard() {
  const tabs = ['EFFICIENCY', 'ACCURACY', 'LATENCY', 'RANGE & MESH', 'E2E DELTA'];
  const weights = { 'EFFICIENCY': '20%', 'ACCURACY': '40%', 'LATENCY': '20%', 'RANGE & MESH': '20%', 'E2E DELTA': '--' };
  const [active, setActive] = useState('ACCURACY');

  return (
    <section id="metrics" className="scorecard-section">
      <div className="section-inner">
        <motion.div
          className="scorecard-top"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-60px' }}
          transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
        >
          <div className="scorecard-left">
            <p className="section-label">CHAPTER 07 · THE SCORECARD</p>
            <h2 className="section-heading">Numbers, not adjectives.</h2>
            <div className="sc-tabs">
              {tabs.map(t => (
                <motion.button
                  key={t}
                  className={`sc-tab${active === t ? ' active' : ''}`}
                  onClick={() => setActive(t)}
                  whileHover={{ scale: 1.03 }}
                  whileTap={{ scale: 0.97 }}
                >
                  {t} — {weights[t]}
                </motion.button>
              ))}
            </div>
          </div>
          <div className="scorecard-right">
            <p className="scorecard-note">
              Evaluation-weighted benchmarks, measured on a 2 GB RAM,
              quad-core Android device with radios on and internet off.
            </p>
          </div>
        </motion.div>

        <AnimatePresence mode="wait">
          <motion.div
            key={active}
            className="sc-table"
            variants={tableVariants}
            initial="hidden"
            animate="visible"
            exit="exit"
          >
            {SCORECARD_DATA[active].map((row) => (
              <motion.div
                key={row.num}
                className="sc-row"
                variants={rowVariants}
                whileHover={{ backgroundColor: '#f5f5f5' }}
              >
                <span className="sc-num">{row.num}</span>
                <span className="sc-label">{row.label}</span>
                <span className="sc-val">{row.val}</span>
                <span className="sc-note">{row.note}</span>
              </motion.div>
            ))}
          </motion.div>
        </AnimatePresence>
      </div>
    </section>
  );
}
