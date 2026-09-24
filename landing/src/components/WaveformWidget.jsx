import React from 'react';
import { motion } from 'framer-motion';

const bars = [8, 20, 35, 14, 42, 28, 50, 18, 38, 22, 55, 12, 45, 30, 20, 48, 15, 35, 25, 40, 18, 52, 30, 10, 44, 22, 38, 16, 48, 28];

export default function WaveformWidget() {
  return (
    <motion.div
      className="widget-card"
      whileHover={{ y: -4, boxShadow: '0 20px 40px rgba(0,0,0,0.12)' }}
      transition={{ duration: 0.3 }}
    >
      <div className="widget-header">
        <span className="widget-tag">LIVE · STT MONITOR</span>
        <span className="widget-online">
          <motion.span
            className="online-dot"
            animate={{ scale: [1, 1.3, 1], opacity: [1, 0.7, 1] }}
            transition={{ repeat: Infinity, duration: 2, ease: 'easeInOut' }}
          />
          ON-DEVICE
        </span>
      </div>
      <div className="waveform-row">
        {bars.map((h, i) => (
          <div
            key={i}
            className="wf-bar"
            style={{ height: `${h}px`, animationDelay: `${i * 0.07}s` }}
          />
        ))}
      </div>
      <div className="widget-transcript">&gt; मदद चाहिए — सेक्टर सात</div>
      <div className="widget-footer">
        <span className="wf-stat">WER <strong>0.11</strong></span>
        <span className="wf-stat">MEM <strong>8.2%</strong></span>
        <span className="wf-stat highlight">148 ms</span>
      </div>
    </motion.div>
  );
}
