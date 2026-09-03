import React from 'react';
import { motion } from 'framer-motion';
import { useInView } from 'framer-motion';
import { useRef } from 'react';

const langs = ['ગુજરાતી', 'मराठी', 'ಕನ್ನಡ', 'മലയാളം', 'தமிழ்', 'తెలుగు', 'ଓଡ଼ିଆ', 'বাংলা', 'English', 'हिन्दी'];
const items = [...langs, ...langs];

export default function Marquee() {
  const ref = useRef(null);
  const inView = useInView(ref, { once: true, margin: '-80px' });

  return (
    <motion.div
      ref={ref}
      className="marquee-wrapper"
      initial={{ opacity: 0 }}
      animate={inView ? { opacity: 1 } : {}}
      transition={{ duration: 0.6 }}
    >
      <div className="marquee-track">
        {items.map((l, i) => (
          <span key={i} className="marquee-item">
            {l} <span className="marquee-dot">•</span>
          </span>
        ))}
      </div>
    </motion.div>
  );
}
