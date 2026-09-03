import React, { useEffect, useRef } from 'react';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

const STATS = [
  { end: 10,    suffix: '',      label: 'INDIAN LANGUAGES' },
  { end: 12,    suffix: ' MB',   label: 'PER STT MODEL', red: true },
  { end: 0,     suffix: '',      label: 'INTERNET REQUIRED', red: true },
  { end: 240,   suffix: ' ms',   label: 'VOICE TO VOICE' },
];

export default function StatsStrip() {
  const numRefs = useRef([]);

  useEffect(() => {
    numRefs.current.forEach((el, i) => {
      if (!el) return;
      const stat = STATS[i];
      const obj = { val: 0 };

      gsap.fromTo(
        obj,
        { val: 0 },
        {
          val: stat.end,
          duration: 1.8,
          ease: 'power2.out',
          scrollTrigger: {
            trigger: el,
            start: 'top 88%',
            once: true,
          },
          onUpdate: () => {
            el.textContent =
              (stat.end < 1 ? '' : stat.end === 0 ? '0' : Math.round(obj.val)) +
              (i === 1 ? '<' : i === 0 ? '' : i === 3 ? '≈' : '') + stat.suffix;
          },
          onComplete: () => {
            // Set final display value
            if (i === 0) el.textContent = '10';
            else if (i === 1) el.textContent = '<12 MB';
            else if (i === 2) el.textContent = '0';
            else if (i === 3) el.textContent = '≈240 ms';
          },
        }
      );
    });
    return () => ScrollTrigger.getAll().forEach(t => t.kill());
  }, []);

  return (
    <div className="stats-strip">
      {STATS.map((s, i) => (
        <div className="stat-item" key={i}>
          <div
            className="stat-val"
            ref={el => (numRefs.current[i] = el)}
          >
            {i === 1 ? '<12 MB' : i === 3 ? '≈240 ms' : String(s.end)}
          </div>
          <div className={`stat-label${s.red ? ' stat-red' : ''}`}>{s.label}</div>
        </div>
      ))}
    </div>
  );
}
