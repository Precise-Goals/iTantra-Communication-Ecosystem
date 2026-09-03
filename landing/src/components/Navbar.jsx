import React from 'react';
import { motion } from 'framer-motion';

const APK_URL =
  'https://github.com/Precise-Goals/iTantra-Communication-Ecosystem/releases/download/android-app/iTantra.apk';

export default function Navbar() {
  return (
    <motion.nav
      className="navbar"
      initial={{ y: -70, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
    >
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
        <a
          href={APK_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="btn-cta"
        >
          DOWNLOAD APK
        </a>
      </div>
    </motion.nav>
  );
}
