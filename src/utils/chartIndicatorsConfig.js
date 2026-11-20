export const CHART_INDICATORS_STORAGE_KEY = 'rs_chart_indicators_v2';

export const INDICATOR_CATALOG = [
  {
    id: 'ema9',
    label: 'EMA 9',
    shortLabel: 'EMA 9',
    group: 'overlay',
    darkColor: '#F472B6',
    lightColor: '#EC4899',
  },
  {
    id: 'ema20',
    label: 'EMA 20',
    shortLabel: 'EMA 20',
    group: 'overlay',
    darkColor: '#22D3EE',
    lightColor: '#0891B2',
  },
  {
    id: 'ema45',
    label: 'EMA 45',
    shortLabel: 'EMA 45',
    group: 'overlay',
    darkColor: '#F59E0B',
    lightColor: '#D97706',
  },
  {
    id: 'sma20',
    label: 'SMA 20',
    shortLabel: 'SMA 20',
    group: 'overlay',
    darkColor: '#2DD4BF',
    lightColor: '#0D9488',
  },
  {
    id: 'sma50',
    label: 'SMA 50',
    shortLabel: 'SMA 50',
    group: 'overlay',
    darkColor: '#60A5FA',
    lightColor: '#2563EB',
  },
  {
    id: 'sma200',
    label: 'SMA 200',
    shortLabel: 'SMA 200',
    group: 'overlay',
    darkColor: '#F87171',
    lightColor: '#DC2626',
  },
  {
    id: 'bb20',
    label: 'Bollinger Bands',
    shortLabel: 'BB (20,2)',
    group: 'overlay',
    darkColor: '#38BDF8',
    lightColor: '#0284C7',
  },
  {
    id: 'volume',
    label: 'Volume',
    shortLabel: 'Vol + MA20',
    group: 'volume',
    darkColor: '#10B981',
    lightColor: '#059669',
  },
  {
    id: 'rsi14',
    label: 'RSI(14) + 45 EMA',
    shortLabel: 'RSI(14) + E45',
    group: 'oscillator',
    darkColor: '#38BDF8',
    lightColor: '#0284C7',
  },
  {
    id: 'hilega',
    label: 'Hilega Milega',
    shortLabel: 'Hilega Milega',
    group: 'oscillator',
    darkColor: '#A855F7',
    lightColor: '#9333EA',
  },
];

export const INDICATOR_BY_ID = Object.fromEntries(
  INDICATOR_CATALOG.map((item) => [item.id, item])
);

export const DEFAULT_INDICATOR_STATE = {
  added: ['ema20', 'ema45', 'volume', 'rsi14'],
  hidden: {},
};

const VALID_IDS = new Set(INDICATOR_CATALOG.map((i) => i.id));

export function loadIndicatorState() {
  try {
    const raw = localStorage.getItem(CHART_INDICATORS_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && Array.isArray(parsed.added)) {
        const added = parsed.added.filter((id) => VALID_IDS.has(id));
        const hidden =
          parsed.hidden && typeof parsed.hidden === 'object' ? parsed.hidden : {};
        return { added, hidden };
      }
    }
  } catch {
    // ignore storage errors
  }
  return DEFAULT_INDICATOR_STATE;
}

export function saveIndicatorState(state) {
  try {
    localStorage.setItem(CHART_INDICATORS_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // ignore storage errors
  }
}

export function isIndicatorVisible(state, id) {
  return state.added.includes(id) && !state.hidden[id];
}

export function formatIndicatorLiveValue(id, val, activeBar) {
  if (!val) return '—';
  switch (id) {
    case 'ema9':
      return val.ema9 != null ? val.ema9.toFixed(1) : '—';
    case 'ema20':
      return val.ema20 != null ? val.ema20.toFixed(1) : '—';
    case 'ema45':
      return val.ema45 != null ? val.ema45.toFixed(1) : '—';
    case 'sma20':
      return val.sma20 != null ? val.sma20.toFixed(1) : '—';
    case 'sma50':
      return val.sma50 != null ? val.sma50.toFixed(1) : '—';
    case 'sma200':
      return val.sma200 != null ? val.sma200.toFixed(1) : '—';
    case 'bb20':
      return val.bbUpper != null && val.bbLower != null
        ? `${val.bbUpper.toFixed(0)}/${val.bbLower.toFixed(0)}`
        : '—';
    case 'volume': {
      const vol = activeBar?.volume;
      if (vol == null) return '—';
      if (vol >= 1e6) return `${(vol / 1e6).toFixed(2)}M`;
      if (vol >= 1e3) return `${(vol / 1e3).toFixed(0)}K`;
      return String(vol);
    }
    case 'rsi14':
      return val.rsi14 != null
        ? `${val.rsi14.toFixed(1)} / ${val.rsiEma45 != null ? val.rsiEma45.toFixed(1) : '—'}`
        : '—';
    case 'hilega':
      return val.hmEma3 != null
        ? `${val.hmEma3.toFixed(1)} / ${val.hmWma21 != null ? val.hmWma21.toFixed(1) : '—'}`
        : '—';
    default:
      return '—';
  }
}
