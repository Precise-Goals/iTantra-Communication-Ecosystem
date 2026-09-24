import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';

const APK_URL =
  'https://github.com/Precise-Goals/iTantra-Communication-Ecosystem/releases/download/android-app/iTantra.apk';

export default function Navbar() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();
  const isHome = location.pathname === '/';

  const getSectionHref = (hash) => (isHome ? hash : `/${hash}`);

  return (
    <header className="navbar-wrapper">
      <motion.nav
        className="navbar"
        initial={{ y: -60, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
      >
        <div className="navbar-container">
          {/* Logo on Left */}
          <Link to="/" className="nav-logo" aria-label="iTantra Home">
            <span className="nav-logo-text">iTantra.</span>
          </Link>

          {/* Center Nav Links */}
          <div className="nav-links-center">
            <a href={getSectionHref('#app-preview')} className="nav-link-item">
              Products
            </a>
            <a href={getSectionHref('#range-architecture')} className="nav-link-item">
              Range Architecture
            </a>
            <a href={getSectionHref('#metrics')} className="nav-link-item">
              Developers
            </a>
            <a href={getSectionHref('#languages')} className="nav-link-item">
              Resources
            </a>
            <Link
              to="/research-paper-article"
              className={`nav-link-item ${
                location.pathname === '/research-paper-article' ? 'nav-link-active' : ''
              }`}
            >
              Research Paper
            </Link>
            <a href={getSectionHref('#sitemap')} className="nav-link-item">
              Company
            </a>
          </div>

          {/* Right Action Buttons: Download APK */}
          <div className="nav-actions-right">
            <a
              href={APK_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="nav-btn-login"
              title="Download APK / Access iTantra Console"
            >
              Download the Apk
            </a>
          </div>

          {/* Mobile Hamburger Toggle */}
          <button
            type="button"
            className="nav-mobile-toggle"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label="Toggle navigation menu"
          >
            <span className={`hamburger-bar ${mobileMenuOpen ? 'open' : ''}`} />
            <span className={`hamburger-bar ${mobileMenuOpen ? 'open' : ''}`} />
          </button>
        </div>
      </motion.nav>

      {/* Mobile Drawer */}
      <AnimatePresence>
        {mobileMenuOpen && (
          <motion.div
            className="mobile-nav-drawer"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.25 }}
          >
            <div className="mobile-nav-links">
              <a
                href={getSectionHref('#app-preview')}
                className="mobile-nav-item"
                onClick={() => setMobileMenuOpen(false)}
              >
                Products
              </a>
              <a
                href={getSectionHref('#range-architecture')}
                className="mobile-nav-item"
                onClick={() => setMobileMenuOpen(false)}
              >
                Range Architecture
              </a>
              <a
                href={getSectionHref('#metrics')}
                className="mobile-nav-item"
                onClick={() => setMobileMenuOpen(false)}
              >
                Developers
              </a>
              <a
                href={getSectionHref('#languages')}
                className="mobile-nav-item"
                onClick={() => setMobileMenuOpen(false)}
              >
                Resources
              </a>
              <Link
                to="/research-paper-article"
                className={`mobile-nav-item ${
                  location.pathname === '/research-paper-article' ? 'nav-link-active' : ''
                }`}
                onClick={() => setMobileMenuOpen(false)}
              >
                Research Paper
              </Link>
              <a
                href={getSectionHref('#sitemap')}
                className="mobile-nav-item"
                onClick={() => setMobileMenuOpen(false)}
              >
                Company
              </a>
              <div className="mobile-nav-actions">
                <a
                  href={APK_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="nav-btn-login mobile"
                  onClick={() => setMobileMenuOpen(false)}
                >
                  Download the Apk
                </a>
                <a
                  href={getSectionHref('#sitemap')}
                  className="nav-btn-contact mobile"
                  onClick={() => setMobileMenuOpen(false)}
                >
                  Contact Us
                </a>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
