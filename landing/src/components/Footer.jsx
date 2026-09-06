import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';

const GITHUB_REPO =
  'https://github.com/Precise-Goals/iTantra-Communication-Ecosystem';
const APK_URL =
  'https://github.com/Precise-Goals/iTantra-Communication-Ecosystem/releases/download/android-app/iTantra.apk';

export default function Footer() {
  const location = useLocation();
  const isHome = location.pathname === '/';

  const resolveHref = (href) => {
    if (href.startsWith('#')) {
      return isHome ? href : `/${href}`;
    }
    return href;
  };

  const sitemapColumns = [
    {
      title: 'PRODUCTS',
      links: [
        { label: 'Voice Agents', href: '#manifesto' },
        { label: 'Content Agents', href: '#manifesto' },
        { label: 'Doc Agents', href: '#manifesto' },
        { label: 'Work Agents', href: '#app-preview' },
        { label: 'Mesh Transceiver', href: '#app-preview' },
        { label: 'Android Field App', href: APK_URL, external: true },
        { label: 'Model Training', href: '#metrics' },
      ],
    },
    {
      title: 'APIS',
      links: [
        { label: 'Text to Speech', href: '#languages' },
        { label: 'Speech to Text', href: '#languages' },
        { label: 'Doc Digitisation', href: '#manifesto' },
        { label: 'Translation', href: '#languages' },
        { label: 'P2P Radio Mesh', href: '#manifesto' },
        { label: 'Quantized Models', href: '#metrics' },
      ],
    },
    {
      title: 'RESOURCES',
      links: [
        { label: 'Research Paper', href: '/research-paper-article' },
        { label: 'Blogs & Manifesto', href: '#manifesto' },
        { label: 'Events & Demos', href: '#manifesto' },
        { label: 'Customer Stories', href: '#app-preview' },
        { label: 'Documentation', href: GITHUB_REPO, external: true },
        { label: 'API Pricing', href: '#metrics' },
        { label: 'Integrations', href: '#metrics' },
        { label: 'Startup Program', href: GITHUB_REPO, external: true },
        { label: 'iTantra Champions', href: '#manifesto' },
      ],
    },
    {
      title: 'COMPANY',
      links: [
        { label: 'About Us', href: '#manifesto' },
        { label: 'Careers', href: GITHUB_REPO, external: true },
        { label: 'Contact Us', href: 'mailto:contact@itantra.in' },
        { label: 'Brand', href: '#' },
        { label: 'Brand Guidelines', href: '#' },
        { label: 'Partnerships', href: 'mailto:partnerships@itantra.in' },
      ],
    },
    {
      title: 'LEGAL',
      links: [
        { label: 'Trust Center', href: '#metrics' },
        { label: 'Terms of Service', href: '#' },
        { label: 'Privacy Policy', href: '#' },
        { label: 'DPDP Compliance', href: '#' },
        { label: 'EULA & License', href: GITHUB_REPO, external: true },
        { label: 'GitHub Source', href: GITHUB_REPO, external: true },
      ],
    },
  ];

  return (
    <footer id="sitemap" className="sarvam-footer">
      <div className="sarvam-footer-inner">
        {/* Top Section: Brand Info + 5 Integrated Sitemap Columns */}
        <div className="sarvam-footer-top">
          {/* Left Column: Brand, Tagline, Certifications, Socials, Address */}
          <div className="sarvam-footer-brand-col">
            <Link to="/" className="sarvam-footer-logo">
              iTantra.
            </Link>
            <p className="sarvam-footer-tagline">AI for all from India</p>

            {/* Compliance Badges: ISO 27001 & AICPA SOC 2 TYPE 1 */}
            <div className="sarvam-badges-row">
              <div className="sarvam-badge" title="ISO 27001 Information Security Certified">
                <svg
                  className="badge-svg-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.5"
                >
                  <circle cx="12" cy="9" r="6" />
                  <path d="M9 14.5L7 21l5-2 5 2-2-6.5" />
                  <path d="M12 6.5l.7 1.5 1.6.2-1.2 1.1.3 1.6-1.4-.8-1.4.8.3-1.6-1.2-1.1 1.6-.2z" />
                </svg>
                <div className="badge-text-wrap">
                  <span className="badge-text-bold">ISO 27001</span>
                </div>
              </div>

              <div className="sarvam-badge" title="AICPA SOC 2 Type 1 Compliant">
                <svg
                  className="badge-svg-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.5"
                >
                  <path d="M12 3l7 3v6c0 5-3.5 9-7 10-3.5-1-7-5-7-10V6l7-3z" />
                  <path d="M9 12l2 2 4-4" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                <div className="badge-text-wrap">
                  <span className="badge-text-mini">AICPA</span>
                  <span className="badge-text-bold">SOC 2 TYPE 1</span>
                </div>
              </div>
            </div>

            {/* Find us at + Social Links */}
            <div className="sarvam-social-section">
              <p className="sarvam-social-label">Find us at</p>
              <div className="sarvam-social-icons">
                {/* LinkedIn */}
                <a
                  href="https://linkedin.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="sarvam-social-btn"
                  aria-label="LinkedIn"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M19 3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14m-.5 15.5v-5.3a3.26 3.26 0 0 0-3.26-3.26c-.85 0-1.84.52-2.28 1.3v-1.11h-2.79v8.37h2.79v-4.93c0-.77.62-1.4 1.39-1.4a1.4 1.4 0 0 1 1.4 1.4v4.93h2.75M6.46 8.76a1.4 1.4 0 1 0-.01-2.8 1.4 1.4 0 0 0 .01 2.8m1.4 9.74v-8.37H5.06v8.37z" />
                  </svg>
                </a>

                {/* X / Twitter */}
                <a
                  href="https://x.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="sarvam-social-btn"
                  aria-label="X (formerly Twitter)"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
                  </svg>
                </a>

                {/* YouTube */}
                <a
                  href="https://youtube.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="sarvam-social-btn"
                  aria-label="YouTube"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z" />
                  </svg>
                </a>

                {/* GitHub */}
                <a
                  href={GITHUB_REPO}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="sarvam-social-btn"
                  aria-label="GitHub"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
                  </svg>
                </a>

                {/* Discord */}
                <a
                  href="https://discord.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="sarvam-social-btn"
                  aria-label="Discord"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.929 1.793 8.18 1.793 12.061 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.893.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.028zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z" />
                  </svg>
                </a>

                {/* Instagram */}
                <a
                  href="https://instagram.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="sarvam-social-btn"
                  aria-label="Instagram"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838a6.162 6.162 0 1 0 0 12.324 6.162 6.162 0 0 0 0-12.324zm0 10.162a3.999 3.999 0 1 1 0-7.998 3.999 3.999 0 0 1 0 7.998zm6.406-11.845a1.44 1.44 0 1 0 0 2.881 1.44 1.44 0 0 0 0-2.881z" />
                  </svg>
                </a>
              </div>
            </div>

            {/* Address & Copyright Box */}
            <div className="sarvam-address-card">
              <p className="sarvam-card-copyright">
                © 2026 iTantra Unniversal Ecosystem. Made by Falcons, All rights reserved.
              </p>
              <p className="sarvam-card-address">
                Pune, India
              </p>
            </div>
          </div>

          {/* Right Columns: Integrated Sitemap */}
          <div className="sarvam-footer-sitemap-grid">
            {sitemapColumns.map((col, idx) => (
              <div key={idx} className="sarvam-sitemap-col">
                <h4 className="sarvam-col-title">{col.title}</h4>
                <ul className="sarvam-col-links">
                  {col.links.map((link, i) => {
                    const targetHref = resolveHref(link.href);
                    const isInternal =
                      targetHref.startsWith('/') &&
                      !link.external &&
                      !targetHref.startsWith('/#');

                    return (
                      <li key={i}>
                        {isInternal ? (
                          <Link to={targetHref} className="sarvam-link">
                            {link.label}
                          </Link>
                        ) : (
                          <a
                            href={targetHref}
                            target={link.external ? '_blank' : '_self'}
                            rel={link.external ? 'noopener noreferrer' : undefined}
                            className="sarvam-link"
                          >
                            {link.label}
                          </a>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))}
          </div>
        </div>

        {/* Bottom Section: Giant iTantra. display in Poppins with Indian Miniature Painting Art fill */}
        <motion.div
          className="sarvam-footer-bottom"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-20px' }}
          transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
        >
          <h1 className="sarvam-giant-wordmark">iTantra.</h1>
        </motion.div>
      </div>
    </footer>
  );
}
