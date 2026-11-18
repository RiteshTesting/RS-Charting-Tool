import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  BookOpen,
  Plus,
  Download,
  Upload,
  FileSpreadsheet,
  Edit3,
  Trash2,
  X,
  TrendingUp,
  TrendingDown,
  Award,
  CheckCircle2,
  Clock,
  Tag,
} from 'lucide-react';
import {
  exportJournalToCsv,
  exportTextFile,
  formatINR,
} from '../utils/formatters';

export const PRESET_SETUP_TAGS = [
  'RSI+45EMA',
  'Multi-Year BO',
  'Hilega Milega',
  'Bollinger Squeeze',
  '52W High',
  'VCP',
];
export const TRADE_STATUSES = ['Open', 'Closed - Win', 'Closed - Loss'];
const CUSTOM_TAGS_KEY = 'swingpulse_custom_setup_tags_v1';

function loadCustomTags() {
  try {
    const raw = localStorage.getItem(CUSTOM_TAGS_KEY);
    if (raw !== null) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed;
    }
  } catch {
    // ignore
  }
  return [];
}

function createEmptyForm(prefill) {
  const today = new Date().toISOString().slice(0, 10);
  if (prefill) {
    return {
      id: null,
      symbol: prefill.symbol || '',
      status: 'Open',
      entryDate: today,
      entryPrice: prefill.entryPrice != null ? String(prefill.entryPrice) : '',
      stopLoss: prefill.stopLoss != null ? String(prefill.stopLoss) : '',
      exitPrice: prefill.targetPrice != null ? String(prefill.targetPrice) : '',
      quantity: prefill.quantity != null ? String(prefill.quantity) : '25',
      setupTag: prefill.setupTag || 'RSI+45EMA',
      notes: prefill.notes || '',
    };
  }
  return {
    id: null,
    symbol: '',
    status: 'Open',
    entryDate: today,
    entryPrice: '',
    stopLoss: '',
    exitPrice: '',
    quantity: '',
    setupTag: 'RSI+45EMA',
    notes: '',
  };
}

function getStatusBadgeStyle(status) {
  if (status === 'Closed - Win') {
    return 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border-emerald-500/40';
  }
  if (status === 'Closed - Loss') {
    return 'bg-rose-500/15 text-rose-600 dark:text-rose-300 border-rose-500/40';
  }
  return 'bg-amber-500/15 text-amber-600 dark:text-amber-300 border-amber-500/40';
}

