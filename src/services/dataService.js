/**
 * SwingPulse NSE - Plug-and-Play EOD Data & Technical Indicator Engine
 *
 * Computes SMA(20,50,100,200), EMA(9,20,45,50,200), Bollinger Bands(20,2),
 * Volume MA(20), RSI(14), Weekly RSI(14), Hilega Milega (RSI9, 3EMA, 21WMA),
 * and RS Rating (1-99) from 260 daily OHLCV candles per NSE stock.
 */

export const EOD_DATA_URL = '/screener_data.json';
const STORAGE_URL_KEY = 'swingpulse_eod_data_url';

export function getConfiguredEodUrl() {
  try {
    const saved = localStorage.getItem(STORAGE_URL_KEY);
    if (saved !== null && saved.trim() !== '') return saved.trim();
  } catch {
    // ignore storage errors
  }
  return EOD_DATA_URL;
}

export function setConfiguredEodUrl(url) {
  try {
    localStorage.setItem(STORAGE_URL_KEY, (url || '').trim());
  } catch {
    // ignore storage errors
  }
}

function isFiniteNum(v) {
  return typeof v === 'number' && Number.isFinite(v);
}

function toFiniteNum(v) {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  if (typeof v === 'string' && v.trim() !== '') {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

function normalizeCandleTime(rawTime) {
  if (typeof rawTime === 'number' && Number.isFinite(rawTime)) {
    return rawTime > 1e11 ? Math.floor(rawTime / 1000) : rawTime;
  }
  if (typeof rawTime === 'string') {
    const trimmed = rawTime.trim();
    if (/^\d{4}-\d{2}-\d{2}/.test(trimmed)) {
      return trimmed.slice(0, 10);
    }
    const parsed = Date.parse(trimmed);
    if (Number.isFinite(parsed)) {
      return new Date(parsed).toISOString().slice(0, 10);
    }
  }
  return null;
}

function normalizeCandles(rawCandles, fallbackPrice, fallbackVol) {
  const seen = new Map();
  if (Array.isArray(rawCandles)) {
    for (const c of rawCandles) {
      if (!c || typeof c !== 'object') continue;
      const time = normalizeCandleTime(c.time ?? c.date ?? c.timestamp);
      const close = toFiniteNum(c.close ?? c.c);
      if (time == null || close == null) continue;
      const open = toFiniteNum(c.open ?? c.o) ?? close;
      const high = toFiniteNum(c.high ?? c.h) ?? Math.max(open, close);
      const low = toFiniteNum(c.low ?? c.l) ?? Math.min(open, close);
      const volume = Math.max(0, Math.round(toFiniteNum(c.volume ?? c.v) ?? 0));
      seen.set(String(time), {
        time,
        open: +open.toFixed(2),
        high: +Math.max(high, open, close).toFixed(2),
        low: +Math.min(low, open, close).toFixed(2),
        close: +close.toFixed(2),
        volume,
      });
    }
  }

  const list = Array.from(seen.values()).sort((a, b) => {
    if (typeof a.time === 'number' && typeof b.time === 'number') return a.time - b.time;
    return String(a.time).localeCompare(String(b.time));
  });

  if (list.length === 0) {
    const px = toFiniteNum(fallbackPrice) ?? 100;
    const vol = Math.max(0, Math.round(toFiniteNum(fallbackVol) ?? 0));
    list.push({
      time: new Date().toISOString().slice(0, 10),
      open: +px.toFixed(2),
      high: +px.toFixed(2),
      low: +px.toFixed(2),
      close: +px.toFixed(2),
      volume: vol,
    });
  }
  return list;
}

function alignSeriesOverride(rawField, computedSeries, candles) {
  const len = candles.length;
  if (Array.isArray(rawField) && rawField.length > 0) {
    const out = new Array(len).fill(null);
    if (typeof rawField[0] === 'object' && rawField[0] !== null) {
      const byTime = new Map();
      for (const pt of rawField) {
        const t = normalizeCandleTime(pt.time ?? pt.date);
        const v = toFiniteNum(pt.value ?? pt.val);
        if (t != null && v != null) byTime.set(String(t), v);
      }
      candles.forEach((c, idx) => {
        const v = byTime.get(String(c.time));
        out[idx] = v != null ? v : computedSeries[idx] ?? null;
      });
      return out;
    }
    const offset = len - rawField.length;
    for (let i = 0; i < rawField.length; i++) {
      const targetIdx = i + offset;
      if (targetIdx >= 0 && targetIdx < len) {
        const v = toFiniteNum(rawField[i]);
        out[targetIdx] = v != null ? v : computedSeries[targetIdx] ?? null;
      }
    }
    return out;
  }

  const scalar = toFiniteNum(rawField);
  if (scalar != null && len > 0) {
    const out = [...computedSeries];
    out[len - 1] = scalar;
    return out;
  }
  return computedSeries;
}

function withCacheBuster(url) {
  const sep = url.includes('?') ? '&' : '?';
  return `${url}${sep}t=${Date.now()}`;
}

function createRng(seed) {
  let t = seed >>> 0;
  return function () {
    t += 0x6d2b79f5;
    let r = Math.imul(t ^ (t >>> 15), 1 | t);
    r ^= r + Math.imul(r ^ (r >>> 7), 61 | r);
    return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
  };
}

function generateTradingDates(count) {
  const dates = [];
  const cursor = new Date('2026-11-17T00:00:00Z');
  while (dates.length < count) {
    const day = cursor.getUTCDay();
    if (day !== 0 && day !== 6) {
      dates.unshift(cursor.toISOString().slice(0, 10));
    }
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  return dates;
}

const TRADING_DATES = generateTradingDates(260);

const NSE_UNIVERSE = [
  // DEFENCE (Leading)
  { symbol: 'BEL', name: 'Bharat Electronics Ltd', sector: 'Defence', basePrice: 214, baseVol: 18500000, archetype: 'pullback', trend1Y: 0.56, seed: 101 },
  { symbol: 'HAL', name: 'Hindustan Aeronautics Ltd', sector: 'Defence', basePrice: 3420, baseVol: 2450000, archetype: 'breakout', trend1Y: 0.68, seed: 102 },
  { symbol: 'MAZDOCK', name: 'Mazagon Dock Shipbuilders', sector: 'Defence', basePrice: 3150, baseVol: 2100000, archetype: 'vcp', trend1Y: 0.72, seed: 103 },
  { symbol: 'BDL', name: 'Bharat Dynamics Ltd', sector: 'Defence', basePrice: 1020, baseVol: 3200000, archetype: 'hilega_bo', trend1Y: 0.44, seed: 104 },

  // CAPITAL GOODS (Leading)
  { symbol: 'CGPOWER', name: 'CG Power & Industrial Sol.', sector: 'Capital Goods', basePrice: 545, baseVol: 4800000, archetype: 'breakout', trend1Y: 0.62, seed: 201 },
  { symbol: 'CUMMINSIND', name: 'Cummins India Ltd', sector: 'Capital Goods', basePrice: 2890, baseVol: 1350000, archetype: 'pullback', trend1Y: 0.48, seed: 202 },
  { symbol: 'ABB', name: 'ABB India Ltd', sector: 'Capital Goods', basePrice: 6420, baseVol: 620000, archetype: 'vcp', trend1Y: 0.51, seed: 203 },
  { symbol: 'SIEMENS', name: 'Siemens Ltd', sector: 'Capital Goods', basePrice: 5980, baseVol: 540000, archetype: 'hilega_bo', trend1Y: 0.42, seed: 204 },

  // AUTO (Weakening / Mixed)
  { symbol: 'TVSMOTOR', name: 'TVS Motor Company Ltd', sector: 'Auto', basePrice: 2180, baseVol: 2100000, archetype: 'breakout', trend1Y: 0.54, seed: 301 },
  { symbol: 'M&M', name: 'Mahindra & Mahindra Ltd', sector: 'Auto', basePrice: 2340, baseVol: 3900000, archetype: 'pullback', trend1Y: 0.49, seed: 302 },
  { symbol: 'EICHERMOT', name: 'Eicher Motors Ltd', sector: 'Auto', basePrice: 4180, baseVol: 980000, archetype: 'vcp', trend1Y: 0.36, seed: 303 },
  { symbol: 'BAJAJ-AUTO', name: 'Bajaj Auto Ltd', sector: 'Auto', basePrice: 8920, baseVol: 610000, archetype: 'weakening', trend1Y: 0.29, seed: 304 },

  // IT (Leading / Improving)
  { symbol: 'PERSISTENT', name: 'Persistent Systems Ltd', sector: 'IT', basePrice: 4280, baseVol: 1120000, archetype: 'breakout', trend1Y: 0.52, seed: 401 },
  { symbol: 'COFORGE', name: 'Coforge Ltd', sector: 'IT', basePrice: 6120, baseVol: 890000, archetype: 'pullback', trend1Y: 0.45, seed: 402 },
  { symbol: 'KPITTECH', name: 'KPIT Technologies Ltd', sector: 'IT', basePrice: 1410, baseVol: 2700000, archetype: 'vcp', trend1Y: 0.34, seed: 403 },
  { symbol: 'TCS', name: 'Tata Consultancy Services', sector: 'IT', basePrice: 3890, baseVol: 2850000, archetype: 'improving', trend1Y: 0.16, seed: 404 },

  // PHARMA (Leading)
  { symbol: 'SUNPHARMA', name: 'Sun Pharmaceutical Ind.', sector: 'Pharma', basePrice: 1580, baseVol: 3400000, archetype: 'pullback', trend1Y: 0.41, seed: 501 },
  { symbol: 'DIVISLAB', name: "Divi's Laboratories Ltd", sector: 'Pharma', basePrice: 4650, baseVol: 880000, archetype: 'vcp', trend1Y: 0.47, seed: 502 },
  { symbol: 'CIPLA', name: 'Cipla Ltd', sector: 'Pharma', basePrice: 1445, baseVol: 2300000, archetype: 'improving', trend1Y: 0.19, seed: 503 },

  // CONSUMER EMS / RETAIL (Leaders)
  { symbol: 'TRENT', name: 'Trent Ltd', sector: 'Consumer EMS', basePrice: 4720, baseVol: 1750000, archetype: 'breakout', trend1Y: 0.78, seed: 601 },
  { symbol: 'DIXON', name: 'Dixon Technologies (India)', sector: 'Consumer EMS', basePrice: 10850, baseVol: 780000, archetype: 'breakout', trend1Y: 0.84, seed: 602 },

  // METAL (Improving)
  { symbol: 'JSWSTEEL', name: 'JSW Steel Ltd', sector: 'Metal', basePrice: 885, baseVol: 3900000, archetype: 'pullback', trend1Y: 0.27, seed: 701 },
  { symbol: 'VEDL', name: 'Vedanta Ltd', sector: 'Metal', basePrice: 412, baseVol: 11200000, archetype: 'improving', trend1Y: 0.31, seed: 702 },
  { symbol: 'HINDALCO', name: 'Hindalco Industries Ltd', sector: 'Metal', basePrice: 635, baseVol: 5400000, archetype: 'improving', trend1Y: 0.14, seed: 703 },

  // REALTY (Lagging / Consolidating)
  { symbol: 'DLF', name: 'DLF Ltd', sector: 'Realty', basePrice: 795, baseVol: 4700000, archetype: 'vcp', trend1Y: 0.22, seed: 801 },
  { symbol: 'LODHA', name: 'Macrotech Developers Ltd', sector: 'Realty', basePrice: 1210, baseVol: 2150000, archetype: 'weakening', trend1Y: 0.12, seed: 802 },

  // PSU BANK (Lagging / Oversold)
  { symbol: 'SBIN', name: 'State Bank of India', sector: 'PSU Bank', basePrice: 775, baseVol: 14500000, archetype: 'weakening', trend1Y: 0.15, seed: 901 },
  { symbol: 'BANKBARODA', name: 'Bank of Baroda', sector: 'PSU Bank', basePrice: 248, baseVol: 18200000, archetype: 'oversold', trend1Y: -0.05, seed: 902 },
  { symbol: 'CANBK', name: 'Canara Bank', sector: 'PSU Bank', basePrice: 108, baseVol: 24000000, archetype: 'oversold', trend1Y: -0.09, seed: 903 },
];

function generateStockCandles(spec) {
  const rng = createRng(spec.seed);
  const total = TRADING_DATES.length;
  const candles = [];
  let price = spec.basePrice;

  for (let i = 0; i < total; i++) {
    let ret = 0;
    let volMult = 0.78 + rng() * 0.45;
    let bodyBias = 0;

    if (i < 220) {
      const wave = Math.sin(i * 0.13 + spec.seed) * 0.0045;
      const drift = spec.trend1Y / 235;
      ret = drift + wave + (rng() - 0.49) * 0.022;
      if (spec.archetype === 'oversold' && i > 175) {
        ret = -0.002 + (rng() - 0.52) * 0.019;
      }
    } else {
      const k = i - 220; // 0 .. 39
      if (spec.archetype === 'pullback') {
        if (k < 16) {
          ret = 0.0052 + (rng() - 0.44) * 0.013;
        } else if (k < 35) {
          ret = -0.0029 + (rng() - 0.51) * 0.008;
          volMult = 0.62 + rng() * 0.25;
        } else if (k < 39) {
          ret = -0.0014 + (rng() - 0.5) * 0.006;
          volMult = 0.68 + rng() * 0.2;
        } else {
          ret = 0.029 + rng() * 0.008;
          volMult = 1.75 + rng() * 0.55;
          bodyBias = 0.7;
        }
      } else if (spec.archetype === 'breakout') {
        if (k < 20) {
          ret = 0.006 + (rng() - 0.44) * 0.014;
        } else if (k < 39) {
          ret = (rng() - 0.49) * 0.01;
          volMult = 0.75 + rng() * 0.35;
        } else {
          ret = 0.044 + rng() * 0.016;
          volMult = 2.35 + rng() * 0.9;
          bodyBias = 0.85;
        }
      } else if (spec.archetype === 'vcp') {
        // Tight Bollinger squeeze in last 10 days + pocket pivot on latest bar
        if (k < 15) {
          ret = (rng() - 0.48) * 0.028;
          volMult = 1.05 + rng() * 0.45;
        } else if (k < 30) {
          ret = (rng() - 0.49) * 0.012;
          volMult = 0.75 + rng() * 0.3;
        } else if (k < 39) {
          ret = (rng() - 0.48) * 0.0045;
          volMult = 0.52 + rng() * 0.22;
        } else {
          ret = 0.021 + rng() * 0.006;
          volMult = 1.75 + rng() * 0.5;
          bodyBias = 0.75;
        }
      } else if (spec.archetype === 'hilega_bo') {
        // Mild dip so 3 EMA of RSI(9) dips below 21 WMA, then sharp 2-day momentum cross above 21 WMA
        if (k < 25) {
          ret = 0.004 + (rng() - 0.46) * 0.014;
        } else if (k < 37) {
          ret = -0.0045 + (rng() - 0.52) * 0.008;
          volMult = 0.72 + rng() * 0.25;
        } else if (k === 37) {
          ret = -0.002;
          volMult = 0.7;
        } else if (k === 38) {
          ret = 0.014;
          volMult = 1.3;
        } else {
          ret = 0.032 + rng() * 0.008;
          volMult = 1.9 + rng() * 0.45;
          bodyBias = 0.8;
        }
      } else if (spec.archetype === 'improving') {
        if (k < 18) {
          ret = -0.001 + (rng() - 0.5) * 0.015;
        } else if (k < 39) {
          ret = 0.0052 + (rng() - 0.43) * 0.011;
          volMult = 0.95 + rng() * 0.35;
        } else {
          ret = 0.021 + rng() * 0.008;
          volMult = 1.45 + rng() * 0.35;
        }
      } else if (spec.archetype === 'weakening') {
        if (k < 39) {
          ret = -0.0018 + (rng() - 0.52) * 0.015;
        } else {
          ret = -0.012 - rng() * 0.011;
          volMult = 1.15 + rng() * 0.35;
        }
      } else {
        // oversold (RSI < 30)
        if (k < 28) {
          ret = -0.002 + (rng() - 0.53) * 0.013;
        } else if (k < 39) {
          ret = -0.0048 + (rng() - 0.54) * 0.009;
          volMult = 1.05 + rng() * 0.35;
        } else {
          ret = -0.008 - rng() * 0.006;
          volMult = 1.25 + rng() * 0.3;
        }
      }
    }

    const prevClose = price;
    const close = Math.max(20, +(prevClose * (1 + ret)).toFixed(2));
    const gap = (rng() - 0.5) * 0.006;
    const open =
      bodyBias > 0
        ? +(prevClose * (1 + Math.max(0, gap))).toFixed(2)
        : +(prevClose * (1 + gap)).toFixed(2);

    const spreadFactor =
      spec.archetype === 'vcp' && i >= 250 && i < 259
        ? 0.004
        : 0.011 + rng() * 0.012;
    const wickUp = Math.abs(rng() * spreadFactor * close);
    const wickDown = Math.abs(rng() * spreadFactor * close * (bodyBias > 0 ? 0.35 : 0.8));

    const high = +Math.max(open, close, Math.max(open, close) + wickUp).toFixed(2);
    const low = +Math.max(10, Math.min(open, close, Math.min(open, close) - wickDown)).toFixed(2);
    const volume = Math.round(spec.baseVol * volMult);

    candles.push({
      time: TRADING_DATES[i],
      open,
      high,
      low,
      close,
      volume,
    });

    price = close;
  }

  return candles;
}

// --- Technical Indicator Engine ---

export function calculateSMA(values, period) {
  const result = new Array(values.length).fill(null);
  if (values.length < period) return result;
  let running = 0;
  let validCount = 0;
  for (let i = 0; i < values.length; i++) {
    const v = values[i];
    if (v != null && !Number.isNaN(v)) {
      running += v;
      validCount++;
    }
    if (i >= period) {
      const old = values[i - period];
      if (old != null && !Number.isNaN(old)) {
        running -= old;
        validCount--;
      }
    }
    if (i >= period - 1 && validCount === period) {
      result[i] = +(running / period).toFixed(2);
    }
  }
  return result;
}

export function calculateEMA(values, period) {
  if (!values.length) return [];
  const k = 2 / (period + 1);
  const result = new Array(values.length).fill(null);

  const firstValid = values.findIndex((v) => v !== null && v !== undefined && !Number.isNaN(v));
  if (firstValid === -1) return result;

  if (values.length - firstValid < period) {
    let ema = values[firstValid];
    result[firstValid] = +ema.toFixed(2);
    for (let i = firstValid + 1; i < values.length; i++) {
      const val = values[i];
      if (val == null) continue;
      ema = val * k + ema * (1 - k);
      result[i] = +ema.toFixed(2);
    }
    return result;
  }

  let sum = 0;
  for (let i = firstValid; i < firstValid + period; i++) {
    sum += values[i];
  }
  let ema = sum / period;
  const seedIdx = firstValid + period - 1;
  result[seedIdx] = +ema.toFixed(2);

  for (let i = seedIdx + 1; i < values.length; i++) {
    const val = values[i];
    if (val == null) continue;
    ema = val * k + ema * (1 - k);
    result[i] = +ema.toFixed(2);
  }
  return result;
}

export function calculateWMA(values, period) {
  const result = new Array(values.length).fill(null);
  const weightSum = (period * (period + 1)) / 2;

  for (let i = period - 1; i < values.length; i++) {
    let weighted = 0;
    let valid = true;
    for (let j = 0; j < period; j++) {
      const v = values[i - period + 1 + j];
      if (v == null || Number.isNaN(v)) {
        valid = false;
        break;
      }
      weighted += v * (j + 1);
    }
    if (valid) {
      result[i] = +(weighted / weightSum).toFixed(2);
    }
  }
  return result;
}

export function calculateBollingerBands(closes, period = 20, mult = 2) {
  const middle = calculateSMA(closes, period);
  const upper = new Array(closes.length).fill(null);
  const lower = new Array(closes.length).fill(null);
  const bandwidth = new Array(closes.length).fill(null);

  for (let i = period - 1; i < closes.length; i++) {
    const mean = middle[i];
    if (mean == null) continue;
    let sqDiffSum = 0;
    for (let j = i - period + 1; j <= i; j++) {
      const d = closes[j] - mean;
      sqDiffSum += d * d;
    }
    const sd = Math.sqrt(sqDiffSum / period);
    const up = +(mean + mult * sd).toFixed(2);
    const lo = +(mean - mult * sd).toFixed(2);
    upper[i] = up;
    lower[i] = lo;
    bandwidth[i] = mean > 0 ? +((up - lo) / mean).toFixed(4) : null;
  }

  return { upper, middle, lower, bandwidth };
}

export function calculateRSI(closes, period = 14) {
  const result = new Array(closes.length).fill(null);
  if (closes.length <= period) return result;

  let gainSum = 0;
  let lossSum = 0;
  for (let i = 1; i <= period; i++) {
    const diff = closes[i] - closes[i - 1];
    if (diff >= 0) gainSum += diff;
    else lossSum += Math.abs(diff);
  }

  let avgGain = gainSum / period;
  let avgLoss = lossSum / period;
  result[period] = avgLoss === 0 ? 100 : +(100 - 100 / (1 + avgGain / avgLoss)).toFixed(2);

  for (let i = period + 1; i < closes.length; i++) {
    const diff = closes[i] - closes[i - 1];
    const gain = diff > 0 ? diff : 0;
    const loss = diff < 0 ? Math.abs(diff) : 0;

    avgGain = (avgGain * (period - 1) + gain) / period;
    avgLoss = (avgLoss * (period - 1) + loss) / period;

    if (avgLoss === 0) {
      result[i] = 100;
    } else {
      const rs = avgGain / avgLoss;
      result[i] = +(100 - 100 / (1 + rs)).toFixed(2);
    }
  }
  return result;
}

export function calculateWeeklyRSI(candles, period = 14) {
  const weeklyCloses = [];
  for (let i = 4; i < candles.length; i += 5) {
    weeklyCloses.push(candles[i].close);
  }
  if ((candles.length - 1) % 5 !== 4) {
    weeklyCloses.push(candles[candles.length - 1].close);
  }
  const weeklyRsi = calculateRSI(weeklyCloses, period);
  const last = weeklyRsi[weeklyRsi.length - 1];
  return last ?? 50;
}

function computeRawPerfScore(closes) {
  const n = closes.length;
  const last = closes[n - 1];
  const p63 = closes[Math.max(0, n - 64)];
  const p126 = closes[Math.max(0, n - 127)];
  const p189 = closes[Math.max(0, n - 190)];
  const p252 = closes[0];

  const r3m = (last - p63) / p63;
  const r6m = (last - p126) / p126;
  const r9m = (last - p189) / p189;
  const r12m = (last - p252) / p252;

  return 0.4 * r3m + 0.25 * r6m + 0.2 * r9m + 0.15 * r12m;
}

const UNIVERSE_META_MAP = new Map(
  NSE_UNIVERSE.map((s) => [s.symbol.toUpperCase(), { name: s.name, sector: s.sector }])
);

function enrichStocks(rawStocks, options = {}) {
  const { isExternal = false } = options;

  const normalizedList = rawStocks
    .filter((s) => s && s.symbol)
    .map((stock) => {
      const symbol = String(stock.symbol).trim();
      const lookupKey = symbol.replace(/\.NS$/i, '').toUpperCase();
      const meta = UNIVERSE_META_MAP.get(lookupKey);
      const candles = normalizeCandles(stock.candles, stock.price ?? stock.close, stock.volume);
      return {
        raw: stock,
        symbol,
        name: stock.name || meta?.name || lookupKey,
        sector: stock.sector || meta?.sector || 'NSE Equity',
        candles,
      };
    });

  const perfList = normalizedList.map((item) => {
    const closes = item.candles.map((c) => c.close);
    const prevIdx = Math.max(0, closes.length - 22);
    const baseClose = closes[prevIdx] || closes[0] || 1;
    return {
      symbol: item.symbol,
      rawScore: computeRawPerfScore(closes),
      mom1M: (closes[closes.length - 1] - baseClose) / baseClose,
    };
  });

  const sortedByScore = [...perfList].sort((a, b) => a.rawScore - b.rawScore);
  const rsMap = new Map();
  const n = sortedByScore.length;
  sortedByScore.forEach((item, idx) => {
    const rating = n <= 1 ? 50 : Math.round(12 + (idx / (n - 1)) * 86);
    rsMap.set(item.symbol, { rsRating: rating, rawScore: item.rawScore, mom1M: item.mom1M });
  });

  return normalizedList.map(({ raw: stock, symbol, name, sector, candles }) => {
    const len = candles.length;
    const closes = candles.map((c) => c.close);
    const volumes = candles.map((c) => c.volume);

    // Base indicator series computed from candles, with optional direct overrides from EOD JSON
    const sma20 = alignSeriesOverride(stock.sma20, calculateSMA(closes, 20), candles);
    const sma50 = alignSeriesOverride(stock.sma50, calculateSMA(closes, 50), candles);
    const sma100 = alignSeriesOverride(stock.sma100, calculateSMA(closes, 100), candles);
    const sma200 = alignSeriesOverride(stock.sma200, calculateSMA(closes, 200), candles);

    const ema9 = alignSeriesOverride(stock.ema9, calculateEMA(closes, 9), candles);
    const ema20 = alignSeriesOverride(stock.ema20, calculateEMA(closes, 20), candles);
    const ema45 = alignSeriesOverride(stock.ema45, calculateEMA(closes, 45), candles);
    const ema50 = alignSeriesOverride(stock.ema50, calculateEMA(closes, 50), candles);
    const ema200 = alignSeriesOverride(stock.ema200, calculateEMA(closes, 200), candles);

    const bbCalc = calculateBollingerBands(closes, 20, 2);
    const bbUpper = alignSeriesOverride(stock.bbUpper, bbCalc.upper, candles);
    const bbLower = alignSeriesOverride(stock.bbLower, bbCalc.lower, candles);
    const bbMiddle = alignSeriesOverride(stock.bbMiddle, bbCalc.middle, candles);
    const bbBandwidth = [...bbCalc.bandwidth];
    if (len > 0 && bbUpper[len - 1] != null && bbLower[len - 1] != null) {
      if (bbMiddle[len - 1] == null) {
        bbMiddle[len - 1] = +((bbUpper[len - 1] + bbLower[len - 1]) / 2).toFixed(2);
      }
      if (bbMiddle[len - 1] > 0) {
        bbBandwidth[len - 1] = +((bbUpper[len - 1] - bbLower[len - 1]) / bbMiddle[len - 1]).toFixed(4);
      }
    }

    const volMa20 = calculateSMA(volumes, 20);

    const rsi14 = alignSeriesOverride(stock.rsi14, calculateRSI(closes, 14), candles);
    const rsiEma45 = alignSeriesOverride(
      stock.rsi14Ema45 ?? stock.rsiEma45,
      calculateEMA(rsi14, 45),
      candles
    );
    const weeklyRsi = toFiniteNum(stock.weeklyRsi) ?? calculateWeeklyRSI(candles, 14);

    // Hilega Milega (NK Sir): RSI(9), 3 EMA of RSI(9), 21 WMA of RSI(9)
    const rsi9 = alignSeriesOverride(stock.rsi9, calculateRSI(closes, 9), candles);
    const hmEma3 = alignSeriesOverride(stock.hmEma3, calculateEMA(rsi9, 3), candles);
    const hmWma21 = alignSeriesOverride(stock.hmWma21, calculateWMA(rsi9, 21), candles);

    const latest = candles[len - 1];
    const prev = len >= 2 ? candles[len - 2] : latest;

    const closePrice = toFiniteNum(stock.price ?? stock.close) ?? latest.close;
    const changePct =
      toFiniteNum(stock.changePct) ??
      (prev.close > 0 ? +(((closePrice - prev.close) / prev.close) * 100).toFixed(2) : 0);

    const latestVolume = toFiniteNum(stock.volume) ?? latest.volume;
    const last20 = candles.slice(-20);
    const computedAvgVol20 =
      last20.reduce((acc, c) => acc + c.volume, 0) / Math.max(1, last20.length);

    const providedVolRatio = toFiniteNum(stock.volRatio ?? stock.volMultiplier);
    const volMultiplier =
      providedVolRatio != null
        ? +providedVolRatio.toFixed(2)
        : computedAvgVol20 > 0
        ? +(latestVolume / computedAvgVol20).toFixed(2)
        : 1;
    const avgVol20 =
      computedAvgVol20 > 0
        ? computedAvgVol20
        : volMultiplier > 0
        ? latestVolume / volMultiplier
        : latestVolume;

    const avgTurnoverCr = +(
      last20.reduce((acc, c) => acc + (c.close * c.volume) / 1e7, 0) / Math.max(1, last20.length)
    ).toFixed(1);

    const computedHigh52W = Math.max(...candles.slice(-252).map((c) => c.high));
    const high52W = toFiniteNum(stock.high52w ?? stock.high52W) ?? computedHigh52W;
    const prevSlice52 = candles.slice(-252, -1);
    const prev52WHigh = prevSlice52.length
      ? Math.max(...prevSlice52.map((c) => c.high))
      : high52W;

    const rawDist52 = toFiniteNum(stock.distFrom52wPct ?? stock.distFrom52WHighPct);
    let distFrom52WHighPct;
    if (rawDist52 != null) {
      // Normalize positive distance-below-high (e.g. 1.5 when close < high52W) to negative (-1.5)
      distFrom52WHighPct =
        rawDist52 > 0 && closePrice < high52W * 0.9999
          ? +(-rawDist52).toFixed(2)
          : +rawDist52.toFixed(2);
    } else {
      distFrom52WHighPct =
        high52W > 0 ? +(((closePrice - high52W) / high52W) * 100).toFixed(2) : 0;
    }

    const recent10Low = Math.min(...candles.slice(-10).map((c) => c.low));
    const swingLowStop =
      toFiniteNum(stock.swingLowStop) ?? +(recent10Low * 0.997).toFixed(2);

    const cSma20 = toFiniteNum(stock.sma20) ?? sma20[len - 1];
    const cSma50 = toFiniteNum(stock.sma50) ?? sma50[len - 1];
    const cSma100 = toFiniteNum(stock.sma100) ?? sma100[len - 1];
    const cSma200 = toFiniteNum(stock.sma200) ?? sma200[len - 1];

    const cEma9 = toFiniteNum(stock.ema9) ?? ema9[len - 1];
    const pEma9 = len >= 2 ? ema9[len - 2] : cEma9;
    const cEma20 = toFiniteNum(stock.ema20) ?? ema20[len - 1];
    const pEma20 = len >= 2 ? ema20[len - 2] : cEma20;
    const cEma45 = toFiniteNum(stock.ema45) ?? ema45[len - 1];
    const pEma45 = len >= 2 ? ema45[len - 2] : cEma45;
    const cEma50 = toFiniteNum(stock.ema50) ?? ema50[len - 1];
    const cEma200 = toFiniteNum(stock.ema200) ?? ema200[len - 1];
    const pastEma200 = ema200[Math.max(0, len - 21)] ?? cEma200;

    const dailyRsi = toFiniteNum(stock.rsi14) ?? rsi14[len - 1] ?? 50;
    const currentRsiEma45 =
      toFiniteNum(stock.rsi14Ema45 ?? stock.rsiEma45) ?? rsiEma45[len - 1] ?? 50;

    const cRsi9 = toFiniteNum(stock.rsi9) ?? rsi9[len - 1] ?? 50;
    const cHmEma3 = toFiniteNum(stock.hmEma3) ?? hmEma3[len - 1] ?? 50;
    const pHmEma3 = (len >= 2 ? hmEma3[len - 2] : null) ?? cHmEma3;
    const p2HmEma3 = (len >= 3 ? hmEma3[len - 3] : null) ?? pHmEma3;
    const cHmWma21 = toFiniteNum(stock.hmWma21) ?? hmWma21[len - 1] ?? 50;
    const pHmWma21 = (len >= 2 ? hmWma21[len - 2] : null) ?? cHmWma21;
    const p2HmWma21 = (len >= 3 ? hmWma21[len - 3] : null) ?? pHmWma21;

    const cBbUpper = toFiniteNum(stock.bbUpper) ?? bbUpper[len - 1];
    const cBbLower = toFiniteNum(stock.bbLower) ?? bbLower[len - 1];
    const cBbMiddle =
      toFiniteNum(stock.bbMiddle) ??
      bbMiddle[len - 1] ??
      (cBbUpper != null && cBbLower != null ? +((cBbUpper + cBbLower) / 2).toFixed(2) : null);
    const cBbWidth =
      bbBandwidth[len - 1] ??
      (cBbUpper != null && cBbLower != null && cBbMiddle > 0
        ? +((cBbUpper - cBbLower) / cBbMiddle).toFixed(4)
        : null);

    const recent20Widths = bbBandwidth.slice(-20).filter((w) => w != null);
    const min20BbWidth = recent20Widths.length ? Math.min(...recent20Widths) : cBbWidth;

    const trend50Ref = cEma50 ?? cSma50;
    const trend200Ref = cEma200 ?? cSma200;
    const isAbove50Ema = trend50Ref != null ? closePrice > trend50Ref : false;
    const isStage2Uptrend =
      trend50Ref != null &&
      trend200Ref != null &&
      closePrice > trend50Ref &&
      trend50Ref > trend200Ref &&
      (pastEma200 == null || trend200Ref >= pastEma200 * 0.995);

    // 1. 45 EMA Pullback
    const recentTouchEma45 =
      (len >= 2 && prev.close <= (pEma45 ?? prev.close) * 1.015) ||
      (len >= 3 && candles[len - 3].close <= (ema45[len - 3] ?? candles[len - 3].close) * 1.015) ||
      (len >= 4 && candles[len - 4].close <= (ema45[len - 4] ?? candles[len - 4].close) * 1.015);
    const crossedAboveEma45 =
      cEma45 != null &&
      pEma45 != null &&
      closePrice >= cEma45 * 0.996 &&
      recentTouchEma45;

    const matchesPullback =
      weeklyRsi > 50 &&
      dailyRsi >= 50 &&
      dailyRsi <= 60 &&
      crossedAboveEma45 &&
      volMultiplier >= 1.25;

    // 2. 52W / Multi-Year High & Breakout
    const near52WHigh = distFrom52WHighPct >= -2.0;
    const matchesBreakout =
      (closePrice >= prev52WHigh * 0.988 || distFrom52WHighPct >= -1.5) &&
      volMultiplier >= 1.5 &&
      changePct > 1.2;

    // 3. RS Rating (honor real EOD rsRating when provided)
    const computedRs = rsMap.get(symbol) || { rsRating: 50, rawScore: 0, mom1M: 0 };
    const providedRs = toFiniteNum(stock.rsRating);
    const rsRating =
      providedRs != null
        ? Math.max(1, Math.min(99, Math.round(providedRs)))
        : computedRs.rsRating;
    const { rawScore, mom1M } = computedRs;
    const matchesRsLeader = rsRating >= 85;

    // 4. VCP / Pocket Pivot
    const last10 = candles.slice(-10);
    const prior15 = candles.slice(-25, -10);
    const rangeSample10 = last10.length > 1 ? last10.slice(0, -1) : last10;
    const avgRange10 =
      rangeSample10.reduce((acc, c) => acc + (c.close > 0 ? (c.high - c.low) / c.close : 0), 0) /
      Math.max(1, rangeSample10.length);
    const avgRangePrior15 = prior15.length
      ? prior15.reduce((acc, c) => acc + (c.close > 0 ? (c.high - c.low) / c.close : 0), 0) /
        prior15.length
      : avgRange10;
    const rangeContraction = prior15.length > 0 && avgRange10 < avgRangePrior15 * 0.85;

    const prior10DownVols = candles
      .slice(-11, -1)
      .filter((c) => c.close < c.open)
      .map((c) => c.volume);
    const maxDownVol = prior10DownVols.length ? Math.max(...prior10DownVols) : 0;
    const isPocketPivot =
      latest.close > latest.open && latestVolume > maxDownVol && volMultiplier >= 1.25;
    const matchesVcpPocketPivot = rangeContraction && isPocketPivot;

    // 5. SMA & Golden Cross booleans
    const aboveSma20 = cSma20 != null && closePrice > cSma20;
    const aboveSma50 = cSma50 != null && closePrice > cSma50;
    const aboveSma100 = cSma100 != null && closePrice > cSma100;
    const aboveSma200 = cSma200 != null && closePrice > cSma200;
    const goldenCross50_200 = cSma50 != null && cSma200 != null && cSma50 > cSma200;

    // 6. EMA & 9/20 Crossover booleans
    const aboveEma9 = cEma9 != null && closePrice > cEma9;
    const aboveEma20 = cEma20 != null && closePrice > cEma20;
    const aboveEma45 = cEma45 != null && closePrice > cEma45;
    const aboveEma200 = cEma200 != null && closePrice > cEma200;
    const ema9_20BullCross =
      cEma9 != null &&
      cEma20 != null &&
      cEma9 > cEma20 &&
      ((pEma9 != null && pEma20 != null && pEma9 <= pEma20) ||
        (len >= 3 &&
          ema9[len - 3] != null &&
          ema20[len - 3] != null &&
          ema9[len - 3] <= ema20[len - 3]));

    // 7. Bollinger Band Signals
    const bbUpperBreakout =
      cBbUpper != null &&
      closePrice > cBbUpper &&
      volMultiplier >= 1.25;
    const bbSqueeze =
      cBbWidth != null &&
      min20BbWidth != null &&
      (cBbWidth <= min20BbWidth * 1.15 || cBbWidth <= 0.055);
    const bbMiddleBounce =
      cBbMiddle != null &&
      latest.low <= cBbMiddle * 1.015 &&
      closePrice > cBbMiddle;

    // 8. Hilega Milega (NK Sir) Signals
    const hmBreakout =
      aboveEma20 &&
      cHmEma3 > cHmWma21 &&
      (pHmEma3 <= pHmWma21 || p2HmEma3 <= p2HmWma21);
    const hmSweetSpot =
      aboveEma20 &&
      cRsi9 >= 50 &&
      cRsi9 <= 70 &&
      cHmEma3 > cHmWma21;

    let setupBadge = 'Consolidating';
    if (matchesBreakout) setupBadge = '52W Breakout';
    else if (matchesPullback) setupBadge = '45 EMA Pullback';
    else if (hmBreakout) setupBadge = 'Hilega Milega';
    else if (matchesVcpPocketPivot) setupBadge = 'VCP Pocket Pivot';
    else if (bbSqueeze) setupBadge = 'BB Squeeze';
    else if (matchesRsLeader) setupBadge = 'RS Leader';
    else if (isStage2Uptrend) setupBadge = 'Stage 2 Trend';

    return {
      symbol,
      name,
      sector,
      candles,
      indicators: {
        sma20,
        sma50,
        sma100,
        sma200,
        ema9,
        ema20,
        ema45,
        ema50,
        ema200,
        bbUpper,
        bbMiddle,
        bbLower,
        bbBandwidth,
        volMa20,
        rsi14,
        rsiEma45,
        rsi9,
        hmEma3,
        hmWma21,
      },
      metrics: {
        close: closePrice,
        prevClose: prev.close,
        changePct,
        volume: latestVolume,
        avgVol20: Math.round(avgVol20),
        volMultiplier,
        avgTurnoverCr,
        dailyRsi,
        rsiEma45: currentRsiEma45,
        weeklyRsi,
        rsi9: cRsi9,
        hmEma3: cHmEma3,
        hmWma21: cHmWma21,
        rsRating,
        rawPerfScore: rawScore,
        mom1M,
        high52W,
        distFrom52WHighPct,
        swingLowStop,
        sma20: cSma20,
        sma50: cSma50,
        sma100: cSma100,
        sma200: cSma200,
        ema9: cEma9,
        ema20: cEma20,
        ema45: cEma45,
        ema50: cEma50,
        ema200: cEma200,
        bbUpper: cBbUpper,
        bbMiddle: cBbMiddle,
        bbLower: cBbLower,
        bbWidth: cBbWidth,
        isAbove50Ema,
        isStage2Uptrend,
      },
      setups: {
        allLiquid: isExternal ? true : avgTurnoverCr >= 10,
        pullback45Ema: matchesPullback,
        multiYearBreakout: matchesBreakout,
        near52WHigh,
        rsLeader: matchesRsLeader,
        vcpPocketPivot: matchesVcpPocketPivot,
        aboveSma20,
        aboveSma50,
        aboveSma100,
        aboveSma200,
        goldenCross50_200,
        aboveEma9,
        aboveEma20,
        aboveEma45,
        aboveEma200,
        ema9_20BullCross,
        bbUpperBreakout,
        bbSqueeze,
        bbMiddleBounce,
        hmBreakout,
        hmSweetSpot,
      },
      setupBadge,
    };
  });
}

function computeMarketBreadth(stocks) {
  const total = stocks.length || 1;
  const above50Count = stocks.filter((s) => s.metrics.isAbove50Ema).length;
  const advCount = stocks.filter((s) => s.metrics.changePct > 0).length;
  const decCount = stocks.filter((s) => s.metrics.changePct < 0).length;
  const pctAbove50Ema = Math.round((above50Count / total) * 100);
  const advDecRatio = decCount === 0 ? `${advCount}:0` : `${(advCount / decCount).toFixed(2)}`;

  return {
    niftyClose: 24685.4,
    niftyChangePct: 0.74,
    niftyTrend: pctAbove50Ema >= 55 ? 'Above 50 EMA • Bullish' : 'Near 50 EMA • Neutral',
    niftyBullish: pctAbove50Ema >= 55,
    pctAbove50Ema,
    advCount,
    decCount,
    advDecRatio,
  };
}

export function computeSectorRotation(stocks) {
  const bySector = new Map();
  stocks.forEach((s) => {
    if (!bySector.has(s.sector)) bySector.set(s.sector, []);
    bySector.get(s.sector).push(s);
  });

  const avgUniverseScore =
    stocks.reduce((acc, s) => acc + s.metrics.rawPerfScore, 0) / (stocks.length || 1);
  const avgUniverseMom =
    stocks.reduce((acc, s) => acc + s.metrics.mom1M, 0) / (stocks.length || 1);

  const sectors = [];
  bySector.forEach((items, sectorName) => {
    const sortedRs = [...items].map((i) => i.metrics.rsRating).sort((a, b) => a - b);
    const mid = Math.floor(sortedRs.length / 2);
    const medianRs =
      sortedRs.length % 2 === 0
        ? Math.round((sortedRs[mid - 1] + sortedRs[mid]) / 2)
        : sortedRs[mid];

    const stage2Count = items.filter((i) => i.metrics.isStage2Uptrend).length;
    const stage2Pct = Math.round((stage2Count / items.length) * 100);

    const avgPerf = items.reduce((a, b) => a + b.metrics.rawPerfScore, 0) / items.length;
    const avgMom = items.reduce((a, b) => a + b.metrics.mom1M, 0) / items.length;
    const avgChange = +(
      items.reduce((a, b) => a + b.metrics.changePct, 0) / items.length
    ).toFixed(2);

    const rsRatio = +(100 + (avgPerf - avgUniverseScore) * 18).toFixed(2);
    const rsMomentum = +(100 + (avgMom - avgUniverseMom) * 65).toFixed(2);

    let quadrant = 'Lagging';
    if (rsRatio >= 100 && rsMomentum >= 100) quadrant = 'Leading';
    else if (rsRatio < 100 && rsMomentum >= 100) quadrant = 'Improving';
    else if (rsRatio >= 100 && rsMomentum < 100) quadrant = 'Weakening';

    sectors.push({
      sector: sectorName,
      displayName:
        sectorName === 'Consumer EMS'
          ? 'Consumer & EMS'
          : sectorName === 'NSE Equity'
          ? 'NSE Equities'
          : `Nifty ${sectorName}`,
      stockCount: items.length,
      medianRs,
      stage2Pct,
      avgChange,
      rsRatio,
      rsMomentum,
      quadrant,
      topStock: [...items].sort((a, b) => b.metrics.rsRating - a.metrics.rsRating)[0]?.symbol,
    });
  });

  return sectors.sort((a, b) => b.medianRs - a.medianRs || b.stage2Pct - a.stage2Pct);
}

export function buildBuiltinDataset() {
  const rawStocks = NSE_UNIVERSE.map((spec) => ({
    ...spec,
    candles: generateStockCandles(spec),
  }));

  const stocks = enrichStocks(rawStocks, { isExternal: false });
  const breadth = computeMarketBreadth(stocks);
  const sectors = computeSectorRotation(stocks);
  const eodStamp = `${TRADING_DATES[TRADING_DATES.length - 1]} 15:30 IST`;

  return {
    source: 'Built-in Fallback (29 Stocks)',
    isLiveFeed: false,
    lastUpdated: eodStamp,
    lastUpdatedEod: eodStamp,
    stocks,
    breadth,
    sectors,
  };
}

export async function fetchEodDataset(overrideUrl) {
  const targetUrl = (overrideUrl !== undefined ? overrideUrl : getConfiguredEodUrl()).trim();
  if (!targetUrl) {
    return buildBuiltinDataset();
  }

  const requestUrl = withCacheBuster(targetUrl);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);

  try {
    const res = await fetch(requestUrl, {
      cache: 'no-store',
      signal: controller.signal,
    });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    const payload = await res.json();
    if (!payload || !Array.isArray(payload.stocks) || payload.stocks.length === 0) {
      throw new Error('Invalid EOD JSON schema');
    }

    const stocks = enrichStocks(payload.stocks, { isExternal: true });
    if (stocks.length === 0) {
      throw new Error('No valid stocks in EOD JSON');
    }

    const lastUpdated =
      payload.lastUpdated ||
      payload.lastUpdatedEod ||
      new Date().toISOString().slice(0, 10);

    return {
      source: targetUrl === EOD_DATA_URL ? 'Live EOD (/screener_data.json)' : 'External EOD Feed',
      isLiveFeed: true,
      lastUpdated: String(lastUpdated),
      lastUpdatedEod: String(lastUpdated),
      stocks,
      breadth: payload.breadth || computeMarketBreadth(stocks),
      sectors: computeSectorRotation(stocks),
    };
  } finally {
    clearTimeout(timer);
  }
}
