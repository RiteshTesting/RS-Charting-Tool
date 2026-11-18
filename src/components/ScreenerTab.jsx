import React, { useMemo, useState } from 'react';
import {
  Search,
  Star,
  ArrowUpDown,
  TrendingUp,
  TrendingDown,
  X,
  Activity,
  Flame,
  Zap,
  Award,
  Layers,
  ChevronRight,
  SlidersHorizontal,
  ChevronDown,
  ChevronUp,
  RotateCcw,
} from 'lucide-react';
import { formatINR, formatPct } from '../utils/formatters';

export const SCAN_FILTERS = [
  { id: 'allLiquid', label: 'All Liquid NSE', shortDesc: 'Min ₹10 Cr 20D Turnover' },
  { id: 'near52WHigh', label: '52W High / BO', shortDesc: 'Within 2% of 52W High' },
  { id: 'pullback45Ema', label: 'RSI + 45 EMA Pullback', shortDesc: 'WRSI>50, RSI 50–60, 45 EMA Reclaim' },
  { id: 'hmBreakout', label: 'Hilega Milega BO', shortDesc: '3 EMA(RSI9) × 21 WMA + >20 EMA' },
  { id: 'hmSweetSpot', label: 'HM Sweet Spot', shortDesc: 'RSI(9) 50–70, 3EMA>21WMA, >20EMA' },
  { id: 'bbSqueeze', label: 'BB Squeeze', shortDesc: 'Lowest 20D Bollinger Bandwidth' },
  { id: 'bbUpperBreakout', label: 'BB Upper BO', shortDesc: 'Close > Upper BB(20,2) + Vol' },
  { id: 'ema9_20BullCross', label: '9/20 EMA Cross', shortDesc: '9 EMA Bullish Cross > 20 EMA' },
  { id: 'goldenCross50_200', label: '50/200 Golden Cross', shortDesc: '50 SMA > 200 SMA' },
  { id: 'multiYearBreakout', label: 'Multi-Year BO', shortDesc: '52W High + >1.5x Vol' },
  { id: 'rsLeader', label: 'RS Leaders (85+)', shortDesc: 'Relative Strength 85–99' },
  { id: 'vcpPocketPivot', label: 'VCP / Pocket Pivot', shortDesc: 'Volatility Contraction + Vol Pivot' },
];

const SORT_OPTIONS = [
  { id: 'rsRating', label: 'RS Rating (1–99)' },
  { id: 'changePct', label: '% Change' },
  { id: 'volMultiplier', label: 'Volume Ratio' },
  { id: 'dailyRsi', label: 'Daily RSI(14)' },
  { id: 'rsi9', label: 'Hilega RSI(9)' },
];

export const INITIAL_CUSTOM_FILTERS = {
  high52W: 'any', // any | near2 | breakout
  dailyRsiZone: 'any', // any | gt50 | zone50_60 | gt60 | lt30
  weeklyRsiGt50: false,
  smaChecks: [], // '20' | '50' | '100' | '200' | 'golden'
  emaChecks: [], // '9' | '20' | '45' | '200' | 'cross9_20'
  bbSignal: 'any', // any | upperBO | squeeze | midBounce
  hilegaSignal: 'any', // any | breakout | sweetSpot
  rsMin: 'any', // any | 80 | 90
  volMin: 'any', // any | 1.5 | 2.0
};

function countActiveCustomFilters(cf) {
  let count = 0;
  if (cf.high52W !== 'any') count++;
  if (cf.dailyRsiZone !== 'any') count++;
  if (cf.weeklyRsiGt50) count++;
  count += cf.smaChecks.length;
  count += cf.emaChecks.length;
  if (cf.bbSignal !== 'any') count++;
  if (cf.hilegaSignal !== 'any') count++;
  if (cf.rsMin !== 'any') count++;
  if (cf.volMin !== 'any') count++;
  return count;
}

