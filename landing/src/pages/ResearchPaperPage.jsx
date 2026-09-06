import React, { useEffect } from 'react';
import { motion } from 'framer-motion';

const PDF_PATH = '/research.pdf';

const PAGES = [
  { num: 1, src: '/research-paper/page-1.webp' },
  { num: 2, src: '/research-paper/page-2.webp' },
  { num: 3, src: '/research-paper/page-3.webp' },
  { num: 4, src: '/research-paper/page-4.webp' },
  { num: 5, src: '/research-paper/page-5.webp' },
  { num: 6, src: '/research-paper/page-6.webp' },
];

export default function ResearchPaperPage() {
  useEffect(() => {
    // Scroll to top on page mount
    window.scrollTo(0, 0);
  }, []);

  return (
    <div className="research-paper-wrapper">
      {/* Article Header Bar */}
      <div className="research-paper-header">
        <div className="research-header-container">
          <div className="research-header-info">
            <span className="research-badge">PUBLIC RESEARCH</span>
            <h1 className="research-title">
              iTantra: Research Paper & Technical Architecture
            </h1>
            <p className="research-subtitle">
              Low-bitrate neural Indic voice transceiver for infrastructure-independent tactical mesh networks.
            </p>
          </div>

          <div className="research-header-actions">
            <a
              href={PDF_PATH}
              target="_blank"
              rel="noopener noreferrer"
              className="research-action-btn primary"
              title="Open original PDF in a new browser tab"
            >
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                <polyline points="15 3 21 3 21 9" />
                <line x1="10" y1="14" x2="21" y2="3" />
              </svg>
              <span>Open in New Tab</span>
            </a>
          </div>
        </div>
      </div>

      {/* Article Document Layout — Continuous Scrollable Pages */}
      <motion.main
        className="research-article-feed"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: 'easeOut' }}
      >
        <div className="article-meta-strip">
          <span className="article-page-count">6 Pages • Complete Paper</span>
          <span className="article-meta-divider">•</span>
          <span className="article-meta-note">Scroll to read continuous article</span>
        </div>

        {PAGES.map((page) => (
          <article
            key={page.num}
            id={`paper-page-${page.num}`}
            className="research-page-card"
          >
            <div className="page-card-header">
              <span className="page-card-tag">Page {page.num} of {PAGES.length}</span>
            </div>

            <div className="page-card-media">
              <img
                src={page.src}
                alt={`iTantra Research Paper — Page ${page.num}`}
                className="page-card-image"
                loading={page.num <= 2 ? 'eager' : 'lazy'}
                decoding="async"
              />
            </div>
          </article>
        ))}
      </motion.main>
    </div>
  );
}
