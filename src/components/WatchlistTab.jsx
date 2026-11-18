import React, { useMemo, useState } from 'react';
import {
  Star,
  Download,
  Calculator,
  Trash2,
  Plus,
  FileText,
  TrendingUp,
  TrendingDown,
  ChevronRight,
  Bell,
  BellRing,
  BellOff,
  RotateCcw,
  CheckCircle2,
  ShieldAlert,
  Play,
} from 'lucide-react';
import { exportWatchlistToCsv, formatINR, formatPct } from '../utils/formatters';

export default function WatchlistTab({
  allStocks,
  watchlist,
  notesMap,
  alertsMap,
  alertHistory,
  notificationPermission,
  onRequestNotificationPermission,
  onSendTestNotification,
  onUpdateStockAlert,
  onCheckAlertsNow,
  onClearAlertHistory,
  onToggleWatchlist,
  onUpdateNote,
  onOpenChart,
  onSendToRiskCalc,
  onNotify,
}) {
  const [selectedToAdd, setSelectedToAdd] = useState('');

  const watchedStocks = useMemo(() => {
    const stockMap = new Map(allStocks.map((s) => [s.symbol, s]));
    return watchlist.map((sym) => stockMap.get(sym)).filter(Boolean);
  }, [allStocks, watchlist]);

  const unstarredStocks = useMemo(
    () => allStocks.filter((s) => !watchlist.includes(s.symbol)),
    [allStocks, watchlist]
  );

  const armedCount = useMemo(
    () => watchedStocks.filter((s) => alertsMap[s.symbol]?.enabled).length,
    [watchedStocks, alertsMap]
  );

  const handleExportCsv = () => {
    if (watchedStocks.length === 0) {
      onNotify('Add at least one stock to export CSV');
      return;
    }
    const result = exportWatchlistToCsv(watchedStocks, notesMap);
    onNotify(
      result.method === 'android'
        ? `Sharing ${result.filename}...`
        : `Downloaded ${result.filename}`
    );
  };

  const handleQuickAdd = (e) => {
    const sym = e.target.value;
    if (!sym) return;
    onToggleWatchlist(sym);
    setSelectedToAdd('');
    onNotify(`Added ${sym} to Watchlist`);
  };

  const handleToggleAlert = async (stock) => {
    const existing = alertsMap[stock.symbol];
    const nextEnabled = !(existing?.enabled);

    if (nextEnabled && notificationPermission === 'default') {
      await onRequestNotificationPermission();
    }

    const defaultEntry = +(stock.metrics.close * 1.015).toFixed(2);
    const defaultSl = stock.metrics.swingLowStop;
    const entryPrice = existing?.entryPrice ?? String(defaultEntry);
    const stopLossPrice = existing?.stopLossPrice ?? String(defaultSl);
    const parsedEntry = parseFloat(entryPrice);

    onUpdateStockAlert(
      stock.symbol,
      {
        enabled: nextEnabled,
        entryPrice,
        stopLossPrice,
        entryMode: Number.isFinite(parsedEntry) && parsedEntry < stock.metrics.close ? 'below' : 'above',
        triggeredEntry: false,
        triggeredSl: false,
      },
      nextEnabled
    );

    onNotify(
      nextEnabled
        ? `Price alert armed for ${stock.symbol}`
        : `Price alert paused for ${stock.symbol}`
    );
  };

  const handleAlertFieldChange = (stock, field, value) => {
    const existing = alertsMap[stock.symbol] || {
      enabled: true,
      entryPrice: String(+(stock.metrics.close * 1.015).toFixed(2)),
      stopLossPrice: String(stock.metrics.swingLowStop),
      entryMode: 'above',
    };

    const nextCfg = {
      ...existing,
      [field]: value,
    };

    if (field === 'entryPrice') {
      nextCfg.triggeredEntry = false;
      const parsed = parseFloat(value);
      nextCfg.entryMode =
        Number.isFinite(parsed) && parsed < stock.metrics.close ? 'below' : 'above';
    }
    if (field === 'stopLossPrice') {
      nextCfg.triggeredSl = false;
    }

    onUpdateStockAlert(stock.symbol, nextCfg, false);
  };

  const handleRearmStockAlert = (stock) => {
    const existing = alertsMap[stock.symbol];
    if (!existing) return;
    const parsedEntry = parseFloat(existing.entryPrice);
    onUpdateStockAlert(
      stock.symbol,
      {
        ...existing,
        enabled: true,
        entryMode:
          Number.isFinite(parsedEntry) && parsedEntry < stock.metrics.close ? 'below' : 'above',
        triggeredEntry: false,
        triggeredSl: false,
      },
      true
    );
  };

  return (
    <div className="flex flex-col gap-3 p-3 pb-6">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h2 className="text-sm font-bold text-[var(--text-primary)] flex items-center gap-1.5">
            <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
            Swing Watchlist ({watchedStocks.length})
          </h2>
          <p className="text-[11px] text-[var(--text-muted)]">
            Saved locally with price alerts, notes &amp; CSV export
          </p>
        </div>

        <button
          type="button"
          onClick={handleExportCsv}
          className="h-10 px-3 rounded-xl bg-emerald-500/15 border border-emerald-500/40 text-emerald-700 dark:text-emerald-300 text-xs font-semibold flex items-center gap-1.5 active:scale-95 transition shrink-0"
        >
          <Download className="w-3.5 h-3.5" />
          <span>Export CSV</span>
        </button>
      </div>

      {/* Browser / Local Notification Status Card */}
      <section
        aria-label="Price Alert Notifications"
        className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3 flex flex-col gap-2.5"
      >
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <div
              className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border ${
                notificationPermission === 'granted'
                  ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-500'
                  : notificationPermission === 'denied'
                  ? 'bg-rose-500/15 border-rose-500/40 text-rose-500'
                  : 'bg-amber-500/15 border-amber-500/40 text-amber-500'
              }`}
            >
              {notificationPermission === 'granted' ? (
                <BellRing className="w-4 h-4" />
              ) : notificationPermission === 'denied' ? (
                <BellOff className="w-4 h-4" />
              ) : (
                <Bell className="w-4 h-4" />
              )}
            </div>
            <div className="min-w-0">
              <div className="text-xs font-bold text-[var(--text-primary)] flex items-center gap-1.5">
                <span>Price Alerts</span>
                <span className="px-1.5 py-0.5 rounded bg-[var(--bg-input)] border border-[var(--border-subtle)] text-[10px] font-semibold tabular-nums text-[var(--text-secondary)]">
                  {armedCount} armed
                </span>
              </div>
              <p className="text-[11px] text-[var(--text-muted)] truncate">
                {notificationPermission === 'granted'
                  ? 'Local notifications active for Entry & Stop-Loss hits'
                  : notificationPermission === 'denied'
                  ? 'Notifications blocked in settings (in-app alerts active)'
                  : notificationPermission === 'unsupported'
                  ? 'Browser Notification API unavailable — using in-app alerts'
                  : 'Allow local notifications for Entry & Stop-Loss alerts'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            {notificationPermission !== 'granted' && notificationPermission !== 'unsupported' && (
              <button
                type="button"
                onClick={onRequestNotificationPermission}
                className="h-9 px-3 rounded-lg bg-emerald-500 text-[#0B0E14] text-xs font-bold active:scale-95 transition"
              >
                Enable
              </button>
            )}
            {notificationPermission === 'granted' && (
              <button
                type="button"
                onClick={onSendTestNotification}
                className="h-9 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] hover:border-emerald-500/50 text-[11px] font-semibold text-[var(--text-primary)] active:scale-95 transition"
              >
                Test Alert
              </button>
            )}
          </div>
        </div>

        {armedCount > 0 && (
          <div className="pt-2 border-t border-[var(--border-subtle)] flex items-center justify-between gap-2">
            <span className="text-[10px] text-[var(--text-muted)]">
              Alerts evaluate on EOD sync or manual check
            </span>
            <button
              type="button"
              onClick={onCheckAlertsNow}
              className="px-2.5 py-1 rounded-lg bg-cyan-500/15 border border-cyan-500/40 text-cyan-700 dark:text-cyan-300 text-[11px] font-semibold flex items-center gap-1 active:scale-95 transition"
            >
              <Play className="w-3 h-3" />
              <span>Check Prices Now</span>
            </button>
          </div>
        )}
      </section>

      {/* Triggered Alert History Banner */}
      {alertHistory.length > 0 && (
        <section
          aria-label="Triggered Price Alerts"
          className="rounded-xl bg-[var(--bg-card)] border border-amber-500/40 p-3 flex flex-col gap-2"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-amber-600 dark:text-amber-300 flex items-center gap-1.5">
              <BellRing className="w-3.5 h-3.5" />
              Recent Triggered Alerts ({alertHistory.length})
            </span>
            <button
              type="button"
              onClick={onClearAlertHistory}
              className="text-[11px] font-semibold text-[var(--text-muted)] hover:text-[var(--text-primary)]"
            >
              Clear
            </button>
          </div>

          <div className="flex flex-col gap-1.5 max-h-36 overflow-y-auto">
            {alertHistory.slice(0, 5).map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between text-[11px] bg-[var(--bg-input)] border border-[var(--border-subtle)] rounded-lg px-2.5 py-1.5 tabular-nums"
              >
                <div className="flex items-center gap-2">
                  <span className="font-bold text-[var(--text-primary)]">{item.symbol}</span>
                  <span
                    className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${
                      item.type === 'ENTRY'
                        ? 'bg-emerald-500/20 text-emerald-700 dark:text-emerald-300'
                        : 'bg-rose-500/20 text-rose-700 dark:text-rose-300'
                    }`}
                  >
                    {item.type === 'ENTRY' ? 'ENTRY HIT' : 'SL HIT'}
                  </span>
                  <span className="text-[var(--text-muted)]">
                    Target ₹{item.targetPrice.toFixed(2)}
                  </span>
                </div>
                <span className="text-[var(--text-primary)] font-semibold">
                  ₹{item.currentPrice.toFixed(2)} • {item.time}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Quick Add Stock Selector */}
      {unstarredStocks.length > 0 && (
        <div className="relative">
          <Plus className="w-4 h-4 text-emerald-500 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          <select
            value={selectedToAdd}
            onChange={handleQuickAdd}
            aria-label="Quick add NSE stock to watchlist"
            className="w-full h-11 pl-9 pr-3 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] text-xs font-medium text-[var(--text-primary)] focus:outline-none focus:border-emerald-500"
          >
            <option value="">+ Add an NSE stock to Watchlist...</option>
            {unstarredStocks.map((s) => (
              <option key={s.symbol} value={s.symbol}>
                {s.symbol} — {s.sector} (RS {s.metrics.rsRating})
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Watchlist Cards */}
      {watchedStocks.length === 0 ? (
        <div className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-8 text-center">
          <Star className="w-8 h-8 text-[var(--text-muted)] mx-auto mb-2 opacity-60" />
          <p className="text-sm font-semibold text-[var(--text-primary)]">Your Watchlist is empty</p>
          <p className="mt-1 text-xs text-[var(--text-muted)]">
            Tap the star icon on any Screener row or use the selector above to track swing setups.
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {watchedStocks.map((stock) => {
            const isUp = stock.metrics.changePct >= 0;
            const noteValue = notesMap[stock.symbol] ?? '';
            const alertCfg = alertsMap[stock.symbol];
            const isArmed = Boolean(alertCfg?.enabled);
            const hasTriggered = Boolean(alertCfg?.triggeredEntry || alertCfg?.triggeredSl);

            return (
              <div
                key={stock.symbol}
                className="rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-3 flex flex-col gap-2.5 shadow-sm"
              >
                <div
                  role="button"
                  tabIndex={0}
                  onClick={() => onOpenChart(stock, watchedStocks, 'Watchlist')}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      onOpenChart(stock, watchedStocks, 'Watchlist');
                    }
                  }}
                  className="flex items-start justify-between gap-2 cursor-pointer"
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-[15px] font-bold text-[var(--text-primary)]">{stock.symbol}</span>
                      <span
                        className={`px-1.5 py-0.5 rounded text-[10px] font-bold tabular-nums border ${
                          stock.metrics.rsRating >= 80
                            ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/35'
                            : 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/35'
                        }`}
                      >
                        RS {stock.metrics.rsRating}
                      </span>
                      <span className="text-[10px] text-[var(--text-muted)]">{stock.sector}</span>
                    </div>
                    <div className="mt-1 flex items-center gap-2 text-[11px] text-[var(--text-muted)] tabular-nums">
                      <span>RSI {stock.metrics.dailyRsi.toFixed(1)}</span>
                      <span>•</span>
                      <span>Vol {stock.metrics.volMultiplier.toFixed(2)}x</span>
                      <span>•</span>
                      <span className="text-rose-500 font-medium">SL ₹{stock.metrics.swingLowStop.toFixed(0)}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <div className="text-right">
                      <div className="text-sm font-bold tabular-nums text-[var(--text-primary)]">
                        {formatINR(stock.metrics.close)}
                      </div>
                      <div
                        className={`inline-flex items-center gap-0.5 text-xs font-semibold tabular-nums ${
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
                    <ChevronRight className="w-4 h-4 text-[var(--text-muted)]" />
                  </div>
                </div>

                {/* Entry & Stop-Loss Price Alert Controls */}
                <div className="rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] p-2.5 flex flex-col gap-2">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-1.5">
                      <Bell
                        className={`w-3.5 h-3.5 ${
                          isArmed ? 'text-emerald-500' : 'text-[var(--text-muted)]'
                        }`}
                      />
                      <span className="text-[11px] font-semibold text-[var(--text-primary)]">
                        Entry &amp; Stop-Loss Alerts
                      </span>

                      {alertCfg?.triggeredEntry && (
                        <span className="px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-700 dark:text-emerald-300 text-[9px] font-bold flex items-center gap-0.5">
                          <CheckCircle2 className="w-2.5 h-2.5" /> Entry Hit
                        </span>
                      )}
                      {alertCfg?.triggeredSl && (
                        <span className="px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-700 dark:text-rose-300 text-[9px] font-bold flex items-center gap-0.5">
                          <ShieldAlert className="w-2.5 h-2.5" /> SL Hit
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-1.5">
                      {hasTriggered && isArmed && (
                        <button
                          type="button"
                          onClick={() => handleRearmStockAlert(stock)}
                          className="px-2 py-1 rounded-md bg-amber-500/15 border border-amber-500/40 text-amber-700 dark:text-amber-300 text-[10px] font-semibold flex items-center gap-1"
                        >
                          <RotateCcw className="w-2.5 h-2.5" />
                          Re-arm
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => handleToggleAlert(stock)}
                        aria-label={`${isArmed ? 'Disable' : 'Enable'} price alert for ${stock.symbol}`}
                        className={`px-2.5 py-1 rounded-md text-[10px] font-bold border transition ${
                          isArmed
                            ? 'bg-emerald-500/15 border-emerald-500/50 text-emerald-700 dark:text-emerald-300'
                            : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-muted)]'
                        }`}
                      >
                        {isArmed ? 'Armed' : 'Off'}
                      </button>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label
                        htmlFor={`entry-alert-${stock.symbol}`}
                        className="block text-[10px] text-[var(--text-muted)] mb-1"
                      >
                        Entry Price (₹){' '}
                        <span className="text-emerald-500">
                          {alertCfg?.entryMode === 'below' ? '≤' : '≥'}
                        </span>
                      </label>
                      <input
                        id={`entry-alert-${stock.symbol}`}
                        type="number"
                        step="0.05"
                        inputMode="decimal"
                        value={alertCfg?.entryPrice ?? ''}
                        placeholder={String(+(stock.metrics.close * 1.015).toFixed(2))}
                        onChange={(e) =>
                          handleAlertFieldChange(stock, 'entryPrice', e.target.value)
                        }
                        onBlur={() => {
                          if (isArmed) onCheckAlertsNow();
                        }}
                        className="w-full h-9 px-2.5 rounded-lg bg-[var(--bg-card)] border border-[var(--border-subtle)] text-xs font-bold tabular-nums text-emerald-600 dark:text-emerald-300 placeholder:text-[var(--text-muted)] focus:outline-none focus:border-emerald-500"
                      />
                    </div>

                    <div>
                      <label
                        htmlFor={`sl-alert-${stock.symbol}`}
                        className="block text-[10px] text-[var(--text-muted)] mb-1"
                      >
                        Stop-Loss (₹) <span className="text-rose-500">≤</span>
                      </label>
                      <input
                        id={`sl-alert-${stock.symbol}`}
                        type="number"
                        step="0.05"
                        inputMode="decimal"
                        value={alertCfg?.stopLossPrice ?? ''}
                        placeholder={String(stock.metrics.swingLowStop)}
                        onChange={(e) =>
                          handleAlertFieldChange(stock, 'stopLossPrice', e.target.value)
                        }
                        onBlur={() => {
                          if (isArmed) onCheckAlertsNow();
                        }}
                        className="w-full h-9 px-2.5 rounded-lg bg-[var(--bg-card)] border border-[var(--border-subtle)] text-xs font-bold tabular-nums text-rose-600 dark:text-rose-300 placeholder:text-[var(--text-muted)] focus:outline-none focus:border-rose-500"
                      />
                    </div>
                  </div>
                </div>

                {/* Custom User Notes */}
                <div className="relative">
                  <FileText className="w-3.5 h-3.5 text-[var(--text-muted)] absolute left-2.5 top-2.5 pointer-events-none" />
                  <textarea
                    rows={2}
                    value={noteValue}
                    onChange={(e) => onUpdateNote(stock.symbol, e.target.value)}
                    placeholder="Add entry trigger, pivot level, or swing note..."
                    aria-label={`Notes for ${stock.symbol}`}
                    className="w-full pl-8 pr-2.5 py-2 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-emerald-500 resize-none"
                  />
                </div>

                <div className="flex items-center justify-between gap-2 pt-0.5">
                  <button
                    type="button"
                    onClick={() => onSendToRiskCalc(stock)}
                    className="h-9 px-3 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] hover:border-emerald-500/40 text-xs font-semibold text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5 active:scale-95 transition"
                  >
                    <Calculator className="w-3.5 h-3.5" />
                    <span>Size Position</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      onToggleWatchlist(stock.symbol);
                      onNotify(`Removed ${stock.symbol} from Watchlist`);
                    }}
                    aria-label={`Remove ${stock.symbol}`}
                    className="h-9 px-2.5 rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] hover:border-rose-500/40 text-xs font-medium text-rose-500 flex items-center gap-1 active:scale-95 transition"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>Remove</span>
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
