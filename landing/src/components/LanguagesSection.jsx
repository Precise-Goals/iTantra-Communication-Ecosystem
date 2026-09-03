import React from 'react';
import { motion } from 'framer-motion';

const languages = [
  { num: '01', native: 'हिन्दी',    eng: 'HINDI' },
  { num: '02', native: 'ગુજરાતી',   eng: 'GUJARATI' },
  { num: '03', native: 'मराठी',     eng: 'MARATHI' },
  { num: '04', native: 'ಕನ್ನಡ',    eng: 'KANNADA' },
  { num: '05', native: 'മലയാളം',   eng: 'MALAYALAM' },
  { num: '06', native: 'தமிழ்',    eng: 'TAMIL' },
  { num: '07', native: 'తెలుగు',   eng: 'TELUGU' },
  { num: '08', native: 'ଓଡ଼ିଆ',    eng: 'ODIA' },
  { num: '09', native: 'বাংলা',    eng: 'BENGALI' },
  { num: '10', native: 'English',   eng: 'ENGLISH' },
];

const container = {
  hidden: {},
  show: {
    transition: {
      staggerChildren: 0.07,
      delayChildren: 0.15,
    },
  },
};

const card = {
  hidden: { opacity: 0, y: 32, scale: 0.95 },
  show:   { opacity: 1, y: 0,  scale: 1,
    transition: { duration: 0.5, ease: [0.22, 1, 0.36, 1] } },
};

export default function LanguagesSection() {
  return (
    <section id="languages" className="languages-section">
      <div className="section-inner">

        <motion.div
          className="languages-header"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-60px' }}
          transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
        >
          <div className="languages-header-left">
            <p className="section-label">CHAPTER 04 · THE MATRIX</p>
            <h2 className="section-heading">Ten languages.<br />One tap each.</h2>
          </div>
          <div className="languages-header-right">
            <p className="lang-desc">
              Every model is quantized, under <span className="red-text">12 MB</span>, and runs
              entirely on device. Tap a language to hear its voice signature and live benchmark.
            </p>
          </div>
        </motion.div>

        <motion.div
          className="lang-grid"
          variants={container}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, margin: '-40px' }}
        >
          {languages.map((lang) => (
            <motion.div
              key={lang.num}
              className="lang-card"
              variants={card}
              whileHover={{ y: -4, boxShadow: '0 12px 32px rgba(0,0,0,0.10)' }}
            >
              <div className="lang-card-num">{lang.num}</div>
              <div className="lang-card-native">{lang.native}</div>
              <div className="lang-card-eng">{lang.eng}</div>
            </motion.div>
          ))}
        </motion.div>

      </div>
    </section>
  );
}