function matchesCustomFilters(stock, cf) {
  const m = stock.metrics;
  const s = stock.setups;

  if (cf.high52W === 'near2' && !s.near52WHigh) return false;
  if (cf.high52W === 'breakout' && !s.multiYearBreakout) return false;

  if (cf.dailyRsiZone === 'gt50' && m.dailyRsi <= 50) return false;
  if (cf.dailyRsiZone === 'zone50_60' && (m.dailyRsi < 50 || m.dailyRsi > 60)) return false;
  if (cf.dailyRsiZone === 'gt60' && m.dailyRsi <= 60) return false;
  if (cf.dailyRsiZone === 'lt30' && m.dailyRsi >= 30) return false;
  if (cf.weeklyRsiGt50 && m.weeklyRsi <= 50) return false;

  for (const key of cf.smaChecks) {
    if (key === '20' && !s.aboveSma20) return false;
    if (key === '50' && !s.aboveSma50) return false;
    if (key === '100' && !s.aboveSma100) return false;
    if (key === '200' && !s.aboveSma200) return false;
    if (key === 'golden' && !s.goldenCross50_200) return false;
  }

  for (const key of cf.emaChecks) {
    if (key === '9' && !s.aboveEma9) return false;
    if (key === '20' && !s.aboveEma20) return false;
    if (key === '45' && !s.aboveEma45) return false;
    if (key === '200' && !s.aboveEma200) return false;
    if (key === 'cross9_20' && !s.ema9_20BullCross) return false;
  }

  if (cf.bbSignal === 'upperBO' && !s.bbUpperBreakout) return false;
  if (cf.bbSignal === 'squeeze' && !s.bbSqueeze) return false;
  if (cf.bbSignal === 'midBounce' && !s.bbMiddleBounce) return false;

  if (cf.hilegaSignal === 'breakout' && !s.hmBreakout) return false;
  if (cf.hilegaSignal === 'sweetSpot' && !s.hmSweetSpot) return false;

  if (cf.rsMin === '80' && m.rsRating < 80) return false;
  if (cf.rsMin === '90' && m.rsRating < 90) return false;

  if (cf.volMin === '1.5' && m.volMultiplier < 1.5) return false;
  if (cf.volMin === '2.0' && m.volMultiplier < 2.0) return false;

  return true;
}

function getRsBadgeStyle(rs) {
  if (rs >= 80) {
    return 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/35';
  }
  if (rs >= 60) {
    return 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/35';
  }
  return 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/35';
}

function getSetupBadgeStyle(badge) {
  switch (badge) {
    case '52W Breakout':
      return 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/30';
    case '45 EMA Pullback':
      return 'bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30';
    case 'Hilega Milega':
      return 'bg-sky-500/15 text-sky-700 dark:text-sky-300 border-sky-500/30';
    case 'VCP Pocket Pivot':
    case 'BB Squeeze':
      return 'bg-cyan-500/15 text-cyan-700 dark:text-cyan-300 border-cyan-500/30';
    case 'RS Leader':
      return 'bg-purple-500/15 text-purple-700 dark:text-purple-300 border-purple-500/30';
    default:
      return 'bg-[var(--bg-input)] text-[var(--text-secondary)] border-[var(--border-subtle)]';
  }
}

