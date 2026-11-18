import React, { useId } from 'react';

export default function RsLogo({
  className = 'w-8 h-8',
  animated = false,
  ariaHidden = true,
}) {
  const uid = useId().replace(/:/g, '');
  const ambientId = `rs-ambient-${uid}`;
  const borderId = `rs-border-${uid}`;
  const trendId = `rs-trend-${uid}`;

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 128 128"
      fill="none"
      aria-hidden={ariaHidden}
      className={className}
    >
      <defs>
        <radialGradient id={ambientId} cx="78%" cy="24%" r="65%">
          <stop offset="0%" stopColor="#10B981" stopOpacity="0.3" />
          <stop offset="100%" stopColor="#0F172A" stopOpacity="0" />
        </radialGradient>
        <linearGradient id={borderId} x1="8" y1="8" x2="120" y2="120" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#34D399" stopOpacity="0.88" />
          <stop offset="55%" stopColor="#10B981" stopOpacity="0.45" />
          <stop offset="100%" stopColor="#059669" stopOpacity="0.72" />
        </linearGradient>
        <linearGradient id={trendId} x1="20" y1="99" x2="104" y2="32" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#059669" />
          <stop offset="50%" stopColor="#10B981" />
          <stop offset="100%" stopColor="#34D399" />
        </linearGradient>
      </defs>

      <rect x="4" y="4" width="120" height="120" rx="28" fill="#0F172A" />
      <rect x="4" y="4" width="120" height="120" rx="28" fill={`url(#${ambientId})`} />
      <rect
        x="5.25"
        y="5.25"
        width="117.5"
        height="117.5"
        rx="26.75"
        stroke={`url(#${borderId})`}
        strokeWidth="2.5"
      />

      {/* Stylized Upward Emerald Candlesticks */}
      <g className={animated ? 'rs-candles-in' : undefined}>
        <line x1="28" y1="83" x2="28" y2="107" stroke="#059669" strokeWidth="2.2" strokeLinecap="round" />
        <rect x="23.5" y="88.5" width="9" height="12.5" rx="2" fill="#059669" />

        <line x1="48" y1="76" x2="48" y2="103" stroke="#10B981" strokeWidth="2.2" strokeLinecap="round" />
        <rect x="43.5" y="81.5" width="9" height="14" rx="2" fill="#10B981" />

        <line x1="68" y1="67" x2="68" y2="96" stroke="#34D399" strokeWidth="2.2" strokeLinecap="round" />
        <rect x="63.5" y="73" width="9" height="15" rx="2" fill="#34D399" />
      </g>

      {/* Bold RS Monogram */}
      <g className={animated ? 'rs-monogram-scale' : undefined}>
        <path
          d="M 23 68 L 23 26 L 39 26 C 46.5 26 50.5 30.2 50.5 36.8 C 50.5 43.2 46.5 47.4 39 47.4 L 23 47.4 M 38 47.4 L 50.5 68"
          stroke="#F8FAFC"
          strokeWidth="8.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M 84 31.5 C 81.5 27.2 77 25.8 71.5 25.8 C 64 25.8 59.5 29.8 59.5 35.6 C 59.5 41.8 64.5 44.2 72.5 46.2 C 81 48.2 85.5 51.2 85.5 57.8 C 85.5 64 80 68.2 71.5 68.2 C 65 68.2 60.5 66.2 58 61.8"
          stroke="#F8FAFC"
          strokeWidth="8.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </g>

      {/* Emerald Momentum Trendline & Arrow */}
      <path
        d="M 20 99 L 44 84 L 62 91 L 102 34"
        pathLength="100"
        stroke={`url(#${trendId})`}
        strokeWidth="5.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className={animated ? 'rs-trend-draw' : undefined}
      />
      <path
        d="M 90 36.5 L 103.5 32 L 103 45.5"
        pathLength="100"
        stroke="#34D399"
        strokeWidth="5.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className={animated ? 'rs-arrow-draw' : undefined}
      />
    </svg>
  );
}
