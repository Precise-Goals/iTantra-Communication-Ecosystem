import React, { useEffect } from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';
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
import RangeBentoSection from './components/RangeBentoSection';
import ResearchPaperPage from './pages/ResearchPaperPage';

function LandingPage() {
  const location = useLocation();

  useEffect(() => {
    if (location.hash) {
      const elem = document.querySelector(location.hash);
      if (elem) {
        elem.scrollIntoView({ behavior: 'smooth' });
      }
    }
  }, [location]);

  return (
    <>
      <PreHeroSection />
      <HeroSection />
      <StatsStrip />
      <Marquee />
      <ManifestoSection />
      <AppScreenshots />
      <RangeBentoSection />
      <LanguagesSection />
      <Scorecard />
    </>
  );
}

export default function App() {
  return (
    <div className="app">
      <Navbar />
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/research-paper-article" element={<ResearchPaperPage />} />
        {/* Fallback for any unknown route */}
        <Route path="*" element={<LandingPage />} />
      </Routes>
      <Footer />
    </div>
  );
}