export default function ScreenerTab({
  stocks,
  breadth,
  activeScan,
  onSelectScan,
  activeSector,
  onClearSector,
  searchQuery,
  onSearchChange,
  sortBy,
  onSortByChange,
  sortDir,
  onToggleSortDir,
  watchlist,
  onToggleWatchlist,
  onOpenChart,
}) {
  const [builderOpen, setBuilderOpen] = useState(false);
  const [customFilters, setCustomFilters] = useState(INITIAL_CUSTOM_FILTERS);

  const activeCustomCount = useMemo(
    () => countActiveCustomFilters(customFilters),
    [customFilters]
  );

  const scanCounts = useMemo(() => {
    const counts = {};
    SCAN_FILTERS.forEach((f) => {
      counts[f.id] = stocks.filter((s) => s.setups[f.id]).length;
    });
    return counts;
  }, [stocks]);

  const filteredStocks = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return stocks
      .filter((s) => {
        if (!s.setups[activeScan]) return false;
        if (!matchesCustomFilters(s, customFilters)) return false;
        if (activeSector && s.sector !== activeSector) return false;
        if (q) {
          const matchSym = s.symbol.toLowerCase().includes(q);
          const matchName = s.name.toLowerCase().includes(q);
          const matchSector = s.sector.toLowerCase().includes(q);
          if (!matchSym && !matchName && !matchSector) return false;
        }
        return true;
      })
      .sort((a, b) => {
        const valA = a.metrics[sortBy] ?? 0;
        const valB = b.metrics[sortBy] ?? 0;
        return sortDir === 'desc' ? valB - valA : valA - valB;
      });
  }, [stocks, activeScan, customFilters, activeSector, searchQuery, sortBy, sortDir]);

  const toggleArrayFilter = (field, value) => {
    setCustomFilters((prev) => {
      const list = prev[field];
      const next = list.includes(value)
        ? list.filter((item) => item !== value)
        : [...list, value];
      return { ...prev, [field]: next };
    });
  };

  const resetAllFilters = () => {
    onSelectScan('allLiquid');
    setCustomFilters(INITIAL_CUSTOM_FILTERS);
    onClearSector();
    onSearchChange('');
  };

  const activeScanMeta = SCAN_FILTERS.find((f) => f.id === activeScan);

  const activeListLabel = useMemo(() => {
    const shortScanMap = {
      allLiquid: 'All Liquid',
      near52WHigh: '52W High',
      pullback45Ema: 'RSI+45EMA',
      hmBreakout: 'Hilega Milega',
      hmSweetSpot: 'HM Sweet Spot',
      bbSqueeze: 'BB Squeeze',
      bbUpperBreakout: 'BB Upper BO',
      ema9_20BullCross: '9/20 Cross',
      goldenCross50_200: 'Golden Cross',
      multiYearBreakout: 'Multi-Year BO',
      rsLeader: 'RS 85+',
      vcpPocketPivot: 'VCP / PP',
    };
    const scanLabel =
      activeScan === 'allLiquid' && activeCustomCount > 0
        ? 'Custom Scan'
        : shortScanMap[activeScan] || activeScanMeta?.label || 'Screener';
    if (activeSector) {
      return activeScan !== 'allLiquid' ? `${activeSector} • ${scanLabel}` : activeSector;
    }
    if (activeCustomCount > 0 && activeScan !== 'allLiquid') {
      return `${scanLabel} +${activeCustomCount}`;
    }
    return scanLabel;
  }, [activeScan, activeScanMeta, activeSector, activeCustomCount]);

  return (
    <div className="flex flex-col gap-3 pb-4">
      {/* Market Breadth Banner */}
      <section
        aria-label="Market Breadth"
        className="mx-3 mt-3 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3 shadow-sm"
      >
        <div className="grid grid-cols-3 gap-2 divide-x divide-[var(--border-subtle)]">
          <div className="pr-2">
            <div className="flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)]">
              <Activity className="w-3 h-3 text-emerald-500" />
              NIFTY 50
            </div>
            <div className="mt-0.5 flex items-baseline gap-1.5">
              <span className="text-sm font-bold tabular-nums text-[var(--text-primary)]">
                {breadth.niftyClose.toLocaleString('en-IN')}
              </span>
              <span className="text-[11px] font-semibold tabular-nums text-emerald-500">
                +{breadth.niftyChangePct}%
              </span>
            </div>
            <div
              className={`mt-0.5 text-[10px] font-medium truncate ${
                breadth.niftyBullish ? 'text-emerald-500' : 'text-amber-500'
              }`}
            >
              {breadth.niftyTrend}
            </div>
          </div>

          <div className="px-2">
            <div className="text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)]">
              &gt; 50 EMA
            </div>
            <div className="mt-0.5 flex items-baseline gap-1">
              <span className="text-sm font-bold tabular-nums text-emerald-500">
                {breadth.pctAbove50Ema}%
              </span>
              <span className="text-[10px] text-[var(--text-muted)]">stocks</span>
            </div>
            <div className="mt-1.5 h-1.5 w-full rounded-full bg-[var(--bg-input)] overflow-hidden">
              <div
                className="h-full rounded-full bg-emerald-500 transition-all duration-300"
                style={{ width: `${breadth.pctAbove50Ema}%` }}
              />
            </div>
          </div>

          <div className="pl-2">
            <div className="text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)]">
              Adv / Dec
            </div>
            <div className="mt-0.5 text-sm font-bold tabular-nums text-[var(--text-primary)]">
              {breadth.advDecRatio}
              <span className="text-[10px] font-normal text-[var(--text-muted)] ml-1">ratio</span>
            </div>
            <div className="mt-0.5 flex items-center gap-1.5 text-[10px] tabular-nums">
              <span className="text-emerald-500 font-medium">{breadth.advCount} Adv</span>
              <span className="text-[var(--text-muted)]">•</span>
              <span className="text-rose-500 font-medium">{breadth.decCount} Dec</span>
            </div>
          </div>
        </div>
      </section>

      {/* Popular Scanner Presets (Horizontal Scrollable Pills) */}
      <div className="flex flex-col gap-1.5">
        <div className="flex gap-2 overflow-x-auto no-scrollbar px-3 py-0.5">
          {SCAN_FILTERS.map((filter) => {
            const active = activeScan === filter.id;
            return (
              <button
                key={filter.id}
                type="button"
                onClick={() => onSelectScan(filter.id)}
                className={`shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-full text-xs font-semibold border transition-all min-h-[38px] ${
                  active
                    ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/50 shadow-sm'
                    : 'bg-[var(--bg-card)] text-[var(--text-secondary)] border-[var(--border-subtle)] hover:border-slate-400 dark:hover:border-slate-600'
                }`}
              >
                {filter.id === 'allLiquid' && <Layers className="w-3.5 h-3.5" />}
                {filter.id === 'pullback45Ema' && <Zap className="w-3.5 h-3.5 text-amber-500" />}
                {(filter.id === 'multiYearBreakout' || filter.id === 'near52WHigh') && (
                  <Flame className="w-3.5 h-3.5 text-emerald-500" />
                )}
                {filter.id === 'rsLeader' && <Award className="w-3.5 h-3.5 text-purple-500" />}
                {(filter.id === 'vcpPocketPivot' ||
                  filter.id === 'hmBreakout' ||
                  filter.id === 'hmSweetSpot' ||
                  filter.id === 'bbSqueeze' ||
                  filter.id === 'bbUpperBreakout') && (
                  <Activity className="w-3.5 h-3.5 text-cyan-500" />
                )}
                <span>{filter.label}</span>
                <span
                  className={`px-1.5 py-0.5 rounded-full text-[10px] tabular-nums ${
                    active
                      ? 'bg-emerald-500/25 text-emerald-800 dark:text-emerald-200'
                      : 'bg-[var(--bg-input)] text-[var(--text-muted)]'
                  }`}
                >
                  {scanCounts[filter.id]}
                </span>
              </button>
            );
          })}
        </div>

        {/* Rule Helper + Filter Builder Toggle Button */}
        <div className="px-3 flex items-center justify-between gap-2 flex-wrap">
          <p className="text-[11px] text-[var(--text-muted)]">
            Preset: <span className="text-[var(--text-secondary)]">{activeScanMeta?.shortDesc}</span>
          </p>

          <div className="flex items-center gap-1.5">
            {activeSector && (
              <button
                type="button"
                onClick={onClearSector}
                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-cyan-500/15 border border-cyan-500/40 text-cyan-700 dark:text-cyan-300 text-[11px] font-semibold"
              >
                <span>{activeSector}</span>
                <X className="w-3 h-3" />
              </button>
            )}

            <button
              type="button"
              onClick={() => setBuilderOpen((o) => !o)}
              aria-expanded={builderOpen}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[11px] font-bold border transition ${
                builderOpen || activeCustomCount > 0
                  ? 'bg-cyan-500/15 border-cyan-500/50 text-cyan-700 dark:text-cyan-300'
                  : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-primary)]'
              }`}
            >
              <SlidersHorizontal className="w-3.5 h-3.5" />
              <span>
                Filter Builder{activeCustomCount > 0 ? ` (${activeCustomCount})` : ''}
              </span>
              {builderOpen ? (
                <ChevronUp className="w-3.5 h-3.5" />
              ) : (
                <ChevronDown className="w-3.5 h-3.5" />
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Expandable Multi-Parameter Filter Builder Drawer */}
      {builderOpen && (
        <section
          aria-label="Multi-Parameter Filter Builder"
          className="mx-3 rounded-xl bg-[var(--bg-card)] border border-cyan-500/40 p-3 flex flex-col gap-3 shadow-sm"
        >
          <div className="flex items-center justify-between border-b border-[var(--border-subtle)] pb-2">
            <div>
              <h3 className="text-xs font-bold text-[var(--text-primary)]">
                Combine Swing Parameters (AND Logic)
              </h3>
              <p className="text-[10px] text-[var(--text-muted)]">
                Live matches:{' '}
                <strong className="text-emerald-500 tabular-nums">{filteredStocks.length}</strong>{' '}
                stocks
              </p>
            </div>
            <button
              type="button"
              onClick={() => setCustomFilters(INITIAL_CUSTOM_FILTERS)}
              className="px-2.5 py-1 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-[10px] font-semibold text-[var(--text-secondary)] flex items-center gap-1"
            >
              <RotateCcw className="w-3 h-3" />
              Reset
            </button>
          </div>

          {/* 1. 52-Week High / Breakout */}
          <div>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)] mb-1.5">
              1. 52-Week High / Breakout
            </span>
            <div className="flex flex-wrap gap-1.5">
              {[
                { id: 'any', label: 'Any' },
                { id: 'near2', label: 'Within 2% of 52W High' },
                { id: 'breakout', label: '52W Breakout (>1.5x Vol)' },
              ].map((opt) => (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => setCustomFilters((p) => ({ ...p, high52W: opt.id }))}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                    customFilters.high52W === opt.id
                      ? 'bg-emerald-500/20 border-emerald-500 text-emerald-700 dark:text-emerald-300'
                      : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-secondary)]'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 2. RSI Above 50 & Momentum Zone */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
                2. RSI(14) &amp; Momentum Zone
              </span>
              <label className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-[var(--text-primary)] cursor-pointer">
                <input
                  type="checkbox"
                  checked={customFilters.weeklyRsiGt50}
                  onChange={(e) =>
                    setCustomFilters((p) => ({ ...p, weeklyRsiGt50: e.target.checked }))
                  }
                  className="rounded accent-emerald-500"
                />
                Weekly RSI &gt; 50
              </label>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {[
                { id: 'any', label: 'Any' },
                { id: 'gt50', label: 'RSI > 50' },
                { id: 'zone50_60', label: 'RSI 50–60 Pullback' },
                { id: 'gt60', label: 'RSI > 60 Strong' },
                { id: 'lt30', label: 'RSI < 30 Oversold' },
              ].map((opt) => (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => setCustomFilters((p) => ({ ...p, dailyRsiZone: opt.id }))}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                    customFilters.dailyRsiZone === opt.id
                      ? 'bg-emerald-500/20 border-emerald-500 text-emerald-700 dark:text-emerald-300'
                      : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-secondary)]'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 3. Simple Moving Average (SMA) */}
          <div>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)] mb-1.5">
              3. Simple Moving Average (SMA)
            </span>
            <div className="flex flex-wrap gap-1.5">
              {[
                { id: '20', label: 'Price > 20 SMA' },
                { id: '50', label: 'Price > 50 SMA' },
                { id: '100', label: 'Price > 100 SMA' },
                { id: '200', label: 'Price > 200 SMA' },
                { id: 'golden', label: '50/200 Golden Cross' },
              ].map((opt) => {
                const active = customFilters.smaChecks.includes(opt.id);
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => toggleArrayFilter('smaChecks', opt.id)}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                      active
                        ? 'bg-cyan-500/20 border-cyan-500 text-cyan-700 dark:text-cyan-300'
                        : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-secondary)]'
                    }`}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* 4. Exponential Moving Average (EMA) */}
          <div>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)] mb-1.5">
              4. Exponential Moving Average (EMA)
            </span>
            <div className="flex flex-wrap gap-1.5">
              {[
                { id: '9', label: 'Price > 9 EMA' },
                { id: '20', label: 'Price > 20 EMA' },
                { id: '45', label: 'Price > 45 EMA' },
                { id: '200', label: 'Price > 200 EMA' },
                { id: 'cross9_20', label: '9/20 EMA Cross' },
              ].map((opt) => {
                const active = customFilters.emaChecks.includes(opt.id);
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => toggleArrayFilter('emaChecks', opt.id)}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                      active
                        ? 'bg-amber-500/20 border-amber-500 text-amber-700 dark:text-amber-300'
                        : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-secondary)]'
                    }`}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* 5. Bollinger Bands (20, 2) */}
          <div>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)] mb-1.5">
              5. Bollinger Bands (20, 2)
            </span>
            <div className="flex flex-wrap gap-1.5">
              {[
                { id: 'any', label: 'Any' },
                { id: 'upperBO', label: 'Upper Band Breakout' },
                { id: 'squeeze', label: 'Bollinger Squeeze' },
                { id: 'midBounce', label: 'Middle Band Bounce' },
              ].map((opt) => (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => setCustomFilters((p) => ({ ...p, bbSignal: opt.id }))}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                    customFilters.bbSignal === opt.id
                      ? 'bg-sky-500/20 border-sky-500 text-sky-700 dark:text-sky-300'
                      : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-secondary)]'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 6. Hilega Milega (NK Sir Momentum System) */}
          <div>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)] mb-1.5">
              6. Hilega Milega (RSI9 • 3 EMA • 21 WMA)
            </span>
            <div className="flex flex-wrap gap-1.5">
              {[
                { id: 'any', label: 'Any' },
                { id: 'breakout', label: 'Hilega Milega Breakout' },
                { id: 'sweetSpot', label: 'HM Sweet Spot (RSI9 50–70)' },
              ].map((opt) => (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => setCustomFilters((p) => ({ ...p, hilegaSignal: opt.id }))}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                    customFilters.hilegaSignal === opt.id
                      ? 'bg-purple-500/20 border-purple-500 text-purple-700 dark:text-purple-300'
                      : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-secondary)]'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 7. Relative Strength & Volume Spike */}
          <div className="grid grid-cols-2 gap-2">
            <div>
              <span className="block text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)] mb-1.5">
                7a. RS Rating
              </span>
              <div className="flex flex-wrap gap-1">
                {[
                  { id: 'any', label: 'Any' },
                  { id: '80', label: '> 80' },
                  { id: '90', label: '> 90' },
                ].map((opt) => (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => setCustomFilters((p) => ({ ...p, rsMin: opt.id }))}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                      customFilters.rsMin === opt.id
                        ? 'bg-emerald-500/20 border-emerald-500 text-emerald-700 dark:text-emerald-300'
                        : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-secondary)]'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <span className="block text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)] mb-1.5">
                7b. Volume Spike
              </span>
              <div className="flex flex-wrap gap-1">
                {[
                  { id: 'any', label: 'Any' },
                  { id: '1.5', label: '> 1.5x' },
                  { id: '2.0', label: '> 2.0x' },
                ].map((opt) => (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => setCustomFilters((p) => ({ ...p, volMin: opt.id }))}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                      customFilters.volMin === opt.id
                        ? 'bg-emerald-500/20 border-emerald-500 text-emerald-700 dark:text-emerald-300'
                        : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-secondary)]'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setBuilderOpen(false)}
            className="w-full h-10 rounded-xl bg-emerald-500 text-[#0B0E14] text-xs font-bold"
          >
            View {filteredStocks.length} Matching Stocks
          </button>
        </section>
      )}

      {/* Search & Sort Bar */}
      <div className="px-3 flex items-center gap-2">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-[var(--text-muted)] absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search symbol or sector..."
            aria-label="Search symbol or sector"
            className="w-full h-11 pl-9 pr-8 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-emerald-500"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => onSearchChange('')}
              aria-label="Clear search"
              className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-[var(--text-muted)] hover:text-[var(--text-primary)]"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          <select
            value={sortBy}
            onChange={(e) => onSortByChange(e.target.value)}
            aria-label="Sort stocks by"
            className="h-11 px-2.5 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] text-xs font-medium text-[var(--text-primary)] focus:outline-none focus:border-emerald-500"
          >
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.id} value={opt.id}>
                {opt.label}
              </option>
            ))}
          </select>

          <button
            type="button"
            onClick={onToggleSortDir}
            aria-label={`Sort direction: ${sortDir === 'desc' ? 'descending' : 'ascending'}`}
            className="h-11 w-11 flex items-center justify-center rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] text-[var(--text-secondary)] hover:border-slate-400 active:scale-95 transition"
          >
            <ArrowUpDown className={`w-4 h-4 ${sortDir === 'asc' ? 'rotate-180 text-amber-500' : 'text-emerald-500'}`} />
          </button>
        </div>
      </div>

      {/* Results Count */}
      <div className="px-3 flex items-center justify-between text-[11px] text-[var(--text-muted)]">
        <span>
          Showing <strong className="text-[var(--text-primary)] tabular-nums">{filteredStocks.length}</strong>{' '}
          {filteredStocks.length === 1 ? 'stock' : 'stocks'}
          {activeCustomCount > 0 && ` • ${activeCustomCount} custom filters active`}
        </span>
        {(activeCustomCount > 0 || activeScan !== 'allLiquid' || activeSector || searchQuery) && (
          <button
            type="button"
            onClick={resetAllFilters}
            className="text-emerald-500 font-semibold"
          >
            Clear All
          </button>
        )}
      </div>

      {/* Stock Cards List */}
      <div className="px-3 flex flex-col gap-2.5">
        {filteredStocks.length === 0 ? (
          <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-8 text-center">
            <p className="text-sm font-semibold text-[var(--text-primary)]">No stocks match these combined filters</p>
            <p className="mt-1 text-xs text-[var(--text-muted)]">
              Relax one of your Filter Builder conditions or reset to All Liquid NSE.
            </p>
            <button
              type="button"
              onClick={resetAllFilters}
              className="mt-4 px-4 py-2 rounded-lg bg-emerald-500/15 border border-emerald-500/40 text-emerald-600 dark:text-emerald-300 text-xs font-semibold"
            >
              Reset All Filters
            </button>
          </div>
        ) : (
          filteredStocks.map((stock) => {
            const isStarred = watchlist.includes(stock.symbol);
            const isUp = stock.metrics.changePct >= 0;

            return (
              <div
                key={stock.symbol}
                role="button"
                tabIndex={0}
                onClick={() => onOpenChart(stock, filteredStocks, activeListLabel)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onOpenChart(stock, filteredStocks, activeListLabel);
                  }
                }}
                className="w-full text-left rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] hover:border-slate-400 dark:hover:border-slate-600 active:bg-[var(--bg-elevated)] p-3 transition cursor-pointer shadow-sm"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-[15px] font-bold tracking-tight text-[var(--text-primary)]">
                        {stock.symbol}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded-md text-[10px] font-bold tabular-nums border ${getRsBadgeStyle(
                          stock.metrics.rsRating
                        )}`}
                      >
                        RS {stock.metrics.rsRating}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded-md text-[10px] font-medium border ${getSetupBadgeStyle(
                          stock.setupBadge
                        )}`}
                      >
                        {stock.setupBadge}
                      </span>
                    </div>
                    <div className="mt-1 flex items-center gap-1.5 text-[11px] text-[var(--text-muted)] truncate">
                      <span className="text-[var(--text-secondary)] font-medium">{stock.sector}</span>
                      <span>•</span>
                      <span className="truncate">{stock.name}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <div className="text-right">
                      <div className="text-[15px] font-bold tabular-nums text-[var(--text-primary)]">
                        {formatINR(stock.metrics.close)}
                      </div>
                      <div
                        className={`inline-flex items-center justify-end gap-0.5 text-xs font-semibold tabular-nums ${
                          isUp ? 'text-emerald-500' : 'text-rose-500'
                        }`}
                      >
                        {isUp ? (
                          <TrendingUp className="w-3 h-3" />
                        ) : (
                          <TrendingDown className="w-3 h-3" />
                        )}
                        {formatPct(stock.metrics.changePct)}
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onToggleWatchlist(stock.symbol);
                      }}
                      aria-label={isStarred ? `Remove ${stock.symbol} from watchlist` : `Add ${stock.symbol} to watchlist`}
                      className="ml-0.5 w-11 h-11 flex items-center justify-center rounded-lg hover:bg-[var(--bg-elevated)] active:scale-90 transition"
                    >
                      <Star
                        className={`w-5 h-5 ${
                          isStarred ? 'fill-amber-400 text-amber-400' : 'text-[var(--text-muted)]'
                        }`}
                      />
                    </button>
                  </div>
                </div>

                <div className="mt-2.5 pt-2 border-t border-[var(--border-subtle)] grid grid-cols-4 gap-2 text-[11px]">
                  <div>
                    <span className="text-[var(--text-muted)] block text-[10px]">Daily RSI(14)</span>
                    <span
                      className={`font-semibold tabular-nums ${
                        stock.metrics.dailyRsi >= 50 && stock.metrics.dailyRsi <= 60
                          ? 'text-amber-500'
                          : stock.metrics.dailyRsi > 60
                          ? 'text-emerald-500'
                          : 'text-[var(--text-secondary)]'
                      }`}
                    >
                      {stock.metrics.dailyRsi.toFixed(1)}
                    </span>
                  </div>
                  <div>
                    <span className="text-[var(--text-muted)] block text-[10px]">Vol Mult</span>
                    <span
                      className={`font-semibold tabular-nums ${
                        stock.metrics.volMultiplier >= 1.5
                          ? 'text-emerald-500'
                          : 'text-[var(--text-primary)]'
                      }`}
                    >
                      {stock.metrics.volMultiplier.toFixed(2)}x
                    </span>
                  </div>
                  <div>
                    <span className="text-[var(--text-muted)] block text-[10px]">Hilega RSI(9)</span>
                    <span
                      className={`font-semibold tabular-nums ${
                        stock.metrics.hmEma3 > stock.metrics.hmWma21
                          ? 'text-cyan-500'
                          : 'text-[var(--text-secondary)]'
                      }`}
                    >
                      {stock.metrics.rsi9.toFixed(1)}
                    </span>
                  </div>
                  <div className="flex items-center justify-end text-emerald-500">
                    <span className="text-[10px] font-semibold mr-0.5">Chart</span>
                    <ChevronRight className="w-3.5 h-3.5" />
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
