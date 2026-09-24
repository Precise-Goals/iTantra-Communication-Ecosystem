import React, { useEffect, useRef } from 'react';

const screens = [
  { img: '/screenshots/screen-3.jpg', alt: 'Screen 3' }, // far-left
  { img: '/screenshots/screen-2.jpg', alt: 'Screen 2' }, // left
  { img: '/screenshots/screen-1.jpg', alt: 'Screen 1' }, // CENTER
  { img: '/screenshots/screen-4.jpg', alt: 'Screen 4' }, // right
  { img: '/screenshots/screen-5.jpg', alt: 'Screen 5' }, // far-right
];

export default function AppScreenshots() {
  const sectionRef = useRef(null);

  useEffect(() => {
    const section = sectionRef.current;
    if (!section) return;
    const obs = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          section.classList.add('fan-visible');
          obs.disconnect();
        }
      },
      { threshold: 0.1 }
    );
    obs.observe(section);
    return () => obs.disconnect();
  }, []);

  return (
    <section id="app-preview" className="fan-section" ref={sectionRef}>
      <div className="fan-stage">

        <div className="fan-phone fp-far-left" style={{ '--d': '0ms' }}>
          <div className="fan-frame">
            <img src={screens[0].img} alt={screens[0].alt} loading="lazy" />
          </div>
        </div>

        <div className="fan-phone fp-left" style={{ '--d': '80ms' }}>
          <div className="fan-frame">
            <img src={screens[1].img} alt={screens[1].alt} loading="lazy" />
          </div>
        </div>

        <div className="fan-phone fp-center" style={{ '--d': '160ms' }}>
          <div className="fan-frame">
            <img src={screens[2].img} alt={screens[2].alt} loading="lazy" />
          </div>
        </div>

        <div className="fan-phone fp-right" style={{ '--d': '80ms' }}>
          <div className="fan-frame">
            <img src={screens[3].img} alt={screens[3].alt} loading="lazy" />
          </div>
        </div>

        <div className="fan-phone fp-far-right" style={{ '--d': '0ms' }}>
          <div className="fan-frame">
            <img src={screens[4].img} alt={screens[4].alt} loading="lazy" />
          </div>
        </div>

      </div>
    </section>
  );
}
