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
import Footer           from './components/Footer';
import { PreHeroSection } from './components/PreHeroSection';

export default function App() {
  return (
    <div className="app">
      <Navbar />
      <PreHeroSection/>
      <HeroSection />
      <StatsStrip />
      <Marquee />
      <ManifestoSection />
      <AppScreenshots />
      <LanguagesSection />
      <Scorecard />
      <Footer />
    </div>
  );
}
