import React, { useEffect, useMemo, useState } from 'react';
import {
  Calculator,
  Target,
  ShieldAlert,
  TrendingUp,
  AlertTriangle,
  Layers,
  RotateCcw,
} from 'lucide-react';
import { formatINR, formatNumberIN } from '../utils/formatters';

const CAPITAL_STORAGE_KEY = 'swingpulse_calc_capital';
const RISK_PCT_STORAGE_KEY = 'swingpulse_calc_risk_pct';

export default function RiskCalcTab({ stocks, prefillStock, onReplaySplash }) {
  const [totalCapital, setTotalCapital] = useState(() => {
    try {
      return localStorage.getItem(CAPITAL_STORAGE_KEY) || '500000';
    } catch {
      return '500000';
    }
  });

  const [riskPercent, setRiskPercent] = useState(() => {
    try {
      return localStorage.getItem(RISK_PCT_STORAGE_KEY) || '1';
    } catch {
      return '1';
    }
  });

  const defaultStock = prefillStock || stocks[0];
  const [selectedSymbol, setSelectedSymbol] = useState(defaultStock?.symbol || '');
  const [entryPrice, setEntryPrice] = useState(
    defaultStock ? String(defaultStock.metrics.close) : '390'
  );
  const [stopLossPrice, setStopLossPrice] = useState(
    defaultStock ? String(defaultStock.metrics.swingLowStop) : '372'
  );

  useEffect(() => {
    if (prefillStock) {
      setSelectedSymbol(prefillStock.symbol);
      setEntryPrice(String(prefillStock.metrics.close));
      setStopLossPrice(String(prefillStock.metrics.swingLowStop));
    }
  }, [prefillStock]);

  useEffect(() => {
    try {
      localStorage.setItem(CAPITAL_STORAGE_KEY, totalCapital);
    } catch {
      // ignore
    }
  }, [totalCapital]);

  useEffect(() => {
    try {
      localStorage.setItem(RISK_PCT_STORAGE_KEY, riskPercent);
    } catch {
      // ignore
    }
  }, [riskPercent]);

  const handlePickStock = (sym) => {
    setSelectedSymbol(sym);
    const found = stocks.find((s) => s.symbol === sym);
    if (found) {
      setEntryPrice(String(found.metrics.close));
      setStopLossPrice(String(found.metrics.swingLowStop));
    }
  };

  const calc = useMemo(() => {
    const cap = parseFloat(totalCapital);
    const riskPct = parseFloat(riskPercent);
    const entry = parseFloat(entryPrice);
    const sl = parseFloat(stopLossPrice);

    if (
      !Number.isFinite(cap) ||
      cap <= 0 ||
      !Number.isFinite(riskPct) ||
      riskPct <= 0 ||
      !Number.isFinite(entry) ||
      entry <= 0 ||
      !Number.isFinite(sl) ||
      sl <= 0
    ) {
      return { valid: false, error: 'Enter positive numbers for capital, risk %, entry, and stop-loss.' };
    }

    if (entry <= sl) {
      return {
        valid: false,
        error: 'For a long swing trade, Entry Price must be strictly greater than Stop-Loss Price.',
      };
    }

    const allowedRiskAmount = cap * (riskPct / 100);
    const riskPerShare = entry - sl;
    const rawQty = Math.floor(allowedRiskAmount / riskPerShare);
    const maxAffordableQty = Math.floor(cap / entry);
    const quantity = Math.max(0, rawQty);
    const positionValue = quantity * entry;
    const maxLoss = quantity * riskPerShare;
    const allocationPct = (positionValue / cap) * 100;
    const slDistancePct = (riskPerShare / entry) * 100;
    const target2R = entry + riskPerShare * 2;
    const target3R = entry + riskPerShare * 3;
    const profit2R = quantity * riskPerShare * 2;
    const profit3R = quantity * riskPerShare * 3;

    return {
      valid: true,
      quantity,
      positionValue,
      maxLoss,
      allowedRiskAmount,
      riskPerShare,
      allocationPct,
      slDistancePct,
      target2R,
      target3R,
      profit2R,
      profit3R,
      exceedsCapital: quantity > maxAffordableQty,
      maxAffordableQty,
    };
  }, [totalCapital, riskPercent, entryPrice, stopLossPrice]);

  return (
    <div className="flex flex-col gap-3 p-3 pb-6">
      <div>
        <h2 className="text-sm font-bold text-[var(--text-primary)] flex items-center gap-1.5">
          <Calculator className="w-4 h-4 text-emerald-500" />
          Swing Position Size Calculator
        </h2>
        <p className="text-[11px] text-[var(--text-muted)]">
          Fixed-fractional risk sizing for disciplined NSE swing entries
        </p>
      </div>

      <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3">
        <label htmlFor="calc-stock-picker" className="block text-[11px] font-semibold text-[var(--text-muted)] mb-1.5">
          Auto-fill Entry &amp; Swing Low SL from Stock
        </label>
        <select
          id="calc-stock-picker"
          value={selectedSymbol}
          onChange={(e) => handlePickStock(e.target.value)}
          className="w-full h-11 px-3 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-medium text-[var(--text-primary)] focus:outline-none focus:border-emerald-500"
        >
          {stocks.map((s) => (
            <option key={s.symbol} value={s.symbol}>
              {s.symbol} — Close ₹{s.metrics.close.toFixed(2)} | Swing SL ₹{s.metrics.swingLowStop.toFixed(2)}
            </option>
          ))}
        </select>
      </div>

      <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3 flex flex-col gap-3">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label htmlFor="calc-capital" className="block text-[11px] font-semibold text-[var(--text-muted)] mb-1">
              Total Capital (₹)
            </label>
            <input
              id="calc-capital"
              type="number"
              inputMode="decimal"
              value={totalCapital}
              onChange={(e) => setTotalCapital(e.target.value)}
              className="w-full h-11 px-3 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] text-sm font-bold tabular-nums text-[var(--text-primary)] focus:outline-none focus:border-emerald-500"
            />
          </div>

          <div>
            <label htmlFor="calc-risk" className="block text-[11px] font-semibold text-[var(--text-muted)] mb-1">
              Risk per Trade (%)
            </label>
            <input
              id="calc-risk"
              type="number"
              step="0.25"
              inputMode="decimal"
              value={riskPercent}
              onChange={(e) => setRiskPercent(e.target.value)}
              className="w-full h-11 px-3 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] text-sm font-bold tabular-nums text-[var(--text-primary)] focus:outline-none focus:border-emerald-500"
            />
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-[10px] text-[var(--text-muted)] mr-1">Risk Preset:</span>
          {['0.5', '1', '1.5', '2'].map((preset) => (
            <button
              key={preset}
              type="button"
              onClick={() => setRiskPercent(preset)}
              className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold tabular-nums border transition ${
                riskPercent === preset
                  ? 'bg-emerald-500/15 border-emerald-500/50 text-emerald-700 dark:text-emerald-300'
                  : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-muted)]'
              }`}
            >
              {preset}%
            </button>
          ))}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label htmlFor="calc-entry" className="block text-[11px] font-semibold text-[var(--text-muted)] mb-1">
              Entry Price (₹)
            </label>
            <input
              id="calc-entry"
              type="number"
              step="0.05"
              inputMode="decimal"
              value={entryPrice}
              onChange={(e) => setEntryPrice(e.target.value)}
              className="w-full h-11 px-3 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] text-sm font-bold tabular-nums text-emerald-600 dark:text-emerald-300 focus:outline-none focus:border-emerald-500"
            />
          </div>

          <div>
            <label htmlFor="calc-sl" className="block text-[11px] font-semibold text-[var(--text-muted)] mb-1">
              Stop-Loss Price (₹)
            </label>
            <input
              id="calc-sl"
              type="number"
              step="0.05"
              inputMode="decimal"
              value={stopLossPrice}
              onChange={(e) => setStopLossPrice(e.target.value)}
              className="w-full h-11 px-3 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] text-sm font-bold tabular-nums text-rose-600 dark:text-rose-300 focus:outline-none focus:border-rose-500"
            />
          </div>
        </div>
      </div>

      {!calc.valid ? (
        <div className="rounded-xl bg-rose-500/10 border border-rose-500/40 p-4 flex items-start gap-2.5 text-xs text-rose-600 dark:text-rose-300">
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
          <span>{calc.error}</span>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="grid grid-cols-2 gap-2.5">
            <div className="rounded-xl bg-[var(--bg-card)] border border-emerald-500/40 p-3">
              <div className="text-[10px] font-semibold uppercase tracking-wider text-emerald-500 flex items-center gap-1">
                <Layers className="w-3 h-3" />
                Quantity to Buy
              </div>
              <div className="mt-1 text-2xl font-extrabold tabular-nums text-[var(--text-primary)]">
                {formatNumberIN(calc.quantity)}
                <span className="text-xs font-medium text-[var(--text-muted)] ml-1">shares</span>
              </div>
              <div className="mt-1 text-[11px] text-[var(--text-muted)] tabular-nums">
                Risk/Share: ₹{calc.riskPerShare.toFixed(2)} ({calc.slDistancePct.toFixed(2)}%)
              </div>
            </div>

            <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3">
              <div className="text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)]">
                Total Position Value
              </div>
              <div className="mt-1 text-lg font-extrabold tabular-nums text-[var(--text-primary)]">
                {formatINR(calc.positionValue, 0)}
              </div>
              <div className="mt-1 text-[11px] tabular-nums text-cyan-600 dark:text-cyan-400 font-medium">
                {calc.allocationPct.toFixed(1)}% of Capital
              </div>
            </div>

            <div className="rounded-xl bg-[var(--bg-card)] border border-rose-500/35 p-3">
              <div className="text-[10px] font-semibold uppercase tracking-wider text-rose-500 flex items-center gap-1">
                <ShieldAlert className="w-3 h-3" />
                Max Loss (1R)
              </div>
              <div className="mt-1 text-lg font-extrabold tabular-nums text-rose-500">
                -{formatINR(calc.maxLoss, 0)}
              </div>
              <div className="mt-1 text-[11px] text-[var(--text-muted)] tabular-nums">
                Budget: {formatINR(calc.allowedRiskAmount, 0)}
              </div>
            </div>

            <div className="rounded-xl bg-[var(--bg-card)] border border-emerald-500/35 p-3">
              <div className="text-[10px] font-semibold uppercase tracking-wider text-emerald-500 flex items-center gap-1">
                <Target className="w-3 h-3" />
                1:2 R:R Target
              </div>
              <div className="mt-1 text-lg font-extrabold tabular-nums text-emerald-500">
                {formatINR(calc.target2R)}
              </div>
              <div className="mt-1 text-[11px] text-emerald-600 dark:text-emerald-400 tabular-nums font-medium">
                +{formatINR(calc.profit2R, 0)} profit
              </div>
            </div>
          </div>

          {calc.exceedsCapital && (
            <div className="rounded-xl bg-amber-500/10 border border-amber-500/40 p-3 flex items-start gap-2 text-xs text-amber-700 dark:text-amber-200">
              <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0 mt-0.5" />
              <span>
                Tight stop-loss causes position value to exceed 100% capital. Max affordable shares without margin:{' '}
                <strong className="tabular-nums">{formatNumberIN(calc.maxAffordableQty)}</strong>.
              </span>
            </div>
          )}

          <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3">
            <div className="flex items-center justify-between text-[11px] font-semibold text-[var(--text-secondary)] mb-2">
              <span className="flex items-center gap-1">
                <TrendingUp className="w-3.5 h-3.5 text-emerald-500" />
                Risk-to-Reward Ladder
              </span>
              <span className="text-[var(--text-muted)] tabular-nums">1:3 Target: {formatINR(calc.target3R)}</span>
            </div>

            <div className="h-3 w-full rounded-full overflow-hidden flex bg-[var(--bg-input)] border border-[var(--border-subtle)]">
              <div className="w-1/4 bg-rose-500/80" title="1R Risk" />
              <div className="w-2/4 bg-emerald-500/70" title="1:2 Target" />
              <div className="w-1/4 bg-emerald-400" title="1:3 Stretch" />
            </div>

            <div className="mt-2 grid grid-cols-4 text-[10px] tabular-nums">
              <div className="text-rose-500 font-semibold">
                SL: ₹{parseFloat(stopLossPrice).toFixed(1)}
              </div>
              <div className="text-[var(--text-primary)] font-semibold text-center">
                Entry: ₹{parseFloat(entryPrice).toFixed(1)}
              </div>
              <div className="text-emerald-500 font-semibold text-center">
                2R: ₹{calc.target2R.toFixed(1)}
              </div>
              <div className="text-emerald-600 dark:text-emerald-300 font-semibold text-right">
                3R: ₹{calc.target3R.toFixed(1)}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Compact App Version Footer */}
      <footer className="mt-1 pt-3 pb-1 border-t border-[var(--border-subtle)] flex flex-col items-center gap-1.5 text-center">
        <p className="text-[11px] font-medium text-[var(--text-muted)] tracking-wide">
          RS Charting Tool • Version 1.0 • EOD Swing Edition
        </p>
        {onReplaySplash && (
          <button
            type="button"
            onClick={onReplaySplash}
            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[var(--bg-card)] border border-[var(--border-subtle)] hover:border-emerald-500/40 text-[10px] font-semibold text-emerald-500 active:scale-95 transition"
          >
            <RotateCcw className="w-3 h-3" />
            <span>Replay Startup Splash</span>
          </button>
        )}
      </footer>
    </div>
  );
}
