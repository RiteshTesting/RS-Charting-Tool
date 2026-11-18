import React, { useEffect, useState } from 'react';
import RsLogo from './RsLogo';

export default function SplashScreen({ onFinish }) {
  const [fadingOut, setFadingOut] = useState(false);

  useEffect(() => {
    const fadeTimer = setTimeout(() => {
      setFadingOut(true);
    }, 1150);

    const doneTimer = setTimeout(() => {
      onFinish();
    }, 1450);

    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(doneTimer);
    };
  }, [onFinish]);

  return (
    <div
      role="status"
      aria-label="Loading RS Charting Tool"
      onClick={() => {
        setFadingOut(true);
        setTimeout(onFinish, 180);
      }}
      className={`fixed inset-0 z-[100] bg-[#0B0E14] flex flex-col items-center justify-center px-6 select-none transition-opacity duration-300 ${
        fadingOut ? 'opacity-0 pointer-events-none' : 'opacity-100'
      }`}
    >
      {/* Subtle Emerald Radial Backdrop Glow */}
      <div
        aria-hidden="true"
        className="w-28 h-28 rounded-3xl flex items-center justify-center shadow-[0_0_40px_rgba(16,185,129,0.22)]"
      >
        <RsLogo className="w-28 h-28" animated />
      </div>

      {/* 0.5s – 1.0s Title, Subtitle & Animated Loading Bar */}
      <div className="mt-5 flex flex-col items-center text-center rs-splash-text-in">
        <h1 className="text-xl font-extrabold tracking-tight text-[#F8FAFC]">
          RS Charting Tool
        </h1>
        <p className="mt-1 text-xs font-medium text-slate-400 tracking-wide">
          v1.0 • EOD Swing &amp; Momentum Scanner
        </p>

        <div className="mt-5 w-44 h-1 rounded-full bg-[#151A23] border border-[#232A38] overflow-hidden">
          <div className="h-full rounded-full bg-gradient-to-r from-emerald-600 via-emerald-400 to-cyan-400 rs-loading-bar" />
        </div>
      </div>
    </div>
  );
}
