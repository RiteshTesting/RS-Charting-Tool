import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  SlidersHorizontal,
  Compass,
  BookOpen,
  Star,
  RefreshCw,
  Clock,
  TrendingUp,
  Link2,
  X,
  CheckCircle2,
  Sun,
  Moon,
  Calculator,
} from 'lucide-react';
import {
  buildBuiltinDataset,
  fetchEodDataset,
  getConfiguredEodUrl,
  setConfiguredEodUrl,
} from './services/dataService';
import {
  dispatchBrowserNotification,
  evaluateWatchlistAlerts,
  getNotificationPermissionStatus,
  requestBrowserNotificationPermission,
} from './services/notificationService';
import ScreenerTab from './components/ScreenerTab';
import SectorsTab from './components/SectorsTab';
import JournalTab from './components/JournalTab';
import WatchlistTab from './components/WatchlistTab';
import RiskCalcTab from './components/RiskCalcTab';
import ChartDrawer from './components/ChartDrawer';
import RsLogo from './components/RsLogo';
import SplashScreen from './components/SplashScreen';

const THEME_KEY = 'swingpulse_theme_v1';
const WATCHLIST_KEY = 'swingpulse_watchlist_v1';
const NOTES_KEY = 'swingpulse_watchlist_notes_v1';
const ALERTS_KEY = 'swingpulse_alerts_v1';
const ALERT_HISTORY_KEY = 'swingpulse_alert_history_v1';
const JOURNAL_KEY = 'swingpulse_journal_v1';

function loadInitialTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    // ignore
  }
  return 'dark';
}

function loadInitialWatchlist() {
  try {
    const raw = localStorage.getItem(WATCHLIST_KEY);
    if (raw !== null) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed;
    }
  } catch {
    // ignore
  }
  return ['BEL', 'TRENT', 'TVSMOTOR'];
}

function loadInitialNotes() {
  try {
    const raw = localStorage.getItem(NOTES_KEY);
    if (raw !== null) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') return parsed;
    }
  } catch {
    // ignore
  }
  return {
    BEL: '45 EMA pullback setup — watch volume > 1.5x above ₹392',
    TRENT: '52W breakout leader — trail stop below 20 EMA',
  };
}

function loadInitialAlerts() {
  try {
    const raw = localStorage.getItem(ALERTS_KEY);
    if (raw !== null) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') return parsed;
    }
  } catch {
    // ignore
  }
  return {};
}

function loadInitialAlertHistory() {
  try {
    const raw = localStorage.getItem(ALERT_HISTORY_KEY);
    if (raw !== null) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed;
    }
  } catch {
    // ignore
  }
  return [];
}

function loadInitialJournal() {
  try {
    const raw = localStorage.getItem(JOURNAL_KEY);
    if (raw !== null) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed;
    }
  } catch {
    // ignore
  }
  return [
    {
      id: 'tr-seed-1',
      symbol: 'TRENT',
      status: 'Closed - Win',
      entryDate: '2026-11-04',
      entryPrice: 7420,
      stopLoss: 7180,
      exitPrice: 7980,
      quantity: 20,
      setupTag: 'Multi-Year BO',
      notes: 'Clean breakout above 52W base on 2.4x volume. Trailed stop with 20 EMA and booked +2.3R.',
    },
    {
      id: 'tr-seed-2',
      symbol: 'BEL',
      status: 'Open',
      entryDate: '2026-11-14',
      entryPrice: 384,
      stopLoss: 371,
      exitPrice: 412,
      quantity: 120,
      setupTag: 'RSI+45EMA',
      notes: 'Held 45 EMA cleanly with RSI(14) rebounding from 52 zone. Weekly RSI remains > 65.',
    },
  ];
}

function mapSetupBadgeToJournalTag(setupBadge) {
  switch (setupBadge) {
    case '45 EMA Pullback':
      return 'RSI+45EMA';
    case '52W Breakout':
      return 'Multi-Year BO';
    case 'Hilega Milega':
      return 'Hilega Milega';
    case 'VCP Pocket Pivot':
      return 'VCP';
    case 'BB Squeeze':
      return 'Bollinger Squeeze';
    default:
      return setupBadge && setupBadge !== 'Consolidating' ? setupBadge : 'RSI+45EMA';
  }
}

