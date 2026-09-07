import React, { useEffect, useRef } from 'react';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

const STATS = [
  { end: 200,  label: 'VERIFIED 2-PHONE BASE', display: '200 m' },
  { end: 2500, label: '10-NODE FLOOD MESH',   display: '2.5 km' },
  { end: 10,   label: 'INDIAN LANGUAGES',      display: '10' },
  { end: 12,   label: 'PER STT MODEL',         display: '<12 MB', red: true },
  { end: 0,    label: 'INTERNET REQUIRED',     display: '0',      red: true },
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
          duration: 1.6,
          ease: 'power2.out',
          scrollTrigger: {
            trigger: el,
            start: 'top 88%',
            once: true,
          },
          onUpdate: () => {
            if (i === 0) {
              el.textContent = Math.round(obj.val) + ' m';
            } else if (i === 1) {
              const km = (obj.val / 1000).toFixed(1);
              el.textContent = km + ' km';
            } else if (i === 2) {
              el.textContent = String(Math.round(obj.val));
            } else if (i === 3) {
              el.textContent = '<' + Math.round(obj.val) + ' MB';
            } else if (i === 4) {
              el.textContent = '0';
            }
          },
          onComplete: () => {
            el.textContent = stat.display;
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
            {s.display}
          </div>
          <div className={`stat-label${s.red ? ' stat-red' : ''}`}>{s.label}</div>
        </div>
      ))}
    </div>
  );
}
