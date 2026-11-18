/**
 * SwingPulse NSE - Plug-and-Play EOD Data & Technical Indicator Engine
 *
 * Computes SMA(20,50,100,200), EMA(9,20,45,50,200), Bollinger Bands(20,2),
 * Volume MA(20), RSI(14), Weekly RSI(14), Hilega Milega (RSI9, 3EMA, 21WMA),
 * and RS Rating (1-99) from 260 daily OHLCV candles per NSE stock.
 */

export const EOD_DATA_URL = '';
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

function enrichStocks(rawStocks) {
  const perfList = rawStocks.map((stock) => {
    const closes = stock.candles.map((c) => c.close);
    return {
      symbol: stock.symbol,
      rawScore: computeRawPerfScore(closes),
      mom1M:
        (closes[closes.length - 1] - closes[Math.max(0, closes.length - 22)]) /
        closes[Math.max(0, closes.length - 22)],
    };
  });

  const sortedByScore = [...perfList].sort((a, b) => a.rawScore - b.rawScore);
  const rsMap = new Map();
  const n = sortedByScore.length;
  sortedByScore.forEach((item, idx) => {
    const rating = n === 1 ? 50 : Math.round(12 + (idx / (n - 1)) * 86);
    rsMap.set(item.symbol, { rsRating: rating, rawScore: item.rawScore, mom1M: item.mom1M });
  });

  return rawStocks.map((stock) => {
    const candles = stock.candles;
    const len = candles.length;
    const closes = candles.map((c) => c.close);
    const volumes = candles.map((c) => c.volume);

    // SMAs
    const sma20 = calculateSMA(closes, 20);
    const sma50 = calculateSMA(closes, 50);
    const sma100 = calculateSMA(closes, 100);
    const sma200 = calculateSMA(closes, 200);

    // EMAs
    const ema9 = calculateEMA(closes, 9);
    const ema20 = calculateEMA(closes, 20);
    const ema45 = calculateEMA(closes, 45);
    const ema50 = calculateEMA(closes, 50);
    const ema200 = calculateEMA(closes, 200);

    // Bollinger Bands (20, 2)
    const bb = calculateBollingerBands(closes, 20, 2);

    // Volume MA (20)
    const volMa20 = calculateSMA(volumes, 20);

    // RSI(14) + EMA(45) of RSI(14) + Weekly RSI(14)
    const rsi14 = calculateRSI(closes, 14);
    const rsiEma45 = calculateEMA(rsi14, 45);
    const weeklyRsi = calculateWeeklyRSI(candles, 14);

    // Hilega Milega (NK Sir): RSI(9), 3 EMA of RSI(9), 21 WMA of RSI(9)
    const rsi9 = calculateRSI(closes, 9);
    const hmEma3 = calculateEMA(rsi9, 3);
    const hmWma21 = calculateWMA(rsi9, 21);

    const latest = candles[len - 1];
    const prev = candles[len - 2];
    const changePct = +(((latest.close - prev.close) / prev.close) * 100).toFixed(2);

    const last20 = candles.slice(-20);
    const avgVol20 = last20.reduce((acc, c) => acc + c.volume, 0) / last20.length;
    const volMultiplier = +(latest.volume / avgVol20).toFixed(2);
    const avgTurnoverCr = +(
      last20.reduce((acc, c) => acc + (c.close * c.volume) / 1e7, 0) / last20.length
    ).toFixed(1);

    const high52W = Math.max(...candles.slice(-252).map((c) => c.high));
    const prev52WHigh = Math.max(...candles.slice(-252, -1).map((c) => c.high));
    const distFrom52WHighPct = +(((latest.close - high52W) / high52W) * 100).toFixed(2);

    const recent10Low = Math.min(...candles.slice(-10).map((c) => c.low));
    const swingLowStop = +(recent10Low * 0.997).toFixed(2);

    const cSma20 = sma20[len - 1];
    const cSma50 = sma50[len - 1];
    const cSma100 = sma100[len - 1];
    const cSma200 = sma200[len - 1];

    const cEma9 = ema9[len - 1];
    const pEma9 = ema9[len - 2];
    const cEma20 = ema20[len - 1];
    const pEma20 = ema20[len - 2];
    const cEma45 = ema45[len - 1];
    const pEma45 = ema45[len - 2];
    const cEma50 = ema50[len - 1];
    const cEma200 = ema200[len - 1];
    const pastEma200 = ema200[Math.max(0, len - 21)] ?? cEma200;

    const dailyRsi = rsi14[len - 1] ?? 50;
    const currentRsiEma45 = rsiEma45[len - 1] ?? 50;

    const cRsi9 = rsi9[len - 1] ?? 50;
    const cHmEma3 = hmEma3[len - 1] ?? 50;
    const pHmEma3 = hmEma3[len - 2] ?? 50;
    const p2HmEma3 = hmEma3[len - 3] ?? 50;
    const cHmWma21 = hmWma21[len - 1] ?? 50;
    const pHmWma21 = hmWma21[len - 2] ?? 50;
    const p2HmWma21 = hmWma21[len - 3] ?? 50;

    const cBbUpper = bb.upper[len - 1];
    const cBbMiddle = bb.middle[len - 1];
    const cBbLower = bb.lower[len - 1];
    const cBbWidth = bb.bandwidth[len - 1];

    const recent20Widths = bb.bandwidth.slice(-20).filter((w) => w != null);
    const min20BbWidth = recent20Widths.length ? Math.min(...recent20Widths) : cBbWidth;

    const isAbove50Ema = cEma50 != null ? latest.close > cEma50 : false;
    const isStage2Uptrend =
      cEma50 != null &&
      cEma200 != null &&
      latest.close > cEma50 &&
      cEma50 > cEma200 &&
      cEma200 >= pastEma200 * 0.995;

    // 1. 45 EMA Pullback
    const recentTouchEma45 =
      prev.close <= (pEma45 ?? prev.close) * 1.015 ||
      candles[len - 3].close <= (ema45[len - 3] ?? candles[len - 3].close) * 1.015 ||
      candles[len - 4].close <= (ema45[len - 4] ?? candles[len - 4].close) * 1.015;
    const crossedAboveEma45 =
      cEma45 != null &&
      pEma45 != null &&
      latest.close >= cEma45 * 0.996 &&
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
      (latest.close >= prev52WHigh * 0.988 || distFrom52WHighPct >= -1.5) &&
      volMultiplier >= 1.5 &&
      changePct > 1.2;

    // 3. RS Rating
    const { rsRating, rawScore, mom1M } = rsMap.get(stock.symbol);
    const matchesRsLeader = rsRating >= 85;

    // 4. VCP / Pocket Pivot
    const last10 = candles.slice(-10);
    const prior15 = candles.slice(-25, -10);
    const avgRange10 =
      last10.slice(0, 9).reduce((acc, c) => acc + (c.high - c.low) / c.close, 0) / 9;
    const avgRangePrior15 =
      prior15.reduce((acc, c) => acc + (c.high - c.low) / c.close, 0) / prior15.length;
    const rangeContraction = avgRange10 < avgRangePrior15 * 0.85;

    const prior10DownVols = candles
      .slice(-11, -1)
      .filter((c) => c.close < c.open)
      .map((c) => c.volume);
    const maxDownVol = prior10DownVols.length ? Math.max(...prior10DownVols) : 0;
    const isPocketPivot =
      latest.close > latest.open && latest.volume > maxDownVol && volMultiplier >= 1.25;
    const matchesVcpPocketPivot = rangeContraction && isPocketPivot;

    // 5. SMA & Golden Cross booleans
    const aboveSma20 = cSma20 != null && latest.close > cSma20;
    const aboveSma50 = cSma50 != null && latest.close > cSma50;
    const aboveSma100 = cSma100 != null && latest.close > cSma100;
    const aboveSma200 = cSma200 != null && latest.close > cSma200;
    const goldenCross50_200 = cSma50 != null && cSma200 != null && cSma50 > cSma200;

    // 6. EMA & 9/20 Crossover booleans
    const aboveEma9 = cEma9 != null && latest.close > cEma9;
    const aboveEma20 = cEma20 != null && latest.close > cEma20;
    const aboveEma45 = cEma45 != null && latest.close > cEma45;
    const aboveEma200 = cEma200 != null && latest.close > cEma200;
    const ema9_20BullCross =
      cEma9 != null &&
      cEma20 != null &&
      cEma9 > cEma20 &&
      (pEma9 <= pEma20 ||
        (ema9[len - 3] != null &&
          ema20[len - 3] != null &&
          ema9[len - 3] <= ema20[len - 3]));

    // 7. Bollinger Band Signals
    const bbUpperBreakout =
      cBbUpper != null &&
      latest.close > cBbUpper &&
      volMultiplier >= 1.25;
    const bbSqueeze =
      cBbWidth != null &&
      min20BbWidth != null &&
      (cBbWidth <= min20BbWidth * 1.15 || cBbWidth <= 0.055);
    const bbMiddleBounce =
      cBbMiddle != null &&
      latest.low <= cBbMiddle * 1.015 &&
      latest.close > cBbMiddle;

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
      symbol: stock.symbol,
      name: stock.name,
      sector: stock.sector,
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
        bbUpper: bb.upper,
        bbMiddle: bb.middle,
        bbLower: bb.lower,
        bbBandwidth: bb.bandwidth,
        volMa20,
        rsi14,
        rsiEma45,
        rsi9,
        hmEma3,
        hmWma21,
      },
      metrics: {
        close: latest.close,
        prevClose: prev.close,
        changePct,
        volume: latest.volume,
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
        allLiquid: avgTurnoverCr >= 10,
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
      displayName: sectorName === 'Consumer EMS' ? 'Consumer & EMS' : `Nifty ${sectorName}`,
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

  const stocks = enrichStocks(rawStocks);
  const breadth = computeMarketBreadth(stocks);
  const sectors = computeSectorRotation(stocks);

  return {
    source: 'Built-in NSE Universe (29 Stocks)',
    lastUpdatedEod: `${TRADING_DATES[TRADING_DATES.length - 1]} 15:30 IST`,
    stocks,
    breadth,
    sectors,
  };
}

export async function fetchEodDataset(overrideUrl) {
  const targetUrl = (overrideUrl ?? getConfiguredEodUrl()).trim();
  if (!targetUrl) {
    return buildBuiltinDataset();
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8000);

  try {
    const res = await fetch(targetUrl, {
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

    const stocks = enrichStocks(payload.stocks);
    return {
      source: 'External EOD Feed',
      lastUpdatedEod: payload.lastUpdatedEod || new Date().toISOString().slice(0, 10),
      stocks,
      breadth: payload.breadth || computeMarketBreadth(stocks),
      sectors: computeSectorRotation(stocks),
    };
  } finally {
    clearTimeout(timer);
  }
}