export default function App() {
  const [theme, setTheme] = useState(loadInitialTheme);
  const [dataset, setDataset] = useState(() => buildBuiltinDataset());
  const [isRefreshing, setIsRefreshing] = useState(false);

  const [activeTab, setActiveTab] = useState('screener'); // screener | sectors | journal | tools
  const [toolsSubTab, setToolsSubTab] = useState('watchlist'); // watchlist | calc

  const [activeScan, setActiveScan] = useState('allLiquid');
  const [activeSector, setActiveSector] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState('rsRating');
  const [sortDir, setSortDir] = useState('desc');

  const [watchlist, setWatchlist] = useState(loadInitialWatchlist);
  const [notesMap, setNotesMap] = useState(loadInitialNotes);
  const [alertsMap, setAlertsMap] = useState(loadInitialAlerts);
  const [alertHistory, setAlertHistory] = useState(loadInitialAlertHistory);
  const [journalEntries, setJournalEntries] = useState(loadInitialJournal);
  const [notificationPermission, setNotificationPermission] = useState(() =>
    getNotificationPermissionStatus()
  );

  const [chartSession, setChartSession] = useState(null);
  const [calcPrefillStock, setCalcPrefillStock] = useState(null);
  const [journalPrefillTrade, setJournalPrefillTrade] = useState(null);

  const chartStocks = useMemo(() => {
    if (!chartSession) return [];
    const bySym = new Map(dataset.stocks.map((s) => [s.symbol, s]));
    return chartSession.symbols.map((sym) => bySym.get(sym)).filter(Boolean);
  }, [chartSession, dataset.stocks]);

  const chartIndex = useMemo(() => {
    if (!chartSession || chartStocks.length === 0) return -1;
    const idx = chartStocks.findIndex((s) => s.symbol === chartSession.currentSymbol);
    return idx >= 0 ? idx : 0;
  }, [chartSession, chartStocks]);

  const selectedChartStock =
    chartIndex >= 0 && chartIndex < chartStocks.length ? chartStocks[chartIndex] : null;
  const prevChartStock = chartIndex > 0 ? chartStocks[chartIndex - 1] : null;
  const nextChartStock =
    chartIndex >= 0 && chartIndex < chartStocks.length - 1 ? chartStocks[chartIndex + 1] : null;

  const [showEodModal, setShowEodModal] = useState(false);
  const [eodUrlInput, setEodUrlInput] = useState(() => getConfiguredEodUrl());
  const [toastMsg, setToastMsg] = useState('');
  const [showSplash, setShowSplash] = useState(true);
  const [splashKey, setSplashKey] = useState(0);

  const handleReplaySplash = useCallback(() => {
    setSplashKey((k) => k + 1);
    setShowSplash(true);
  }, []);

  const showToast = useCallback((msg) => {
    setToastMsg(msg);
  }, []);

  // Apply Day / Night theme to <html>, meta theme-color, localStorage, and Android shell
  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch {
      // ignore
    }
    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme) {
      metaTheme.setAttribute('content', theme === 'dark' ? '#0B0E14' : '#F8FAFC');
    }
    if (window.AndroidBridge && typeof window.AndroidBridge.setTheme === 'function') {
      window.AndroidBridge.setTheme(theme);
    }
  }, [theme]);

  useEffect(() => {
    if (!toastMsg) return undefined;
    const t = setTimeout(() => setToastMsg(''), 3000);
    return () => clearTimeout(t);
  }, [toastMsg]);

  useEffect(() => {
    try {
      localStorage.setItem(WATCHLIST_KEY, JSON.stringify(watchlist));
    } catch {
      // ignore
    }
  }, [watchlist]);

  useEffect(() => {
    try {
      localStorage.setItem(NOTES_KEY, JSON.stringify(notesMap));
    } catch {
      // ignore
    }
  }, [notesMap]);

  useEffect(() => {
    try {
      localStorage.setItem(ALERTS_KEY, JSON.stringify(alertsMap));
    } catch {
      // ignore
    }
  }, [alertsMap]);

  useEffect(() => {
    try {
      localStorage.setItem(ALERT_HISTORY_KEY, JSON.stringify(alertHistory));
    } catch {
      // ignore
    }
  }, [alertHistory]);

  useEffect(() => {
    try {
      localStorage.setItem(JOURNAL_KEY, JSON.stringify(journalEntries));
    } catch {
      // ignore
    }
  }, [journalEntries]);

  // Sync Android hardware Back gesture/button state
  useEffect(() => {
    const canGoBack = Boolean(selectedChartStock || showEodModal || activeTab !== 'screener');
    if (window.AndroidBridge && typeof window.AndroidBridge.setCanGoBack === 'function') {
      window.AndroidBridge.setCanGoBack(canGoBack);
    }

    window.handleAndroidBack = () => {
      if (showEodModal) {
        setShowEodModal(false);
        return true;
      }
      if (selectedChartStock) {
        setChartSession(null);
        return true;
      }
      if (activeTab !== 'screener') {
        setActiveTab('screener');
        return true;
      }
      return false;
    };
  }, [selectedChartStock, showEodModal, activeTab]);

  const runAlertEvaluation = useCallback(
    (targetStocks = dataset.stocks, targetAlerts = alertsMap, showEmptyNotice = false) => {
      const { triggeredAlerts, updatedAlertsMap } = evaluateWatchlistAlerts(
        targetStocks,
        watchlist,
        targetAlerts
      );
      setAlertsMap(updatedAlertsMap);
      if (triggeredAlerts.length > 0) {
        setAlertHistory((prev) => [...triggeredAlerts, ...prev].slice(0, 20));
        const first = triggeredAlerts[0];
        showToast(
          triggeredAlerts.length === 1
            ? `Alert: ${first.symbol} hit ${first.type === 'ENTRY' ? 'Entry' : 'Stop-Loss'} ₹${first.targetPrice.toFixed(2)}`
            : `${triggeredAlerts.length} watchlist price alerts triggered!`
        );
      } else if (showEmptyNotice) {
        showToast('All armed alerts checked — no new price triggers yet.');
      }
      return triggeredAlerts.length;
    },
    [dataset.stocks, watchlist, alertsMap, showToast]
  );

  useEffect(() => {
    if (notificationPermission === 'granted') {
      runAlertEvaluation(dataset.stocks, alertsMap, false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleRequestNotificationPermission = useCallback(async () => {
    const status = await requestBrowserNotificationPermission();
    setNotificationPermission(status);
    if (status === 'granted') {
      showToast('Price alert notifications enabled!');
      runAlertEvaluation(dataset.stocks, alertsMap, false);
    } else if (status === 'denied') {
      showToast('Notifications blocked. Enable them in browser/app settings.');
    } else {
      showToast('Browser Notification API is unsupported in this environment.');
    }
    return status;
  }, [dataset.stocks, alertsMap, runAlertEvaluation, showToast]);

  const handleSendTestNotification = useCallback(async () => {
    let status = getNotificationPermissionStatus();
    if (status === 'default') {
      status = await handleRequestNotificationPermission();
    }
    if (status === 'granted') {
      const sent = dispatchBrowserNotification(
        'RS Charting Tool Alert Ready',
        'You will be notified whenever a watchlist stock reaches your Entry or Stop-Loss price.',
        'swingpulse-test-alert'
      );
      if (sent) {
        showToast('Test notification dispatched!');
      } else {
        showToast('Could not dispatch browser notification.');
      }
    } else {
      showToast('Enable notification permission to receive alerts.');
    }
  }, [handleRequestNotificationPermission, showToast]);

  const handleUpdateStockAlert = useCallback(
    (symbol, nextCfg, shouldEvaluate = false) => {
      const nextMap = { ...alertsMap, [symbol]: nextCfg };
      setAlertsMap(nextMap);
      if (shouldEvaluate) {
        runAlertEvaluation(dataset.stocks, nextMap, false);
      }
    },
    [alertsMap, dataset.stocks, runAlertEvaluation]
  );

  const handleRefreshEod = async (customUrl) => {
    setIsRefreshing(true);
    try {
      const next = await fetchEodDataset(customUrl);
      setDataset(next);
      const hits = runAlertEvaluation(next.stocks, alertsMap, false);
      if (hits === 0) {
        showToast(`EOD Synced • ${next.stocks.length} NSE stocks (${next.source})`);
      }
    } catch (err) {
      showToast(`External feed unavailable (${err.message}). Using built-in NSE EOD data.`);
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleToggleWatchlist = useCallback((symbol) => {
    setWatchlist((prev) =>
      prev.includes(symbol) ? prev.filter((s) => s !== symbol) : [...prev, symbol]
    );
  }, []);

  const handleUpdateNote = useCallback((symbol, note) => {
    setNotesMap((prev) => ({ ...prev, [symbol]: note }));
  }, []);

  const handleSelectSectorFromRrg = useCallback((sectorName) => {
    setActiveSector(sectorName);
    setActiveScan('allLiquid');
    setActiveTab('screener');
  }, []);

  const handleOpenChart = useCallback((stock, list, contextLabel = 'Screener') => {
    const sourceList = Array.isArray(list) && list.length > 0 ? list : [stock];
    setChartSession({
      symbols: sourceList.map((s) => s.symbol),
      currentSymbol: stock.symbol,
      contextLabel,
    });
  }, []);

  const handleStepChartPrev = useCallback(() => {
    setChartSession((prev) => {
      if (!prev) return prev;
      const idx = prev.symbols.indexOf(prev.currentSymbol);
      if (idx <= 0) return prev;
      return { ...prev, currentSymbol: prev.symbols[idx - 1] };
    });
  }, []);

  const handleStepChartNext = useCallback(() => {
    setChartSession((prev) => {
      if (!prev) return prev;
      const idx = prev.symbols.indexOf(prev.currentSymbol);
      if (idx < 0 || idx >= prev.symbols.length - 1) return prev;
      return { ...prev, currentSymbol: prev.symbols[idx + 1] };
    });
  }, []);

  const handleSendToRiskCalc = useCallback((stock) => {
    setChartSession(null);
    setCalcPrefillStock(stock);
    setToolsSubTab('calc');
    setActiveTab('tools');
  }, []);

  const handleLogInJournalFromChart = useCallback((stock) => {
    const entry = stock.metrics.close;
    const sl = stock.metrics.swingLowStop;
    const risk = Math.max(1, entry - sl);
    const target2R = +(entry + risk * 2).toFixed(2);

    setChartSession(null);
    setJournalPrefillTrade({
      symbol: stock.symbol,
      entryPrice: entry,
      stopLoss: sl,
      targetPrice: target2R,
      setupTag: mapSetupBadgeToJournalTag(stock.setupBadge),
      notes: `${stock.setupBadge} • RS ${stock.metrics.rsRating} | Daily RSI ${stock.metrics.dailyRsi.toFixed(1)} | Wkly RSI ${stock.metrics.weeklyRsi.toFixed(1)}`,
    });
    setActiveTab('journal');
  }, []);

  const handleSaveJournalEntry = useCallback((entry) => {
    setJournalEntries((prev) => {
      const exists = prev.some((item) => item.id === entry.id);
      if (exists) {
        return prev.map((item) => (item.id === entry.id ? entry : item));
      }
      return [entry, ...prev];
    });
  }, []);

  const handleDeleteJournalEntry = useCallback((id) => {
    setJournalEntries((prev) => prev.filter((item) => item.id !== id));
  }, []);

  const buildBackupPayload = useCallback(() => {
    return {
      app: 'SwingPulse NSE',
      version: 1,
      exportedAt: new Date().toISOString(),
      journal: journalEntries,
      watchlist,
      notes: notesMap,
      alerts: alertsMap,
    };
  }, [journalEntries, watchlist, notesMap, alertsMap]);

  const handleRestoreBackup = useCallback((payload) => {
    const incomingJournal = Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.journal)
      ? payload.journal
      : null;

    if (!incomingJournal) {
      throw new Error('Missing journal array in backup');
    }

    const sanitized = incomingJournal
      .filter((item) => item && item.symbol)
      .map((item, idx) => ({
        id: item.id || `tr-restored-${Date.now()}-${idx}`,
        symbol: String(item.symbol).toUpperCase(),
        status: ['Open', 'Closed - Win', 'Closed - Loss'].includes(item.status)
          ? item.status
          : 'Open',
        entryDate: item.entryDate || new Date().toISOString().slice(0, 10),
        entryPrice: Number(item.entryPrice) || 0,
        stopLoss: Number(item.stopLoss) || 0,
        exitPrice: Number(item.exitPrice) || 0,
        quantity: Number(item.quantity) || 0,
        setupTag: item.setupTag || 'RSI+45EMA',
        notes: String(item.notes || ''),
      }));

    setJournalEntries(sanitized);

    if (Array.isArray(payload?.watchlist)) {
      setWatchlist(payload.watchlist);
    }
    if (payload?.notes && typeof payload.notes === 'object') {
      setNotesMap(payload.notes);
    }
    if (payload?.alerts && typeof payload.alerts === 'object') {
      setAlertsMap(payload.alerts);
    }

    return sanitized.length;
  }, []);

  const handleSaveEodUrl = async (e) => {
    e.preventDefault();
    setConfiguredEodUrl(eodUrlInput);
    setShowEodModal(false);
    await handleRefreshEod(eodUrlInput);
  };

  return (
    <div className="min-h-screen bg-[var(--bg-app)] text-[var(--text-primary)] flex flex-col max-w-2xl mx-auto">
      {/* Sticky Top Header */}
      <header className="sticky top-0 z-30 bg-[var(--bg-app)] border-b border-[var(--border-subtle)] px-3 py-2.5 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <RsLogo className="w-8 h-8 shrink-0 drop-shadow-[0_0_8px_rgba(16,185,129,0.25)]" />
          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <h1 className="text-sm font-extrabold tracking-tight text-[var(--text-primary)] truncate">
                RS Charting Tool
              </h1>
              <span className="px-1.5 py-0.5 rounded-md bg-[var(--bg-card)] border border-[var(--border-subtle)] text-[9px] font-bold text-[var(--text-muted)] tracking-wide shrink-0">
                v1.0
              </span>
              <span className="hidden sm:inline-block px-1.5 py-0.5 rounded bg-[var(--bg-card)] border border-[var(--border-subtle)] text-[9px] font-bold text-emerald-500 tracking-wider shrink-0">
                NSE EOD
              </span>
            </div>
            <button
              type="button"
              onClick={() => setShowEodModal(true)}
              className="mt-0.5 flex items-center gap-1 text-[10px] text-[var(--text-muted)] hover:text-[var(--text-primary)] truncate"
            >
              <Clock className="w-2.5 h-2.5 text-emerald-500 shrink-0" />
              <span className="truncate">Last Updated: EOD ({dataset.lastUpdatedEod.slice(0, 10)})</span>
            </button>
          </div>
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          <button
            type="button"
            onClick={() => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))}
            aria-label={theme === 'dark' ? 'Switch to Day Light mode' : 'Switch to Night Dark mode'}
            title={theme === 'dark' ? 'Day Mode' : 'Night Mode'}
            className="w-9 h-9 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] hover:border-amber-500/50 flex items-center justify-center active:scale-95 transition"
          >
            {theme === 'dark' ? (
              <Sun className="w-4 h-4 text-amber-400" />
            ) : (
              <Moon className="w-4 h-4 text-slate-700" />
            )}
          </button>

          <button
            type="button"
            onClick={() => handleRefreshEod()}
            disabled={isRefreshing}
            aria-label="Refresh EOD Data"
            className="h-9 px-2.5 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] hover:border-emerald-500/50 text-xs font-semibold text-[var(--text-primary)] flex items-center gap-1.5 active:scale-95 transition disabled:opacity-60"
          >
            <RefreshCw className={`w-3.5 h-3.5 text-emerald-500 ${isRefreshing ? 'animate-spin' : ''}`} />
            <span>Refresh EOD</span>
          </button>
        </div>
      </header>

      {/* Main Scrollable Viewport */}
      <main className="flex-1 pb-20">
        {activeTab === 'screener' && (
          <ScreenerTab
            stocks={dataset.stocks}
            breadth={dataset.breadth}
            activeScan={activeScan}
            onSelectScan={setActiveScan}
            activeSector={activeSector}
            onClearSector={() => setActiveSector('')}
            searchQuery={searchQuery}
            onSearchChange={setSearchQuery}
            sortBy={sortBy}
            onSortByChange={setSortBy}
            sortDir={sortDir}
            onToggleSortDir={() => setSortDir((d) => (d === 'desc' ? 'asc' : 'desc'))}
            watchlist={watchlist}
            onToggleWatchlist={handleToggleWatchlist}
            onOpenChart={handleOpenChart}
          />
        )}

        {activeTab === 'sectors' && (
          <SectorsTab
            sectors={dataset.sectors}
            theme={theme}
            onSelectSector={handleSelectSectorFromRrg}
          />
        )}

        {activeTab === 'journal' && (
          <JournalTab
            stocks={dataset.stocks}
            entries={journalEntries}
            onSaveEntry={handleSaveJournalEntry}
            onDeleteEntry={handleDeleteJournalEntry}
            onRestoreBackup={handleRestoreBackup}
            buildBackupPayload={buildBackupPayload}
            prefillTrade={journalPrefillTrade}
            onClearPrefill={() => setJournalPrefillTrade(null)}
            onNotify={showToast}
          />
        )}

        {activeTab === 'tools' && (
          <div className="flex flex-col">
            {/* Segmented Sub-Tab Bar inside Watchlist & Calc */}
            <div className="px-3 pt-3">
              <div className="grid grid-cols-2 gap-1 p-1 rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)]">
                <button
                  type="button"
                  onClick={() => setToolsSubTab('watchlist')}
                  className={`h-9 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 transition ${
                    toolsSubTab === 'watchlist'
                      ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border border-emerald-500/40'
                      : 'text-[var(--text-muted)] hover:text-[var(--text-primary)]'
                  }`}
                >
                  <Star className="w-3.5 h-3.5" />
                  <span>Watchlist ({watchlist.length})</span>
                </button>
                <button
                  type="button"
                  onClick={() => setToolsSubTab('calc')}
                  className={`h-9 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 transition ${
                    toolsSubTab === 'calc'
                      ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border border-emerald-500/40'
                      : 'text-[var(--text-muted)] hover:text-[var(--text-primary)]'
                  }`}
                >
                  <Calculator className="w-3.5 h-3.5" />
                  <span>Position Size Calc</span>
                </button>
              </div>
            </div>

            {toolsSubTab === 'watchlist' ? (
              <WatchlistTab
                allStocks={dataset.stocks}
                watchlist={watchlist}
                notesMap={notesMap}
                alertsMap={alertsMap}
                alertHistory={alertHistory}
                notificationPermission={notificationPermission}
                onRequestNotificationPermission={handleRequestNotificationPermission}
                onSendTestNotification={handleSendTestNotification}
                onUpdateStockAlert={handleUpdateStockAlert}
                onCheckAlertsNow={() => runAlertEvaluation(dataset.stocks, alertsMap, true)}
                onClearAlertHistory={() => setAlertHistory([])}
                onToggleWatchlist={handleToggleWatchlist}
                onUpdateNote={handleUpdateNote}
                onOpenChart={handleOpenChart}
                onSendToRiskCalc={handleSendToRiskCalc}
                onNotify={showToast}
              />
            ) : (
              <RiskCalcTab
                stocks={dataset.stocks}
                prefillStock={calcPrefillStock}
                onReplaySplash={handleReplaySplash}
              />
            )}
          </div>
        )}
      </main>

      {/* Bottom Navigation Bar */}
      <nav
        aria-label="Primary Navigation"
        className="fixed bottom-0 left-0 right-0 z-30 max-w-2xl mx-auto bg-[var(--bg-card)] border-t border-[var(--border-subtle)] grid grid-cols-4 px-1 py-1"
      >
        {[
          { id: 'screener', label: 'Screener', Icon: SlidersHorizontal },
          { id: 'sectors', label: 'Sectors', Icon: Compass },
          { id: 'journal', label: 'Journal', Icon: BookOpen, badge: journalEntries.length },
          { id: 'tools', label: 'Watchlist & Calc', Icon: Star, badge: watchlist.length },
        ].map(({ id, label, Icon, badge }) => {
          const active = activeTab === id;
          return (
            <button
              key={id}
              type="button"
              onClick={() => setActiveTab(id)}
              className={`min-h-[52px] flex flex-col items-center justify-center gap-0.5 rounded-xl transition relative ${
                active
                  ? 'text-emerald-600 dark:text-emerald-400 bg-emerald-500/10'
                  : 'text-[var(--text-muted)] hover:text-[var(--text-primary)]'
              }`}
            >
              <div className="relative">
                <Icon className="w-5 h-5" />
                {badge > 0 && (
                  <span className="absolute -top-1.5 -right-2.5 min-w-[16px] h-4 px-1 rounded-full bg-amber-400 text-[#0B0E14] text-[9px] font-extrabold tabular-nums flex items-center justify-center">
                    {badge}
                  </span>
                )}
              </div>
              <span className="text-[10px] font-semibold tracking-tight truncate max-w-full px-1">
                {label}
              </span>
            </button>
          );
        })}
      </nav>

      {/* Interactive Fullscreen Chart Drawer */}
      {selectedChartStock && (
        <ChartDrawer
          stock={selectedChartStock}
          theme={theme}
          isStarred={watchlist.includes(selectedChartStock.symbol)}
          onToggleWatchlist={handleToggleWatchlist}
          onClose={() => setChartSession(null)}
          onSendToRiskCalc={handleSendToRiskCalc}
          onLogInJournal={handleLogInJournalFromChart}
          listPosition={chartIndex + 1}
          listTotal={chartStocks.length}
          listLabel={chartSession?.contextLabel || 'Screener'}
          prevSymbol={prevChartStock?.symbol || null}
          nextSymbol={nextChartStock?.symbol || null}
          onPrev={handleStepChartPrev}
          onNext={handleStepChartNext}
        />
      )}

      {/* Plug-and-Play EOD_DATA_URL Configuration Modal */}
      {showEodModal && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="EOD Data Source Configuration"
          className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-4"
        >
          <form
            onSubmit={handleSaveEodUrl}
            className="w-full max-w-md rounded-2xl bg-[var(--bg-card)] border border-[var(--border-subtle)] p-4 flex flex-col gap-3 shadow-2xl"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-[var(--text-primary)] flex items-center gap-1.5">
                <Link2 className="w-4 h-4 text-emerald-500" />
                EOD Data Feed (`EOD_DATA_URL`)
              </h3>
              <button
                type="button"
                onClick={() => setShowEodModal(false)}
                aria-label="Close modal"
                className="w-9 h-9 flex items-center justify-center rounded-lg text-[var(--text-muted)] hover:text-[var(--text-primary)]"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-xs text-[var(--text-muted)] leading-relaxed">
              Current source: <strong className="text-[var(--text-primary)]">{dataset.source}</strong>. Paste an
              external daily <code className="text-emerald-500">screener_data.json</code> URL below,
              or leave blank to use the built-in 29-stock NSE dataset.
            </p>

            <input
              type="url"
              value={eodUrlInput}
              onChange={(e) => setEodUrlInput(e.target.value)}
              placeholder="https://example.com/screener_data.json"
              className="w-full h-11 px-3 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-emerald-500"
            />

            <div className="flex items-center justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => {
                  setEodUrlInput('');
                  setConfiguredEodUrl('');
                  setShowEodModal(false);
                  handleRefreshEod('');
                }}
                className="h-10 px-3 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] text-xs font-semibold text-[var(--text-secondary)]"
              >
                Use Built-in EOD
              </button>
              <button
                type="submit"
                className="h-10 px-4 rounded-xl bg-emerald-500 text-[#0B0E14] text-xs font-bold"
              >
                Save &amp; Sync
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Toast Notification */}
      {toastMsg && (
        <div className="fixed bottom-20 left-1/2 -translate-x-1/2 z-50 px-3.5 py-2 rounded-xl bg-[var(--bg-card)] border border-emerald-500/50 text-xs font-medium text-[var(--text-primary)] shadow-lg flex items-center gap-2 max-w-[90vw]">
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
          <span className="truncate">{toastMsg}</span>
        </div>
      )}

      {/* Animated Startup Splash Screen */}
      {showSplash && (
        <SplashScreen key={splashKey} onFinish={() => setShowSplash(false)} />
      )}
    </div>
  );
}
