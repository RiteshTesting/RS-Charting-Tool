import {
  calculateSMA,
  calculateEMA,
  calculateWMA,
  calculateBollingerBands,
  calculateRSI,
} from '../services/dataService.js';

/**
 * Returns an ISO-8601 week key ("YYYY-Www") in UTC so Monday–Friday candles
 * always group into the same trading week regardless of local timezone.
 */
function getIsoWeekKey(dateStr) {
  const parts = String(dateStr).split('-');
  if (parts.length !== 3) return String(dateStr);
  const year = Number(parts[0]);
  const month = Number(parts[1]) - 1;
  const day = Number(parts[2]);
  const d = new Date(Date.UTC(year, month, day));
  if (Number.isNaN(d.getTime())) return String(dateStr);

  // ISO week date: Thursday in current week determines the ISO week year
  const dayNum = d.getUTCDay() || 7; // Mon=1 .. Sun=7
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const weekNo = Math.ceil(((d - yearStart) / 86400000 + 1) / 7);
  return `${d.getUTCFullYear()}-W${String(weekNo).padStart(2, '0')}`;
}

/**
 * Dynamically resamples daily OHLCV candles to 'D' (Daily), 'W' (ISO Weekly),
 * or 'M' (Monthly YYYY-MM) candles.
 */
export function resampleCandles(dailyCandles, timeframe = 'D') {
  if (!Array.isArray(dailyCandles) || dailyCandles.length === 0) return [];
  if (timeframe === 'D') return dailyCandles;

  const grouped = new Map();

  for (let i = 0; i < dailyCandles.length; i++) {
    const c = dailyCandles[i];
    if (!c || !c.time) continue;
    const key =
      timeframe === 'W'
        ? getIsoWeekKey(c.time)
        : String(c.time).slice(0, 7); // YYYY-MM

    const existing = grouped.get(key);
    if (!existing) {
      grouped.set(key, {
        time: c.time,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
        volume: Number(c.volume) || 0,
      });
    } else {
      existing.high = Math.max(existing.high, c.high);
      existing.low = Math.min(existing.low, c.low);
      existing.close = c.close;
      existing.volume += Number(c.volume) || 0;
      // Keep the latest trading date in that week/month so times remain strictly ascending
      existing.time = c.time;
    }
  }

  return Array.from(grouped.values()).map((bar) => ({
    time: bar.time,
    open: +bar.open.toFixed(2),
    high: +bar.high.toFixed(2),
    low: +bar.low.toFixed(2),
    close: +bar.close.toFixed(2),
    volume: Math.round(bar.volume),
  }));
}

/**
 * Computes all overlay and oscillator indicator arrays for an arbitrary candle array.
 */
export function computeIndicatorsForCandles(candles) {
  const closes = candles.map((c) => c.close);
  const volumes = candles.map((c) => c.volume);

  const sma20 = calculateSMA(closes, 20);
  const sma50 = calculateSMA(closes, 50);
  const sma200 = calculateSMA(closes, 200);
  const ema9 = calculateEMA(closes, 9);
  const ema20 = calculateEMA(closes, 20);
  const ema45 = calculateEMA(closes, 45);
  const bb = calculateBollingerBands(closes, 20, 2);
  const volMa20 = calculateSMA(volumes, 20);

  const rsi14 = calculateRSI(closes, 14);
  const rsiEma45 = calculateEMA(rsi14, 45);

  const rsi9 = calculateRSI(closes, 9);
  const hmEma3 = calculateEMA(rsi9, 3);
  const hmWma21 = calculateWMA(rsi9, 21);

  return {
    sma20,
    sma50,
    sma200,
    ema9,
    ema20,
    ema45,
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
  };
}

/**
 * Returns { candles, indicators } for a given stock and timeframe ('D' | 'W' | 'M').
 * For 'D', preserves the stock's precomputed (or real EOD JSON-overridden) daily series.
 * For 'W' and 'M', dynamically resamples OHLCV and recalculates all overlays & oscillators.
 */
export function computeChartSeriesForTimeframe(stock, timeframe = 'D') {
  if (!stock || !Array.isArray(stock.candles)) {
    return { candles: [], indicators: computeIndicatorsForCandles([]) };
  }
  if (timeframe === 'D') {
    return {
      candles: stock.candles,
      indicators: stock.indicators,
    };
  }
  const candles = resampleCandles(stock.candles, timeframe);
  const indicators = computeIndicatorsForCandles(candles);
  return { candles, indicators };
}

/**
 * Resolves a YYYY-MM-DD time string to an X pixel coordinate on the chart,
 * gracefully falling back to the nearest bar when switching between D/W/M timeframes.
 */
export function timeToXCoordinate(timeScale, candles, targetTime) {
  if (!timeScale || !targetTime || !candles || candles.length === 0) return null;

  const exact = timeScale.timeToCoordinate(targetTime);
  if (exact != null && Number.isFinite(exact)) return exact;

  // Binary search / linear lookup for nearest candle time string (ISO YYYY-MM-DD sorts lexicographically)
  let bestIdx = 0;
  if (targetTime <= candles[0].time) {
    bestIdx = 0;
  } else if (targetTime >= candles[candles.length - 1].time) {
    bestIdx = candles.length - 1;
  } else {
    let lo = 0;
    let hi = candles.length - 1;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (candles[mid].time < targetTime) lo = mid + 1;
      else hi = mid;
    }
    bestIdx = lo;
  }

  const mapped = timeScale.logicalToCoordinate(bestIdx);
  return mapped != null && Number.isFinite(mapped) ? mapped : null;
}

/**
 * Resolves an X pixel coordinate to a candle time string (clamped to valid bar bounds).
 */
export function xCoordinateToTime(timeScale, candles, x) {
  if (!timeScale || !candles || candles.length === 0) return null;

  const exact = timeScale.coordinateToTime(x);
  if (typeof exact === 'string') return exact;

  const logical = timeScale.coordinateToLogical(x);
  if (logical == null || !Number.isFinite(logical)) return candles[candles.length - 1].time;
  const clamped = Math.max(0, Math.min(candles.length - 1, Math.round(logical)));
  return candles[clamped].time;
}
