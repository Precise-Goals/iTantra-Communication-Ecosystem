import React from 'react';
import { motion } from 'framer-motion';

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        {/* Giant Sarvam-Style iTantra White Display Text with scroll reveal */}
        <motion.div
          className="footer-big-brand-wrapper"
          initial={{ opacity: 0, y: 60, scale: 0.95 }}
          whileInView={{ opacity: 1, y: 0, scale: 1 }}
          viewport={{ once: true, margin: '-40px' }}
          transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
        >
          <h1 className="footer-big-brand">iTantra</h1>
        </motion.div>
      </div>
    </footer>
  );
}
