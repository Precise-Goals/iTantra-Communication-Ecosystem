import React from 'react';
import { motion } from 'framer-motion';

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

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.1,
    },
  },
};

const colVariants = {
  hidden: { opacity: 0, y: 25 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.6, ease: [0.22, 1, 0.36, 1] },
  },
};

export default function SitemapSection() {
  return (
    <section id="sitemap" className="sitemap-section">
      <div className="section-inner">
        <motion.div
          className="sitemap-header"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-60px' }}
          transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
        >
          <p className="section-label">CHAPTER 08 · SITEMAP & SYSTEM INDEX</p>
          <h2 className="section-heading">Complete Navigation Index</h2>
        </motion.div>

        <motion.div
          className="sitemap-grid font-mono"
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: '-50px' }}
        >
          {sitemapCategories.map((cat, idx) => (
            <motion.div key={idx} className="sitemap-col" variants={colVariants}>
              <h3 className="sitemap-cat-title">{cat.title}</h3>
              <ul className="sitemap-link-list">
                {cat.links.map((link, i) => (
                  <motion.li
                    key={i}
                    whileHover={{ x: 6 }}
                    transition={{ type: 'spring', stiffness: 400, damping: 20 }}
                  >
                    <a href={link.href} className="sitemap-link">
                      <span className="sitemap-arrow">→</span> {link.label}
                    </a>
                  </motion.li>
                ))}
              </ul>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
