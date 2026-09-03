import React from 'react';
import './App.css';

import Navbar           from './components/Navbar';
import HeroSection      from './components/HeroSection';
import StatsStrip       from './components/StatsStrip';
import Marquee          from './components/Marquee';
import ManifestoSection from './components/ManifestoSection';
import AppScreenshots   from './components/AppScreenshots';
import LanguagesSection from './components/LanguagesSection';
import Scorecard        from './components/Scorecard';
import SitemapSection   from './components/SitemapSection';
import Footer           from './components/Footer';

export default function App() {
  return (
    <div className="app">
      <Navbar />
      <HeroSection />
      <StatsStrip />
      <Marquee />
      <ManifestoSection />
      <AppScreenshots />
      <LanguagesSection />
      <Scorecard />
      <SitemapSection />
      <Footer />
    </div>
  );
}
