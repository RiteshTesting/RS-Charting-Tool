import React, { useMemo } from 'react';
import { Compass, ArrowRight, TrendingUp, Layers } from 'lucide-react';
import { formatPct } from '../utils/formatters';

function getQuadrantStyle(quadrant) {
  switch (quadrant) {
    case 'Leading':
      return {
        badge: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border-emerald-500/40',
        dot: '#10B981',
        ring: 'rgba(16, 185, 129, 0.25)',
      };
    case 'Improving':
      return {
        badge: 'bg-cyan-500/15 text-cyan-600 dark:text-cyan-300 border-cyan-500/40',
        dot: '#06B6D4',
        ring: 'rgba(6, 182, 212, 0.25)',
      };
    case 'Weakening':
      return {
        badge: 'bg-amber-500/15 text-amber-600 dark:text-amber-300 border-amber-500/40',
        dot: '#F59E0B',
        ring: 'rgba(245, 158, 11, 0.25)',
      };
    default:
      return {
        badge: 'bg-rose-500/15 text-rose-600 dark:text-rose-300 border-rose-500/40',
        dot: '#EF4444',
        ring: 'rgba(239, 68, 68, 0.25)',
      };
  }
}

export default function SectorsTab({ sectors, theme = 'dark', onSelectSector }) {
  const isLight = theme === 'light';

  const bounds = useMemo(() => {
    const maxDx = Math.max(
      6,
      ...sectors.map((s) => Math.abs(s.rsRatio - 100) + 1.5)
    );
    const maxDy = Math.max(
      5,
      ...sectors.map((s) => Math.abs(s.rsMomentum - 100) + 1.5)
    );
    return { maxDx, maxDy };
  }, [sectors]);

  const W = 340;
  const H = 250;
  const PAD = 20;
  const cx = W / 2;
  const cy = H / 2;

  const toX = (rsRatio) => cx + ((rsRatio - 100) / bounds.maxDx) * (W / 2 - PAD);
  const toY = (rsMom) => cy - ((rsMom - 100) / bounds.maxDy) * (H / 2 - PAD);

  return (
    <div className="flex flex-col gap-3 p-3 pb-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-bold text-[var(--text-primary)] flex items-center gap-1.5">
            <Compass className="w-4 h-4 text-emerald-500" />
            NSE Sector Rotation (RRG)
          </h2>
          <p className="text-[11px] text-[var(--text-muted)]">
            RS-Ratio vs RS-Momentum • Tap any sector to screen stocks
          </p>
        </div>
      </div>

      {/* 4-Quadrant RRG Scatter View */}
      <section
        aria-label="Relative Rotation Graph 4-Quadrant View"
        className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-2.5 overflow-hidden"
      >
        <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto block select-none">
          <rect x={cx} y={0} width={W / 2} height={cy} fill="rgba(16, 185, 129, 0.08)" />
          <rect x={0} y={0} width={W / 2} height={cy} fill="rgba(6, 182, 212, 0.08)" />
          <rect x={cx} y={cy} width={W / 2} height={H / 2} fill="rgba(245, 158, 11, 0.08)" />
          <rect x={0} y={cy} width={W / 2} height={H / 2} fill="rgba(239, 68, 68, 0.08)" />

          <line
            x1={cx}
            y1={0}
            x2={cx}
            y2={H}
            stroke={isLight ? '#CBD5E1' : '#2E3749'}
            strokeDasharray="4 3"
            strokeWidth="1"
          />
          <line
            x1={0}
            y1={cy}
            x2={W}
            y2={cy}
            stroke={isLight ? '#CBD5E1' : '#2E3749'}
            strokeDasharray="4 3"
            strokeWidth="1"
          />

          <text x={8} y={14} fill={isLight ? '#0891B2' : '#22D3EE'} fontSize="9" fontWeight="700">
            IMPROVING
          </text>
          <text x={W - 8} y={14} textAnchor="end" fill={isLight ? '#059669' : '#10B981'} fontSize="9" fontWeight="700">
            LEADING
          </text>
          <text x={8} y={H - 8} fill={isLight ? '#DC2626' : '#EF4444'} fontSize="9" fontWeight="700">
            LAGGING
          </text>
          <text x={W - 8} y={H - 8} textAnchor="end" fill={isLight ? '#D97706' : '#F59E0B'} fontSize="9" fontWeight="700">
            WEAKENING
          </text>

          <text x={cx + 3} y={H - 5} fill="#64748B" fontSize="8">
            RS-Ratio →
          </text>
          <text x={4} y={cy - 4} fill="#64748B" fontSize="8">
            RS-Mom ↑
          </text>

          {sectors.map((sec, idx) => {
            const x = Math.max(24, Math.min(W - 24, toX(sec.rsRatio)));
            const y = Math.max(22, Math.min(H - 22, toY(sec.rsMomentum)));
            const style = getQuadrantStyle(sec.quadrant);
            const radius = 5 + Math.min(4, sec.stockCount);
            const labelOffsetY = idx % 2 === 0 ? -10 : 14;

            return (
              <g
                key={sec.sector}
                onClick={() => onSelectSector(sec.sector)}
                className="cursor-pointer"
              >
                <circle cx={x} cy={y} r={radius + 4} fill={style.ring} />
                <circle
                  cx={x}
                  cy={y}
                  r={radius}
                  fill={style.dot}
                  stroke={isLight ? '#FFFFFF' : '#0B0E14'}
                  strokeWidth="1.5"
                />
                <text
                  x={x}
                  y={y + labelOffsetY}
                  textAnchor="middle"
                  fill={isLight ? '#0F172A' : '#E2E8F0'}
                  fontSize="8.5"
                  fontWeight="700"
                >
                  {sec.sector}
                </text>
              </g>
            );
          })}
        </svg>

        <div className="mt-2 pt-2 border-t border-[var(--border-subtle)] grid grid-cols-4 gap-1 text-center text-[10px]">
          <div className="text-emerald-500 font-semibold">
            Leading ({sectors.filter((s) => s.quadrant === 'Leading').length})
          </div>
          <div className="text-cyan-600 dark:text-cyan-400 font-semibold">
            Improving ({sectors.filter((s) => s.quadrant === 'Improving').length})
          </div>
          <div className="text-amber-600 dark:text-amber-400 font-semibold">
            Weakening ({sectors.filter((s) => s.quadrant === 'Weakening').length})
          </div>
          <div className="text-rose-500 font-semibold">
            Lagging ({sectors.filter((s) => s.quadrant === 'Lagging').length})
          </div>
        </div>
      </section>

      <div className="flex items-center justify-between mt-1">
        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--text-muted)] flex items-center gap-1.5">
          <Layers className="w-3.5 h-3.5 text-emerald-500" />
          Sector Strength Leaderboard
        </h3>
        <span className="text-[10px] text-[var(--text-muted)]">Ranked by Median RS</span>
      </div>

      <div className="flex flex-col gap-2">
        {sectors.map((sec, idx) => {
          const style = getQuadrantStyle(sec.quadrant);
          const isPos = sec.avgChange >= 0;

          return (
            <button
              key={sec.sector}
              type="button"
              onClick={() => onSelectSector(sec.sector)}
              className="w-full text-left rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] hover:border-slate-400 dark:hover:border-slate-600 active:bg-[var(--bg-elevated)] p-3 transition"
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="w-6 h-6 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-[11px] font-bold tabular-nums text-[var(--text-secondary)] flex items-center justify-center shrink-0">
                    {idx + 1}
                  </span>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-bold text-[var(--text-primary)]">{sec.displayName}</span>
                      <span className={`px-2 py-0.5 rounded-md text-[10px] font-semibold border ${style.badge}`}>
                        {sec.quadrant}
                      </span>
                    </div>
                    <div className="mt-0.5 text-[11px] text-[var(--text-muted)] flex items-center gap-2">
                      <span>{sec.stockCount} stocks</span>
                      <span>•</span>
                      <span>
                        Top: <strong className="text-[var(--text-primary)]">{sec.topStock}</strong>
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <div className="text-right">
                    <div className="text-xs font-bold tabular-nums text-[var(--text-primary)]">
                      RS {sec.medianRs}
                    </div>
                    <div
                      className={`text-[11px] font-semibold tabular-nums flex items-center justify-end gap-0.5 ${
                        isPos ? 'text-emerald-500' : 'text-rose-500'
                      }`}
                    >
                      <TrendingUp className={`w-3 h-3 ${!isPos ? 'rotate-180' : ''}`} />
                      {formatPct(sec.avgChange)}
                    </div>
                  </div>
                  <ArrowRight className="w-4 h-4 text-[var(--text-muted)]" />
                </div>
              </div>

              <div className="mt-2.5 flex items-center gap-2.5">
                <span className="text-[10px] text-[var(--text-muted)] shrink-0 w-24">
                  Stage 2 Uptrend
                </span>
                <div className="flex-1 h-1.5 rounded-full bg-[var(--bg-input)] overflow-hidden">
                  <div
                    className={`h-full rounded-full ${
                      sec.stage2Pct >= 65
                        ? 'bg-emerald-500'
                        : sec.stage2Pct >= 40
                        ? 'bg-amber-500'
                        : 'bg-rose-500'
                    }`}
                    style={{ width: `${sec.stage2Pct}%` }}
                  />
                </div>
                <span className="text-[11px] font-bold tabular-nums text-[var(--text-primary)] w-9 text-right">
                  {sec.stage2Pct}%
                </span>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