export default function JournalTab({
  stocks,
  entries,
  onSaveEntry,
  onDeleteEntry,
  onRestoreBackup,
  buildBackupPayload,
  prefillTrade,
  onClearPrefill,
  onNotify,
}) {
  const [filter, setFilter] = useState('All'); // All | Open | Closed
  const [setupFilter, setSetupFilter] = useState('All');
  const [customTags, setCustomTags] = useState(loadCustomTags);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(() => createEmptyForm(null));
  const restoreInputRef = useRef(null);

  useEffect(() => {
    try {
      localStorage.setItem(CUSTOM_TAGS_KEY, JSON.stringify(customTags));
    } catch {
      // ignore
    }
  }, [customTags]);

  const allAvailableTags = useMemo(() => {
    const set = new Set([...PRESET_SETUP_TAGS, ...customTags]);
    entries.forEach((e) => {
      if (e.setupTag && e.setupTag.trim()) set.add(e.setupTag.trim());
    });
    return Array.from(set);
  }, [customTags, entries]);

  useEffect(() => {
    if (prefillTrade) {
      setForm(createEmptyForm(prefillTrade));
      setShowForm(true);
      onClearPrefill?.();
    }
  }, [prefillTrade, onClearPrefill]);

  const stats = useMemo(() => {
    const total = entries.length;
    const closed = entries.filter((e) => e.status !== 'Open');
    const openCount = total - closed.length;
    const wins = closed.filter((e) => e.status === 'Closed - Win').length;
    const winRate = closed.length > 0 ? (wins / closed.length) * 100 : 0;

    let netPnl = 0;
    const rMultiples = [];

    closed.forEach((e) => {
      const entry = parseFloat(e.entryPrice) || 0;
      const sl = parseFloat(e.stopLoss) || 0;
      const exit = parseFloat(e.exitPrice) || 0;
      const qty = parseFloat(e.quantity) || 0;
      netPnl += (exit - entry) * qty;
      const risk = entry - sl;
      if (risk > 0 && exit > 0) {
        rMultiples.push((exit - entry) / risk);
      }
    });

    const avgR =
      rMultiples.length > 0
        ? rMultiples.reduce((sum, r) => sum + r, 0) / rMultiples.length
        : null;

    return {
      total,
      openCount,
      closedCount: closed.length,
      winRate,
      netPnl,
      avgR,
    };
  }, [entries]);

  const filteredEntries = useMemo(() => {
    return entries.filter((e) => {
      if (filter === 'Open' && e.status !== 'Open') return false;
      if (filter === 'Closed' && e.status === 'Open') return false;
      if (setupFilter !== 'All' && e.setupTag !== setupFilter) return false;
      return true;
    });
  }, [entries, filter, setupFilter]);

  const handleSubmit = (ev) => {
    ev.preventDefault();
    const cleanSymbol = form.symbol.trim().toUpperCase();
    const entryNum = parseFloat(form.entryPrice);
    const slNum = parseFloat(form.stopLoss);
    const exitNum = parseFloat(form.exitPrice);
    const qtyNum = parseFloat(form.quantity);
    const cleanSetupTag = form.setupTag.trim() || 'Custom Setup';

    if (!cleanSymbol) {
      onNotify('Enter a stock symbol (e.g. BEL)');
      return;
    }
    if (!Number.isFinite(entryNum) || entryNum <= 0) {
      onNotify('Enter a valid Entry Price (₹)');
      return;
    }
    if (!Number.isFinite(qtyNum) || qtyNum <= 0) {
      onNotify('Enter a valid Quantity');
      return;
    }

    if (
      !PRESET_SETUP_TAGS.includes(cleanSetupTag) &&
      !customTags.includes(cleanSetupTag)
    ) {
      setCustomTags((prev) => [...prev, cleanSetupTag]);
    }

    onSaveEntry({
      id: form.id || `tr-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      symbol: cleanSymbol,
      status: form.status,
      entryDate: form.entryDate || new Date().toISOString().slice(0, 10),
      entryPrice: entryNum,
      stopLoss: Number.isFinite(slNum) && slNum > 0 ? slNum : 0,
      exitPrice: Number.isFinite(exitNum) && exitNum > 0 ? exitNum : 0,
      quantity: qtyNum,
      setupTag: cleanSetupTag,
      notes: form.notes.trim(),
      updatedAt: new Date().toISOString(),
    });

    setShowForm(false);
    setForm(createEmptyForm(null));
    onNotify(form.id ? `Updated ${cleanSymbol} journal entry` : `Logged ${cleanSymbol} trade`);
  };

  const handleEdit = (entry) => {
    setForm({
      id: entry.id,
      symbol: entry.symbol,
      status: entry.status,
      entryDate: entry.entryDate,
      entryPrice: String(entry.entryPrice),
      stopLoss: entry.stopLoss ? String(entry.stopLoss) : '',
      exitPrice: entry.exitPrice ? String(entry.exitPrice) : '',
      quantity: String(entry.quantity),
      setupTag: entry.setupTag || 'RSI+45EMA',
      notes: entry.notes || '',
    });
    setShowForm(true);
  };

  const handleExportCsv = () => {
    if (entries.length === 0) {
      onNotify('Log at least one trade before exporting CSV');
      return;
    }
    const res = exportJournalToCsv(entries);
    onNotify(
      res.method === 'android' ? `Sharing ${res.filename}...` : `Downloaded ${res.filename}`
    );
  };

  const handleBackupJson = () => {
    const payload = buildBackupPayload();
    const json = JSON.stringify(payload, null, 2);
    const filename = `swingpulse_backup_${new Date().toISOString().slice(0, 10)}.json`;
    const res = exportTextFile(filename, 'application/json', json);
    onNotify(
      res.method === 'android' ? `Sharing ${res.filename}...` : `Downloaded ${res.filename}`
    );
  };

  const handleRestoreFileChange = (ev) => {
    const file = ev.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(String(reader.result || '{}'));
        const count = onRestoreBackup(parsed);
        onNotify(`Restored ${count} journal entries from backup`);
      } catch {
        onNotify('Invalid JSON backup file');
      } finally {
        ev.target.value = '';
      }
    };
    reader.readAsText(file);
  };

  const previewStats = useMemo(() => {
    const entry = parseFloat(form.entryPrice);
    const sl = parseFloat(form.stopLoss);
    const exit = parseFloat(form.exitPrice);
    const qty = parseFloat(form.quantity);
    if (!Number.isFinite(entry) || !Number.isFinite(exit) || !Number.isFinite(qty) || qty <= 0) {
      return null;
    }
    const pnl = (exit - entry) * qty;
    const risk = Number.isFinite(sl) && entry > sl ? entry - sl : 0;
    const rr = risk > 0 ? (exit - entry) / risk : null;
    return { pnl, rr };
  }, [form.entryPrice, form.stopLoss, form.exitPrice, form.quantity]);

  return (
    <div className="flex flex-col gap-3 p-3 pb-6">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h2 className="text-sm font-bold text-[var(--text-primary)] flex items-center gap-1.5">
            <BookOpen className="w-4 h-4 text-emerald-500" />
            Swing Trade Journal
          </h2>
          <p className="text-[11px] text-[var(--text-muted)]">
            Track setups, R-multiples, and custom lessons learned
          </p>
        </div>

        <button
          type="button"
          onClick={() => {
            if (showForm && !form.id) {
              setShowForm(false);
            } else {
              setForm(createEmptyForm(null));
              setShowForm(true);
            }
          }}
          className="h-10 px-3 rounded-xl bg-emerald-500 text-[#0B0E14] text-xs font-bold flex items-center gap-1 active:scale-95 transition shrink-0"
        >
          {showForm && !form.id ? <X className="w-3.5 h-3.5" /> : <Plus className="w-3.5 h-3.5" />}
          <span>{showForm && !form.id ? 'Close' : 'Log Trade'}</span>
        </button>
      </div>

      {/* Performance Summary Cards */}
      <section aria-label="Journal Performance Summary" className="grid grid-cols-2 gap-2.5">
        <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)]">
            Total Trades
          </span>
          <div className="mt-0.5 text-lg font-extrabold tabular-nums text-[var(--text-primary)]">
            {stats.total}
          </div>
          <div className="mt-0.5 text-[10px] text-[var(--text-muted)] tabular-nums">
            {stats.openCount} Open • {stats.closedCount} Closed
          </div>
        </div>

        <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] flex items-center gap-1">
            <Award className="w-3 h-3 text-amber-500" />
            Win Rate
          </span>
          <div className="mt-0.5 text-lg font-extrabold tabular-nums text-emerald-500">
            {stats.winRate.toFixed(1)}%
          </div>
          <div className="mt-0.5 text-[10px] text-[var(--text-muted)] tabular-nums">
            Across {stats.closedCount} closed
          </div>
        </div>

        <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)]">
            Total Net P&amp;L
          </span>
          <div
            className={`mt-0.5 text-lg font-extrabold tabular-nums ${
              stats.netPnl >= 0 ? 'text-emerald-500' : 'text-rose-500'
            }`}
          >
            {stats.netPnl >= 0 ? '+' : ''}
            {formatINR(stats.netPnl, 0)}
          </div>
          <div className="mt-0.5 text-[10px] text-[var(--text-muted)]">Realized closed P&amp;L</div>
        </div>

        <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)]">
            Avg Risk : Reward
          </span>
          <div
            className={`mt-0.5 text-lg font-extrabold tabular-nums ${
              stats.avgR == null
                ? 'text-[var(--text-primary)]'
                : stats.avgR >= 1
                ? 'text-emerald-500'
                : 'text-amber-500'
            }`}
          >
            {stats.avgR == null ? '—' : `1 : ${stats.avgR.toFixed(2)}`}
          </div>
          <div className="mt-0.5 text-[10px] text-[var(--text-muted)]">
            {stats.avgR == null ? 'Close a trade to compute' : `${stats.avgR >= 0 ? '+' : ''}${stats.avgR.toFixed(2)}R avg`}
          </div>
        </div>
      </section>

      {/* Export CSV & Backup/Restore JSON Bar */}
      <div className="grid grid-cols-3 gap-2">
        <button
          type="button"
          onClick={handleExportCsv}
          className="h-10 px-2 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] hover:border-emerald-500/50 text-[11px] font-semibold text-[var(--text-primary)] flex items-center justify-center gap-1.5 active:scale-95 transition"
        >
          <FileSpreadsheet className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
          <span className="truncate">Export CSV</span>
        </button>

        <button
          type="button"
          onClick={handleBackupJson}
          className="h-10 px-2 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] hover:border-cyan-500/50 text-[11px] font-semibold text-[var(--text-primary)] flex items-center justify-center gap-1.5 active:scale-95 transition"
        >
          <Download className="w-3.5 h-3.5 text-cyan-500 shrink-0" />
          <span className="truncate">Backup JSON</span>
        </button>

        <button
          type="button"
          onClick={() => restoreInputRef.current?.click()}
          className="h-10 px-2 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] hover:border-amber-500/50 text-[11px] font-semibold text-[var(--text-primary)] flex items-center justify-center gap-1.5 active:scale-95 transition"
        >
          <Upload className="w-3.5 h-3.5 text-amber-500 shrink-0" />
          <span className="truncate">Restore JSON</span>
        </button>
        <input
          ref={restoreInputRef}
          type="file"
          accept=".json,application/json"
          onChange={handleRestoreFileChange}
          className="hidden"
          aria-label="Restore JSON Backup File"
        />
      </div>

      {/* Add / Edit Trade Form */}
      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="rounded-xl bg-[var(--bg-card)] border border-emerald-500/40 p-3.5 flex flex-col gap-3 shadow-sm"
        >
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-bold uppercase tracking-wider text-emerald-500">
              {form.id ? `Edit ${form.symbol} Trade` : 'Log Swing Trade'}
            </h3>
            <button
              type="button"
              onClick={() => {
                setShowForm(false);
                setForm(createEmptyForm(null));
              }}
              aria-label="Cancel trade form"
              className="p-1 text-[var(--text-muted)] hover:text-[var(--text-primary)]"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          <div className="grid grid-cols-2 gap-2.5">
            <div>
              <label htmlFor="j-symbol" className="block text-[10px] font-semibold text-[var(--text-muted)] mb-1">
                Stock Symbol
              </label>
              <input
                id="j-symbol"
                list="nse-symbol-list"
                value={form.symbol}
                onChange={(e) => setForm((f) => ({ ...f, symbol: e.target.value.toUpperCase() }))}
                placeholder="e.g. BEL"
                className="w-full h-10 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-bold text-[var(--text-primary)] uppercase focus:outline-none focus:border-emerald-500"
              />
              <datalist id="nse-symbol-list">
                {stocks.map((s) => (
                  <option key={s.symbol} value={s.symbol} />
                ))}
              </datalist>
            </div>

            <div>
              <label htmlFor="j-status" className="block text-[10px] font-semibold text-[var(--text-muted)] mb-1">
                Trade Status
              </label>
              <select
                id="j-status"
                value={form.status}
                onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
                className="w-full h-10 px-2 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-semibold text-[var(--text-primary)] focus:outline-none focus:border-emerald-500"
              >
                {TRADE_STATUSES.map((st) => (
                  <option key={st} value={st}>
                    {st}
                  </option>
                ))}
              </select>
            </div>

            <div className="col-span-2">
              <label htmlFor="j-date" className="block text-[10px] font-semibold text-[var(--text-muted)] mb-1">
                Entry Date
              </label>
              <input
                id="j-date"
                type="date"
                value={form.entryDate}
                onChange={(e) => setForm((f) => ({ ...f, entryDate: e.target.value }))}
                className="w-full h-10 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-medium text-[var(--text-primary)] focus:outline-none focus:border-emerald-500"
              />
            </div>

            {/* Hybrid Select or Type Custom Setup Tag */}
            <div className="col-span-2 flex flex-col gap-1.5">
              <label htmlFor="j-setup" className="text-[10px] font-semibold text-[var(--text-muted)] flex items-center gap-1">
                <Tag className="w-3 h-3 text-cyan-500" />
                Setup Tag (Tap Preset or Type Custom)
              </label>
              <div className="flex flex-wrap gap-1.5">
                {allAvailableTags.map((tag) => {
                  const selected = form.setupTag === tag;
                  return (
                    <button
                      key={tag}
                      type="button"
                      onClick={() => setForm((f) => ({ ...f, setupTag: tag }))}
                      className={`px-2.5 py-1 rounded-full text-[11px] font-semibold border transition ${
                        selected
                          ? 'bg-cyan-500/20 border-cyan-500 text-cyan-700 dark:text-cyan-300'
                          : 'bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-secondary)]'
                      }`}
                    >
                      {tag}
                    </button>
                  );
                })}
              </div>
              <input
                id="j-setup"
                list="setup-tags-datalist"
                value={form.setupTag}
                onChange={(e) => setForm((f) => ({ ...f, setupTag: e.target.value }))}
                placeholder="Or type custom setup (e.g. Inside Bar, Earnings Gap)"
                className="w-full h-10 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-semibold text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-cyan-500"
              />
              <datalist id="setup-tags-datalist">
                {allAvailableTags.map((tag) => (
                  <option key={tag} value={tag} />
                ))}
              </datalist>
            </div>

            <div>
              <label htmlFor="j-entry" className="block text-[10px] font-semibold text-[var(--text-muted)] mb-1">
                Entry Price (₹)
              </label>
              <input
                id="j-entry"
                type="number"
                step="0.05"
                inputMode="decimal"
                value={form.entryPrice}
                onChange={(e) => setForm((f) => ({ ...f, entryPrice: e.target.value }))}
                placeholder="390.00"
                className="w-full h-10 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-bold tabular-nums text-[var(--text-primary)] focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div>
              <label htmlFor="j-sl" className="block text-[10px] font-semibold text-[var(--text-muted)] mb-1">
                Stop-Loss (₹)
              </label>
              <input
                id="j-sl"
                type="number"
                step="0.05"
                inputMode="decimal"
                value={form.stopLoss}
                onChange={(e) => setForm((f) => ({ ...f, stopLoss: e.target.value }))}
                placeholder="372.00"
                className="w-full h-10 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-bold tabular-nums text-rose-500 focus:outline-none focus:border-rose-500"
              />
            </div>

            <div>
              <label htmlFor="j-exit" className="block text-[10px] font-semibold text-[var(--text-muted)] mb-1">
                Target / Exit Price (₹)
              </label>
              <input
                id="j-exit"
                type="number"
                step="0.05"
                inputMode="decimal"
                value={form.exitPrice}
                onChange={(e) => setForm((f) => ({ ...f, exitPrice: e.target.value }))}
                placeholder="430.00"
                className="w-full h-10 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-bold tabular-nums text-emerald-500 focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div>
              <label htmlFor="j-qty" className="block text-[10px] font-semibold text-[var(--text-muted)] mb-1">
                Quantity
              </label>
              <input
                id="j-qty"
                type="number"
                inputMode="numeric"
                value={form.quantity}
                onChange={(e) => setForm((f) => ({ ...f, quantity: e.target.value }))}
                placeholder="50"
                className="w-full h-10 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-bold tabular-nums text-[var(--text-primary)] focus:outline-none focus:border-emerald-500"
              />
            </div>
          </div>

          {previewStats && (
            <div className="rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] px-3 py-2 flex items-center justify-between text-[11px] tabular-nums">
              <span className="text-[var(--text-muted)]">
                {form.status === 'Open' ? 'Planned Target P&L:' : 'Realized P&L:'}{' '}
                <strong className={previewStats.pnl >= 0 ? 'text-emerald-500' : 'text-rose-500'}>
                  {previewStats.pnl >= 0 ? '+' : ''}
                  {formatINR(previewStats.pnl, 0)}
                </strong>
              </span>
              {previewStats.rr !== null && (
                <span className="font-bold text-[var(--text-primary)]">
                  {previewStats.rr >= 0 ? '+' : ''}
                  {previewStats.rr.toFixed(2)}R
                </span>
              )}
            </div>
          )}

          <div>
            <label htmlFor="j-notes" className="block text-[10px] font-semibold text-[var(--text-muted)] mb-1">
              Trade Notes / Lesson Learned
            </label>
            <textarea
              id="j-notes"
              rows={3}
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              placeholder="Why did you take this setup? Note volume confirmation, base structure, or emotional mistakes..."
              className="w-full p-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-emerald-500 resize-none"
            />
          </div>

          <div className="flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={() => {
                setShowForm(false);
                setForm(createEmptyForm(null));
              }}
              className="h-10 px-3 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-semibold text-[var(--text-secondary)]"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="h-10 px-4 rounded-xl bg-emerald-500 text-[#0B0E14] text-xs font-bold flex items-center gap-1"
            >
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>{form.id ? 'Update Entry' : 'Save Trade'}</span>
            </button>
          </div>
        </form>
      )}

      {/* Status Filter Pills */}
      <div className="flex items-center gap-2">
        {[
          { id: 'All', count: entries.length },
          { id: 'Open', count: stats.openCount },
          { id: 'Closed', count: stats.closedCount },
        ].map((pill) => {
          const active = filter === pill.id;
          return (
            <button
              key={pill.id}
              type="button"
              onClick={() => setFilter(pill.id)}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition flex items-center gap-1.5 ${
                active
                  ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border-emerald-500/50'
                  : 'bg-[var(--bg-card)] text-[var(--text-secondary)] border-[var(--border-subtle)]'
              }`}
            >
              <span>{pill.id}</span>
              <span className="px-1.5 py-0.5 rounded-full bg-[var(--bg-input)] text-[10px] tabular-nums">
                {pill.count}
              </span>
            </button>
          );
        })}
      </div>

      {/* Setup Tag Filter Pills */}
      <div className="flex gap-1.5 overflow-x-auto no-scrollbar py-0.5">
        {['All', ...allAvailableTags].map((tag) => {
          const active = setupFilter === tag;
          return (
            <button
              key={tag}
              type="button"
              onClick={() => setSetupFilter(tag)}
              className={`shrink-0 px-2.5 py-1 rounded-full text-[11px] font-semibold border transition ${
                active
                  ? 'bg-cyan-500/15 border-cyan-500/50 text-cyan-700 dark:text-cyan-300'
                  : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-muted)]'
              }`}
            >
              {tag === 'All' ? 'All Setups' : tag}
            </button>
          );
        })}
      </div>

      {/* Journal Entries List */}
      {filteredEntries.length === 0 ? (
        <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-8 text-center">
          <BookOpen className="w-8 h-8 text-[var(--text-muted)] mx-auto mb-2 opacity-60" />
          <p className="text-sm font-semibold text-[var(--text-primary)]">No matching trades</p>
          <p className="mt-1 text-xs text-[var(--text-muted)]">
            Tap &ldquo;Log Trade&rdquo; above, clear setup filters, or use &ldquo;Journal&rdquo; from any stock chart.
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-2.5">
          {filteredEntries.map((item) => {
            const isClosed = item.status !== 'Open';
            const pnl =
              item.exitPrice > 0 ? (item.exitPrice - item.entryPrice) * item.quantity : 0;
            const riskPerShare = item.stopLoss > 0 ? item.entryPrice - item.stopLoss : 0;
            const rMult =
              item.exitPrice > 0 && riskPerShare > 0
                ? (item.exitPrice - item.entryPrice) / riskPerShare
                : null;
            const isPositive = pnl >= 0;

            return (
              <div
                key={item.id}
                className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3 flex flex-col gap-2"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-[15px] font-bold text-[var(--text-primary)]">
                        {item.symbol}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded-md text-[10px] font-bold border ${getStatusBadgeStyle(
                          item.status
                        )}`}
                      >
                        {item.status}
                      </span>
                      <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-cyan-500/10 text-cyan-600 dark:text-cyan-300 border border-cyan-500/30">
                        {item.setupTag}
                      </span>
                    </div>
                    <div className="mt-1 flex items-center gap-1.5 text-[11px] text-[var(--text-muted)] tabular-nums">
                      <Clock className="w-3 h-3" />
                      <span>{item.entryDate}</span>
                      <span>•</span>
                      <span>Qty {item.quantity}</span>
                    </div>
                  </div>

                  {item.exitPrice > 0 && (
                    <div className="text-right shrink-0 tabular-nums">
                      <div
                        className={`text-sm font-extrabold flex items-center justify-end gap-0.5 ${
                          isPositive ? 'text-emerald-500' : 'text-rose-500'
                        }`}
                      >
                        {isPositive ? (
                          <TrendingUp className="w-3.5 h-3.5" />
                        ) : (
                          <TrendingDown className="w-3.5 h-3.5" />
                        )}
                        <span>
                          {isPositive ? '+' : ''}
                          {formatINR(pnl, 0)}
                        </span>
                      </div>
                      <div className="text-[10px] font-semibold text-[var(--text-muted)]">
                        {isClosed ? 'Net P&L' : 'Target P&L'}
                        {rMult !== null && (
                          <span className="ml-1 text-[var(--text-primary)]">
                            ({rMult >= 0 ? '+' : ''}
                            {rMult.toFixed(2)}R)
                          </span>
                        )}
                      </div>
                    </div>
                  )}
                </div>

                <div className="grid grid-cols-3 gap-2 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] p-2 text-[11px] tabular-nums">
                  <div>
                    <span className="block text-[9px] uppercase text-[var(--text-muted)]">Entry</span>
                    <span className="font-bold text-[var(--text-primary)]">
                      ₹{Number(item.entryPrice).toFixed(2)}
                    </span>
                  </div>
                  <div>
                    <span className="block text-[9px] uppercase text-[var(--text-muted)]">Stop-Loss</span>
                    <span className="font-bold text-rose-500">
                      {item.stopLoss > 0 ? `₹${Number(item.stopLoss).toFixed(2)}` : '—'}
                    </span>
                  </div>
                  <div>
                    <span className="block text-[9px] uppercase text-[var(--text-muted)]">
                      {isClosed ? 'Exit' : 'Target'}
                    </span>
                    <span className="font-bold text-emerald-500">
                      {item.exitPrice > 0 ? `₹${Number(item.exitPrice).toFixed(2)}` : '—'}
                    </span>
                  </div>
                </div>

                {item.notes && (
                  <p className="text-xs text-[var(--text-secondary)] leading-relaxed whitespace-pre-line bg-[var(--bg-input)]/60 rounded-lg px-2.5 py-2 border border-[var(--border-subtle)]/70">
                    {item.notes}
                  </p>
                )}

                <div className="flex items-center justify-end gap-2 pt-0.5">
                  <button
                    type="button"
                    onClick={() => handleEdit(item)}
                    className="h-8 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-[11px] font-semibold text-[var(--text-secondary)] flex items-center gap-1"
                  >
                    <Edit3 className="w-3 h-3" />
                    <span>Edit</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      onDeleteEntry(item.id);
                      onNotify(`Deleted ${item.symbol} journal entry`);
                    }}
                    className="h-8 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-[11px] font-semibold text-rose-500 flex items-center gap-1"
                  >
                    <Trash2 className="w-3 h-3" />
                    <span>Delete</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
