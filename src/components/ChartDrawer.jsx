import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createChart, ColorType, LineStyle } from 'lightweight-charts';
import {
  ArrowLeft,
  Star,
  Calculator,
  BookOpen,
  TrendingUp,
  TrendingDown,
  ShieldAlert,
  ChevronUp,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { formatINR, formatPct } from '../utils/formatters';

const RANGE_BARS = {
  '3M': 65,
  '6M': 125,
  '1Y': 260,
};

const CHART_INDICATORS_KEY = 'swingpulse_chart_indicators_v1';

const DEFAULT_INDICATORS = {
  sma20: false,
  sma50: false,
  sma200: false,
  ema9: false,
  ema20: true,
  ema45: true,
  bb20: false,
  volume: true,
  rsiPane: true,
  oscMode: 'rsi14', // 'rsi14' | 'hilega'
};

function loadChartIndicators() {
  try {
    const raw = localStorage.getItem(CHART_INDICATORS_KEY);
    if (raw !== null) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') {
        return { ...DEFAULT_INDICATORS, ...parsed };
      }
    }
  } catch {
    // ignore
  }
  return DEFAULT_INDICATORS;
}

// Custom lightweight-charts Primitive to render shaded channel between Bollinger Upper & Lower bands
class BollingerBandFillPrimitive {
  constructor(candles, bbUpper, bbLower, fillColor) {
    this._candles = candles;
    this._upper = bbUpper;
    this._lower = bbLower;
    this._fillColor = fillColor;
    this._visible = false;
    this._chart = null;
    this._series = null;
  }

  attached({ chart, series }) {
    this._chart = chart;
    this._series = series;
  }

  detached() {
    this._chart = null;
    this._series = null;
  }

  setVisible(visible) {
    this._visible = visible;
  }

  setFillColor(color) {
    this._fillColor = color;
  }

  setData(candles, bbUpper, bbLower) {
    this._candles = candles || [];
    this._upper = bbUpper || [];
    this._lower = bbLower || [];
  }

  paneViews() {
    const self = this;
    return [
      {
        zOrder: () => 'bottom',
        renderer: () => ({
          draw(target) {
            if (!self._visible || !self._chart || !self._series) return;
            target.useMediaCoordinateSpace(({ context }) => {
              try {
                const ts = self._chart.timeScale();
                const points = [];
                for (let i = 0; i < self._candles.length; i++) {
                  const u = self._upper[i];
                  const l = self._lower[i];
                  if (u == null || l == null) continue;
                  const x = ts.timeToCoordinate(self._candles[i].time);
                  const yU = self._series.priceToCoordinate(u);
                  const yL = self._series.priceToCoordinate(l);
                  if (x != null && yU != null && yL != null) {
                    points.push({ x, yU, yL });
                  }
                }
                if (points.length < 2) return;
                context.save();
                context.beginPath();
                context.moveTo(points[0].x, points[0].yU);
                for (let i = 1; i < points.length; i++) {
                  context.lineTo(points[i].x, points[i].yU);
                }
                for (let i = points.length - 1; i >= 0; i--) {
                  context.lineTo(points[i].x, points[i].yL);
                }
                context.closePath();
                context.fillStyle = self._fillColor;
                context.fill();
                context.restore();
              } catch {
                // ignore transient draw coordinates
              }
            });
          },
        }),
      },
    ];
  }
}

function getChartThemePalette(theme) {
  const isLight = theme === 'light';
  return {
    bg: isLight ? '#F8FAFC' : '#0B0E14',
    text: isLight ? '#334155' : '#94A3B8',
    grid: isLight ? '#E2E8F0' : '#18202D',
    border: isLight ? '#CBD5E1' : '#232A38',
    crosshair: isLight ? '#64748B' : '#475569',
    crosshairLabelBg: isLight ? '#0F172A' : '#1E293B',
    sma20: isLight ? '#0D9488' : '#2DD4BF',
    sma50: isLight ? '#2563EB' : '#60A5FA',
    sma200: isLight ? '#DC2626' : '#F87171',
    ema9: isLight ? '#EC4899' : '#F472B6',
    ema20: isLight ? '#0891B2' : '#22D3EE',
    ema45: isLight ? '#D97706' : '#F59E0B',
    bbEdge: isLight ? 'rgba(14, 165, 233, 0.85)' : 'rgba(56, 189, 248, 0.85)',
    bbMid: isLight ? 'rgba(100, 116, 139, 0.8)' : 'rgba(148, 163, 184, 0.75)',
    bbFill: isLight ? 'rgba(14, 165, 233, 0.08)' : 'rgba(56, 189, 248, 0.09)',
    volMa: isLight ? '#475569' : '#CBD5E1',
    rsiLine: isLight ? '#0284C7' : '#38BDF8',
    rsiEma: isLight ? '#D97706' : '#F59E0B',
    hmRsi9: isLight ? '#2563EB' : '#60A5FA',
    hmEma3: '#10B981',
    hmWma21: '#EF4444',
  };
}

export default function ChartDrawer({
  stock,
  theme = 'dark',
  isStarred,
  onToggleWatchlist,
  onClose,
  onSendToRiskCalc,
  onLogInJournal,
  listPosition = 1,
  listTotal = 1,
  listLabel = 'Screener',
  prevSymbol = null,
  nextSymbol = null,
  onPrev,
  onNext,
}) {
  const priceContainerRef = useRef(null);
  const rsiContainerRef = useRef(null);

  const priceChartRef = useRef(null);
  const rsiChartRef = useRef(null);
  const seriesRefs = useRef({});
  const bbPrimitiveRef = useRef(null);
  const swingSlLineRef = useRef(null);
  const isFirstDataRef = useRef(true);
  const prevBarCountRef = useRef(stock.candles.length);

  const [ind, setInd] = useState(loadChartIndicators);
  const [rangeKey, setRangeKey] = useState('6M');
  const oscModeRef = useRef(ind.oscMode);

  useEffect(() => {
    oscModeRef.current = ind.oscMode;
  }, [ind.oscMode]);

  const latestCandle = stock.candles[stock.candles.length - 1];
  const [hoverInfo, setHoverInfo] = useState(null);

  useEffect(() => {
    try {
      localStorage.setItem(CHART_INDICATORS_KEY, JSON.stringify(ind));
    } catch {
      // ignore
    }
  }, [ind]);

  const indicatorLookup = useMemo(() => {
    const map = new Map();
    stock.candles.forEach((c, idx) => {
      map.set(c.time, {
        candle: c,
        sma20: stock.indicators.sma20[idx],
        sma50: stock.indicators.sma50[idx],
        sma200: stock.indicators.sma200[idx],
        ema9: stock.indicators.ema9[idx],
        ema20: stock.indicators.ema20[idx],
        ema45: stock.indicators.ema45[idx],
        bbUpper: stock.indicators.bbUpper[idx],
        bbMiddle: stock.indicators.bbMiddle[idx],
        bbLower: stock.indicators.bbLower[idx],
        volMa20: stock.indicators.volMa20[idx],
        rsi14: stock.indicators.rsi14[idx],
        rsiEma45: stock.indicators.rsiEma45[idx],
        rsi9: stock.indicators.rsi9[idx],
        hmEma3: stock.indicators.hmEma3[idx],
        hmWma21: stock.indicators.hmWma21[idx],
      });
    });
    return map;
  }, [stock]);

  const indicatorLookupRef = useRef(indicatorLookup);
  useEffect(() => {
    indicatorLookupRef.current = indicatorLookup;
  }, [indicatorLookup]);

  // Keyboard navigation: ArrowDown/ArrowRight/Space -> Next, ArrowUp/ArrowLeft -> Prev, Escape -> Close
  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const tag = e.target?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown' || e.key === ' ' || e.code === 'Space') {
        e.preventDefault();
        onNext?.();
      } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
        e.preventDefault();
        onPrev?.();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose, onPrev, onNext]);

  // Initialize charts ONCE on mount so stepping between stocks is flicker-free
  useEffect(() => {
    const priceEl = priceContainerRef.current;
    const rsiEl = rsiContainerRef.current;
    if (!priceEl || !rsiEl) return;

    const pal = getChartThemePalette(theme);

    const commonChartOptions = {
      layout: {
        background: { type: ColorType.Solid, color: pal.bg },
        textColor: pal.text,
        fontSize: 11,
      },
      grid: {
        vertLines: { color: pal.grid },
        horzLines: { color: pal.grid },
      },
      crosshair: {
        mode: 0,
        vertLine: {
          color: pal.crosshair,
          width: 1,
          style: LineStyle.Dashed,
          labelBackgroundColor: pal.crosshairLabelBg,
        },
        horzLine: {
          color: pal.crosshair,
          width: 1,
          style: LineStyle.Dashed,
          labelBackgroundColor: pal.crosshairLabelBg,
        },
      },
      rightPriceScale: {
        borderColor: pal.border,
        minimumWidth: 56,
      },
      handleScroll: {
        mouseWheel: true,
        pressedMouseMove: true,
        horzTouchDrag: true,
        vertTouchDrag: false,
      },
      handleScale: {
        axisPressedMouseMove: true,
        mouseWheel: true,
        pinch: true,
      },
    };

    const priceChart = createChart(priceEl, {
      ...commonChartOptions,
      width: priceEl.clientWidth,
      height: priceEl.clientHeight,
      timeScale: {
        borderColor: pal.border,
        visible: !ind.rsiPane,
        rightOffset: 4,
      },
    });

    const rsiChart = createChart(rsiEl, {
      ...commonChartOptions,
      width: rsiEl.clientWidth,
      height: rsiEl.clientHeight,
      timeScale: {
        borderColor: pal.border,
        visible: true,
        timeVisible: false,
        rightOffset: 4,
      },
      rightPriceScale: {
        borderColor: pal.border,
        minimumWidth: 56,
        scaleMargins: { top: 0.1, bottom: 0.1 },
      },
    });

    priceChartRef.current = priceChart;
    rsiChartRef.current = rsiChart;

    const candleSeries = priceChart.addCandlestickSeries({
      upColor: '#10B981',
      downColor: '#EF4444',
      borderVisible: false,
      wickUpColor: '#10B981',
      wickDownColor: '#EF4444',
    });

    const volumeSeries = priceChart.addHistogramSeries({
      priceFormat: { type: 'volume' },
      priceScaleId: 'vol',
      visible: ind.volume,
    });
    priceChart.priceScale('vol').applyOptions({
      scaleMargins: { top: 0.78, bottom: 0 },
    });

    const volMaSeries = priceChart.addLineSeries({
      priceScaleId: 'vol',
      color: pal.volMa,
      lineWidth: 1,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.volume,
    });

    const sma20Series = priceChart.addLineSeries({
      color: pal.sma20,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.sma20,
    });
    const sma50Series = priceChart.addLineSeries({
      color: pal.sma50,
      lineWidth: 1.75,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.sma50,
    });
    const sma200Series = priceChart.addLineSeries({
      color: pal.sma200,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.sma200,
    });

    const ema9Series = priceChart.addLineSeries({
      color: pal.ema9,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.ema9,
    });
    const ema20Series = priceChart.addLineSeries({
      color: pal.ema20,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.ema20,
    });
    const ema45Series = priceChart.addLineSeries({
      color: pal.ema45,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.ema45,
    });

    const bbUpperSeries = priceChart.addLineSeries({
      color: pal.bbEdge,
      lineWidth: 1.25,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.bb20,
    });
    const bbMiddleSeries = priceChart.addLineSeries({
      color: pal.bbMid,
      lineWidth: 1,
      lineStyle: LineStyle.Dotted,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.bb20,
    });
    const bbLowerSeries = priceChart.addLineSeries({
      color: pal.bbEdge,
      lineWidth: 1.25,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: ind.bb20,
    });

    const bbPrimitive = new BollingerBandFillPrimitive([], [], [], pal.bbFill);
    bbPrimitive.setVisible(ind.bb20);
    try {
      bbUpperSeries.attachPrimitive(bbPrimitive);
    } catch {
      // fallback if primitive unsupported
    }
    bbPrimitiveRef.current = bbPrimitive;

    const isHilega = ind.oscMode === 'hilega';

    const rsi14Series = rsiChart.addLineSeries({
      color: pal.rsiLine,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: !isHilega,
      visible: !isHilega,
    });
    const rsiEma45Series = rsiChart.addLineSeries({
      color: pal.rsiEma,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: !isHilega,
      visible: !isHilega,
    });

    const rsi9Series = rsiChart.addLineSeries({
      color: pal.hmRsi9,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: isHilega,
      visible: isHilega,
    });
    const hmEma3Series = rsiChart.addLineSeries({
      color: pal.hmEma3,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: isHilega,
      visible: isHilega,
    });
    const hmWma21Series = rsiChart.addLineSeries({
      color: pal.hmWma21,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: isHilega,
      visible: isHilega,
    });

    [
      { price: 70, color: 'rgba(16, 185, 129, 0.7)', title: '70' },
      { price: 50, color: 'rgba(148, 163, 184, 0.65)', title: '50' },
      { price: 30, color: 'rgba(239, 68, 68, 0.7)', title: '30' },
    ].forEach((ref) => {
      rsi14Series.createPriceLine({
        price: ref.price,
        color: ref.color,
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        axisLabelVisible: true,
        title: ref.title,
      });
      hmEma3Series.createPriceLine({
        price: ref.price,
        color: ref.color,
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        axisLabelVisible: true,
        title: ref.title,
      });
    });

    seriesRefs.current = {
      candleSeries,
      volumeSeries,
      volMaSeries,
      sma20Series,
      sma50Series,
      sma200Series,
      ema9Series,
      ema20Series,
      ema45Series,
      bbUpperSeries,
      bbMiddleSeries,
      bbLowerSeries,
      rsi14Series,
      rsiEma45Series,
      rsi9Series,
      hmEma3Series,
      hmWma21Series,
    };

    let syncingRange = false;
    const onPriceRangeChange = (range) => {
      if (syncingRange || !range) return;
      syncingRange = true;
      rsiChart.timeScale().setVisibleLogicalRange(range);
      syncingRange = false;
    };
    const onRsiRangeChange = (range) => {
      if (syncingRange || !range) return;
      syncingRange = true;
      priceChart.timeScale().setVisibleLogicalRange(range);
      syncingRange = false;
    };

    priceChart.timeScale().subscribeVisibleLogicalRangeChange(onPriceRangeChange);
    rsiChart.timeScale().subscribeVisibleLogicalRangeChange(onRsiRangeChange);

    const onPriceCrosshair = (param) => {
      if (!param || !param.time) {
        setHoverInfo(null);
        rsiChart.clearCrosshairPosition();
        return;
      }
      const entry = indicatorLookupRef.current.get(param.time);
      if (entry) {
        setHoverInfo(entry);
        if (oscModeRef.current === 'hilega') {
          if (entry.hmEma3 != null) {
            rsiChart.setCrosshairPosition(entry.hmEma3, param.time, hmEma3Series);
          }
        } else if (entry.rsi14 != null) {
          rsiChart.setCrosshairPosition(entry.rsi14, param.time, rsi14Series);
        }
      }
    };

    const onRsiCrosshair = (param) => {
      if (!param || !param.time) {
        setHoverInfo(null);
        priceChart.clearCrosshairPosition();
        return;
      }
      const entry = indicatorLookupRef.current.get(param.time);
      if (entry) {
        setHoverInfo(entry);
        priceChart.setCrosshairPosition(entry.candle.close, param.time, candleSeries);
      }
    };

    priceChart.subscribeCrosshairMove(onPriceCrosshair);
    rsiChart.subscribeCrosshairMove(onRsiCrosshair);

    const ro = new ResizeObserver(() => {
      if (priceEl.clientWidth > 0 && priceEl.clientHeight > 0) {
        priceChart.applyOptions({ width: priceEl.clientWidth, height: priceEl.clientHeight });
      }
      if (rsiEl.clientWidth > 0 && rsiEl.clientHeight > 0) {
        rsiChart.applyOptions({ width: rsiEl.clientWidth, height: rsiEl.clientHeight });
      }
    });
    ro.observe(priceEl);
    ro.observe(rsiEl);

    return () => {
      ro.disconnect();
      priceChart.timeScale().unsubscribeVisibleLogicalRangeChange(onPriceRangeChange);
      rsiChart.timeScale().unsubscribeVisibleLogicalRangeChange(onRsiRangeChange);
      priceChart.unsubscribeCrosshairMove(onPriceCrosshair);
      rsiChart.unsubscribeCrosshairMove(onRsiCrosshair);
      priceChart.remove();
      rsiChart.remove();
      priceChartRef.current = null;
      rsiChartRef.current = null;
      seriesRefs.current = {};
      bbPrimitiveRef.current = null;
      swingSlLineRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Update series data in-place on stock switch while preserving zoom range & active indicators
  useEffect(() => {
    const s = seriesRefs.current;
    const priceChart = priceChartRef.current;
    const rsiChart = rsiChartRef.current;
    if (!s.candleSeries || !priceChart || !rsiChart) return;

    const prevRange = !isFirstDataRef.current
      ? priceChart.timeScale().getVisibleLogicalRange()
      : null;
    const prevBars = prevBarCountRef.current || stock.candles.length;

    const toLineData = (arr) =>
      stock.candles
        .map((c, idx) => (arr[idx] != null ? { time: c.time, value: arr[idx] } : null))
        .filter(Boolean);

    s.candleSeries.setData(
      stock.candles.map((c) => ({
        time: c.time,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
      }))
    );

    if (swingSlLineRef.current) {
      try {
        s.candleSeries.removePriceLine(swingSlLineRef.current);
      } catch {
        // ignore
      }
    }
    swingSlLineRef.current = s.candleSeries.createPriceLine({
      price: stock.metrics.swingLowStop,
      color: 'rgba(239, 68, 68, 0.65)',
      lineWidth: 1,
      lineStyle: LineStyle.Dotted,
      axisLabelVisible: true,
      title: 'SL',
    });

    s.volumeSeries.setData(
      stock.candles.map((c) => ({
        time: c.time,
        value: c.volume,
        color: c.close >= c.open ? 'rgba(16, 185, 129, 0.35)' : 'rgba(239, 68, 68, 0.35)',
      }))
    );
    s.volMaSeries.setData(toLineData(stock.indicators.volMa20));

    s.sma20Series.setData(toLineData(stock.indicators.sma20));
    s.sma50Series.setData(toLineData(stock.indicators.sma50));
    s.sma200Series.setData(toLineData(stock.indicators.sma200));

    s.ema9Series.setData(toLineData(stock.indicators.ema9));
    s.ema20Series.setData(toLineData(stock.indicators.ema20));
    s.ema45Series.setData(toLineData(stock.indicators.ema45));

    s.bbUpperSeries.setData(toLineData(stock.indicators.bbUpper));
    s.bbMiddleSeries.setData(toLineData(stock.indicators.bbMiddle));
    s.bbLowerSeries.setData(toLineData(stock.indicators.bbLower));
    bbPrimitiveRef.current?.setData(
      stock.candles,
      stock.indicators.bbUpper,
      stock.indicators.bbLower
    );

    s.rsi14Series.setData(toLineData(stock.indicators.rsi14));
    s.rsiEma45Series.setData(toLineData(stock.indicators.rsiEma45));
    s.rsi9Series.setData(toLineData(stock.indicators.rsi9));
    s.hmEma3Series.setData(toLineData(stock.indicators.hmEma3));
    s.hmWma21Series.setData(toLineData(stock.indicators.hmWma21));

    const totalBars = stock.candles.length;
    let targetRange;
    if (isFirstDataRef.current || !prevRange) {
      const bars = RANGE_BARS[rangeKey] || 125;
      targetRange = {
        from: Math.max(0, totalBars - bars),
        to: totalBars + 2,
      };
    } else {
      const span = prevRange.to - prevRange.from;
      const rightOffset = prevBars - prevRange.to;
      targetRange = {
        from: totalBars - rightOffset - span,
        to: totalBars - rightOffset,
      };
    }

    priceChart.timeScale().setVisibleLogicalRange(targetRange);
    rsiChart.timeScale().setVisibleLogicalRange(targetRange);
    priceChart.clearCrosshairPosition();
    rsiChart.clearCrosshairPosition();
    setHoverInfo(null);

    isFirstDataRef.current = false;
    prevBarCountRef.current = totalBars;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stock]);

  // Apply dynamic theme updates
  useEffect(() => {
    const pal = getChartThemePalette(theme);
    const sharedUpdate = {
      layout: {
        background: { type: ColorType.Solid, color: pal.bg },
        textColor: pal.text,
      },
      grid: {
        vertLines: { color: pal.grid },
        horzLines: { color: pal.grid },
      },
      crosshair: {
        vertLine: { color: pal.crosshair, labelBackgroundColor: pal.crosshairLabelBg },
        horzLine: { color: pal.crosshair, labelBackgroundColor: pal.crosshairLabelBg },
      },
      rightPriceScale: { borderColor: pal.border },
      timeScale: { borderColor: pal.border },
    };

    priceChartRef.current?.applyOptions(sharedUpdate);
    rsiChartRef.current?.applyOptions(sharedUpdate);
    const s = seriesRefs.current;
    s.sma20Series?.applyOptions({ color: pal.sma20 });
    s.sma50Series?.applyOptions({ color: pal.sma50 });
    s.sma200Series?.applyOptions({ color: pal.sma200 });
    s.ema9Series?.applyOptions({ color: pal.ema9 });
    s.ema20Series?.applyOptions({ color: pal.ema20 });
    s.ema45Series?.applyOptions({ color: pal.ema45 });
    s.bbUpperSeries?.applyOptions({ color: pal.bbEdge });
    s.bbMiddleSeries?.applyOptions({ color: pal.bbMid });
    s.bbLowerSeries?.applyOptions({ color: pal.bbEdge });
    s.volMaSeries?.applyOptions({ color: pal.volMa });
    s.rsi14Series?.applyOptions({ color: pal.rsiLine });
    s.rsiEma45Series?.applyOptions({ color: pal.rsiEma });
    s.rsi9Series?.applyOptions({ color: pal.hmRsi9 });
    bbPrimitiveRef.current?.setFillColor(pal.bbFill);
  }, [theme]);

  // Apply indicator visibility toggles smoothly
  useEffect(() => {
    const s = seriesRefs.current;
    s.sma20Series?.applyOptions({ visible: ind.sma20 });
    s.sma50Series?.applyOptions({ visible: ind.sma50 });
    s.sma200Series?.applyOptions({ visible: ind.sma200 });
    s.ema9Series?.applyOptions({ visible: ind.ema9 });
    s.ema20Series?.applyOptions({ visible: ind.ema20 });
    s.ema45Series?.applyOptions({ visible: ind.ema45 });
    s.bbUpperSeries?.applyOptions({ visible: ind.bb20 });
    s.bbMiddleSeries?.applyOptions({ visible: ind.bb20 });
    s.bbLowerSeries?.applyOptions({ visible: ind.bb20 });
    bbPrimitiveRef.current?.setVisible(ind.bb20);
    s.volumeSeries?.applyOptions({ visible: ind.volume });
    s.volMaSeries?.applyOptions({ visible: ind.volume });

    const isHilega = ind.oscMode === 'hilega';
    s.rsi14Series?.applyOptions({ visible: !isHilega, lastValueVisible: !isHilega });
    s.rsiEma45Series?.applyOptions({ visible: !isHilega, lastValueVisible: !isHilega });
    s.rsi9Series?.applyOptions({ visible: isHilega, lastValueVisible: isHilega });
    s.hmEma3Series?.applyOptions({ visible: isHilega, lastValueVisible: isHilega });
    s.hmWma21Series?.applyOptions({ visible: isHilega, lastValueVisible: isHilega });

    priceChartRef.current?.applyOptions({
      timeScale: { visible: !ind.rsiPane },
    });
    const currentRange = priceChartRef.current?.timeScale().getVisibleLogicalRange();
    if (ind.rsiPane && currentRange) {
      rsiChartRef.current?.timeScale().setVisibleLogicalRange(currentRange);
    }
  }, [ind]);

  const handleSelectRange = (key) => {
    setRangeKey(key);
    const bars = RANGE_BARS[key] || 125;
    const totalBars = stock.candles.length;
    const nextRange = {
      from: Math.max(0, totalBars - bars),
      to: totalBars + 2,
    };
    priceChartRef.current?.timeScale().setVisibleLogicalRange(nextRange);
    rsiChartRef.current?.timeScale().setVisibleLogicalRange(nextRange);
  };

  const toggleInd = (key) => {
    setInd((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const activeBar = hoverInfo?.candle ?? latestCandle;
  const val = hoverInfo || {
    sma20: stock.metrics.sma20,
    sma50: stock.metrics.sma50,
    sma200: stock.metrics.sma200,
    ema9: stock.metrics.ema9,
    ema20: stock.metrics.ema20,
    ema45: stock.metrics.ema45,
    bbUpper: stock.metrics.bbUpper,
    bbMiddle: stock.metrics.bbMiddle,
    bbLower: stock.metrics.bbLower,
    volMa20: stock.metrics.avgVol20,
    rsi14: stock.metrics.dailyRsi,
    rsiEma45: stock.metrics.rsiEma45,
    rsi9: stock.metrics.rsi9,
    hmEma3: stock.metrics.hmEma3,
    hmWma21: stock.metrics.hmWma21,
  };
  const isUp = stock.metrics.changePct >= 0;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`${stock.symbol} interactive chart`}
      className="fixed inset-0 z-50 bg-[var(--bg-app)] text-[var(--text-primary)] flex flex-col overflow-hidden"
    >
      {/* Top Bar */}
      <header className="shrink-0 bg-[var(--bg-card)] border-b border-[var(--border-subtle)] px-2.5 py-2 flex items-center justify-between gap-1.5">
        <div className="flex items-center gap-1.5 min-w-0">
          <button
            type="button"
            onClick={onClose}
            aria-label="Close chart"
            className="w-9 h-9 -ml-0.5 flex items-center justify-center rounded-xl text-[var(--text-primary)] hover:bg-[var(--bg-elevated)] active:scale-95 transition shrink-0"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <h2 className="text-sm font-bold text-[var(--text-primary)] truncate">{stock.symbol}</h2>
              {/* Compact One-Handed ▲ / ▼ Stock Stepper */}
              <div className="inline-flex items-center rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] shrink-0 overflow-hidden">
                <button
                  type="button"
                  onClick={onPrev}
                  disabled={!prevSymbol}
                  aria-label="Previous stock"
                  title={prevSymbol ? `Previous: ${prevSymbol} (▲)` : 'Start of list'}
                  className="w-7 h-7 flex items-center justify-center text-[var(--text-primary)] hover:bg-[var(--bg-elevated)] active:scale-90 transition disabled:opacity-30 disabled:pointer-events-none border-r border-[var(--border-subtle)]"
                >
                  <ChevronUp className="w-3.5 h-3.5" />
                </button>
                <button
                  type="button"
                  onClick={onNext}
                  disabled={!nextSymbol}
                  aria-label="Next stock"
                  title={nextSymbol ? `Next: ${nextSymbol} (▼)` : 'End of list'}
                  className="w-7 h-7 flex items-center justify-center text-[var(--text-primary)] hover:bg-[var(--bg-elevated)] active:scale-90 transition disabled:opacity-30 disabled:pointer-events-none"
                >
                  <ChevronDown className="w-3.5 h-3.5" />
                </button>
              </div>
              <span className="hidden sm:inline-block px-1.5 py-0.5 rounded bg-[var(--bg-input)] border border-[var(--border-subtle)] text-[9px] font-medium text-[var(--text-secondary)] truncate max-w-[84px]">
                {stock.sector}
              </span>
            </div>
            <div className="flex items-center gap-1.5 text-xs tabular-nums">
              <span className="font-semibold text-[var(--text-primary)]">{formatINR(stock.metrics.close)}</span>
              <span
                className={`inline-flex items-center gap-0.5 font-semibold ${
                  isUp ? 'text-emerald-500' : 'text-rose-500'
                }`}
              >
                {isUp ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                {formatPct(stock.metrics.changePct)}
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          <button
            type="button"
            onClick={() => onLogInJournal(stock)}
            className="h-9 px-2 rounded-xl bg-cyan-500/15 border border-cyan-500/40 text-cyan-600 dark:text-cyan-300 text-[11px] font-semibold flex items-center gap-1 active:scale-95 transition"
          >
            <BookOpen className="w-3.5 h-3.5" />
            <span>Journal</span>
          </button>

          <button
            type="button"
            onClick={() => onSendToRiskCalc(stock)}
            className="h-9 px-2 rounded-xl bg-emerald-500/15 border border-emerald-500/40 text-emerald-600 dark:text-emerald-300 text-[11px] font-semibold flex items-center gap-1 active:scale-95 transition"
          >
            <Calculator className="w-3.5 h-3.5" />
            <span>Size</span>
          </button>

          <button
            type="button"
            onClick={() => onToggleWatchlist(stock.symbol)}
            aria-label={isStarred ? 'Remove from watchlist' : 'Add to watchlist'}
            className="w-9 h-9 flex items-center justify-center rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] active:scale-95 transition"
          >
            <Star
              className={`w-4 h-4 ${
                isStarred ? 'fill-amber-400 text-amber-400' : 'text-[var(--text-muted)]'
              }`}
            />
          </button>
        </div>
      </header>

      {/* Scrollable Interactive Indicators Toolbar + Timeframe Controls */}
      <div className="shrink-0 bg-[var(--bg-app)] border-b border-[var(--border-subtle)] px-3 py-2 flex items-center gap-2 overflow-x-auto no-scrollbar">
        <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)] shrink-0">
          Indicators:
        </span>

        {/* EMA Toggles */}
        {[
          { key: 'ema9', label: '9 EMA', dot: '#EC4899' },
          { key: 'ema20', label: '20 EMA', dot: '#22D3EE' },
          { key: 'ema45', label: '45 EMA', dot: '#F59E0B' },
        ].map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => toggleInd(item.key)}
            className={`shrink-0 px-2 py-1 rounded-lg text-[11px] font-semibold border transition flex items-center gap-1.5 ${
              ind[item.key]
                ? 'bg-emerald-500/15 border-emerald-500/50 text-[var(--text-primary)]'
                : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-muted)]'
            }`}
          >
            <span className="w-2 h-2 rounded-full" style={{ backgroundColor: item.dot }} />
            {item.label}
          </button>
        ))}

        {/* SMA Toggles */}
        {[
          { key: 'sma20', label: '20 SMA', dot: '#2DD4BF' },
          { key: 'sma50', label: '50 SMA', dot: '#60A5FA' },
          { key: 'sma200', label: '200 SMA', dot: '#F87171' },
        ].map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => toggleInd(item.key)}
            className={`shrink-0 px-2 py-1 rounded-lg text-[11px] font-semibold border transition flex items-center gap-1.5 ${
              ind[item.key]
                ? 'bg-cyan-500/15 border-cyan-500/50 text-[var(--text-primary)]'
                : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-muted)]'
            }`}
          >
            <span className="w-2 h-2 rounded-full" style={{ backgroundColor: item.dot }} />
            {item.label}
          </button>
        ))}

        {/* Bollinger Bands (20, 2) */}
        <button
          type="button"
          onClick={() => toggleInd('bb20')}
          className={`shrink-0 px-2 py-1 rounded-lg text-[11px] font-semibold border transition flex items-center gap-1.5 ${
            ind.bb20
              ? 'bg-sky-500/15 border-sky-500/50 text-[var(--text-primary)]'
              : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-muted)]'
          }`}
        >
          <span className="w-2 h-2 rounded-full bg-sky-400" />
          BB (20,2)
        </button>

        {/* Volume + 20D Vol MA */}
        <button
          type="button"
          onClick={() => toggleInd('volume')}
          className={`shrink-0 px-2 py-1 rounded-lg text-[11px] font-semibold border transition ${
            ind.volume
              ? 'bg-emerald-500/15 border-emerald-500/50 text-[var(--text-primary)]'
              : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-muted)]'
          }`}
        >
          Vol + MA20
        </button>

        {/* RSI / Hilega Sub-Pane Toggle */}
        <button
          type="button"
          onClick={() => toggleInd('rsiPane')}
          className={`shrink-0 px-2 py-1 rounded-lg text-[11px] font-semibold border transition ${
            ind.rsiPane
              ? 'bg-purple-500/15 border-purple-500/50 text-[var(--text-primary)]'
              : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-muted)]'
          }`}
        >
          RSI Pane
        </button>

        {/* Timeframe Range */}
        <div className="flex items-center gap-0.5 bg-[var(--bg-card)] p-0.5 rounded-lg border border-[var(--border-subtle)] shrink-0 ml-auto">
          {Object.keys(RANGE_BARS).map((key) => (
            <button
              key={key}
              type="button"
              onClick={() => handleSelectRange(key)}
              className={`px-2 py-0.5 rounded-md text-[10px] font-semibold transition ${
                rangeKey === key
                  ? 'bg-[var(--bg-elevated)] text-[var(--text-primary)]'
                  : 'text-[var(--text-muted)]'
              }`}
            >
              {key}
            </button>
          ))}
        </div>
      </div>

      {/* Live OHLCV + Active Indicator Legend Strip */}
      <div className="shrink-0 px-3 py-1.5 bg-[var(--bg-app)] border-b border-[var(--border-subtle)] flex flex-wrap items-center gap-x-2.5 gap-y-0.5 text-[10px] tabular-nums text-[var(--text-muted)]">
        <span className="text-[var(--text-primary)] font-semibold">{activeBar.time}</span>
        <span>O:{activeBar.open.toFixed(1)}</span>
        <span>H:{activeBar.high.toFixed(1)}</span>
        <span>L:{activeBar.low.toFixed(1)}</span>
        <span>C:<strong className="text-[var(--text-primary)]">{activeBar.close.toFixed(1)}</strong></span>
        {ind.ema9 && val.ema9 != null && <span className="text-pink-500">E9:{val.ema9.toFixed(1)}</span>}
        {ind.ema20 && val.ema20 != null && <span className="text-cyan-600 dark:text-[#22D3EE]">E20:{val.ema20.toFixed(1)}</span>}
        {ind.ema45 && val.ema45 != null && <span className="text-amber-600 dark:text-[#F59E0B]">E45:{val.ema45.toFixed(1)}</span>}
        {ind.sma20 && val.sma20 != null && <span className="text-teal-600 dark:text-teal-400">S20:{val.sma20.toFixed(1)}</span>}
        {ind.sma50 && val.sma50 != null && <span className="text-blue-600 dark:text-blue-400">S50:{val.sma50.toFixed(1)}</span>}
        {ind.sma200 && val.sma200 != null && <span className="text-rose-600 dark:text-rose-400">S200:{val.sma200.toFixed(1)}</span>}
        {ind.bb20 && val.bbUpper != null && (
          <span className="text-sky-600 dark:text-sky-400">
            BB:{val.bbUpper.toFixed(0)}/{val.bbMiddle?.toFixed(0)}/{val.bbLower?.toFixed(0)}
          </span>
        )}
        {ind.volume && (
          <span>
            Vol:{(activeBar.volume / 1e6).toFixed(2)}M
            {val.volMa20 ? ` (MA:${(val.volMa20 / 1e6).toFixed(2)}M)` : ''}
          </span>
        )}
      </div>

      {/* Charts Area: Price Pane + Toggleable Oscillator Sub-Pane */}
      <div className="flex-1 min-h-0 flex flex-col">
        <div
          ref={priceContainerRef}
          className={`${ind.rsiPane ? 'flex-[64]' : 'flex-1'} min-h-0 w-full relative`}
        />

        {ind.rsiPane && (
          <div className="shrink-0 px-3 py-1 bg-[var(--bg-card)] border-y border-[var(--border-subtle)] flex items-center justify-between gap-2 text-[10px] tabular-nums">
            <div className="flex items-center gap-2.5 min-w-0 truncate">
              {ind.oscMode === 'rsi14' ? (
                <>
                  <span className="font-semibold text-[var(--text-secondary)]">
                    RSI(14):{' '}
                    <strong className="text-sky-600 dark:text-sky-400">
                      {val.rsi14 != null ? val.rsi14.toFixed(2) : '—'}
                    </strong>
                  </span>
                  <span className="font-semibold text-[var(--text-secondary)]">
                    EMA(45):{' '}
                    <strong className="text-amber-600 dark:text-amber-400">
                      {val.rsiEma45 != null ? val.rsiEma45.toFixed(2) : '—'}
                    </strong>
                  </span>
                </>
              ) : (
                <>
                  <span className="font-semibold text-blue-500">
                    RSI9: <strong>{val.rsi9 != null ? val.rsi9.toFixed(1) : '—'}</strong>
                  </span>
                  <span className="font-semibold text-emerald-500">
                    3EMA: <strong>{val.hmEma3 != null ? val.hmEma3.toFixed(1) : '—'}</strong>
                  </span>
                  <span className="font-semibold text-rose-500">
                    21WMA: <strong>{val.hmWma21 != null ? val.hmWma21.toFixed(1) : '—'}</strong>
                  </span>
                </>
              )}
            </div>

            <div className="flex items-center gap-1 shrink-0 bg-[var(--bg-input)] p-0.5 rounded-md border border-[var(--border-subtle)]">
              <button
                type="button"
                onClick={() => setInd((p) => ({ ...p, oscMode: 'rsi14' }))}
                className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${
                  ind.oscMode === 'rsi14'
                    ? 'bg-emerald-500/20 text-emerald-600 dark:text-emerald-300'
                    : 'text-[var(--text-muted)]'
                }`}
              >
                RSI(14)
              </button>
              <button
                type="button"
                onClick={() => setInd((p) => ({ ...p, oscMode: 'hilega' }))}
                className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${
                  ind.oscMode === 'hilega'
                    ? 'bg-purple-500/20 text-purple-600 dark:text-purple-300'
                    : 'text-[var(--text-muted)]'
                }`}
              >
                Hilega Milega
              </button>
            </div>
          </div>
        )}

        <div
          ref={rsiContainerRef}
          className={ind.rsiPane ? 'flex-[36] min-h-0 w-full relative' : 'hidden'}
        />
      </div>

      {/* Key Metrics Strip */}
      <footer className="shrink-0 bg-[var(--bg-card)] border-t border-[var(--border-subtle)] px-3 py-2.5">
        <div className="grid grid-cols-5 gap-1.5 text-center">
          <div className="rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] p-1.5">
            <span className="block text-[9px] uppercase tracking-wide text-[var(--text-muted)]">Wkly RSI</span>
            <span className="mt-0.5 block text-xs font-bold tabular-nums text-emerald-500">
              {stock.metrics.weeklyRsi.toFixed(1)}
            </span>
          </div>

          <div className="rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] p-1.5">
            <span className="block text-[9px] uppercase tracking-wide text-[var(--text-muted)]">RS Rating</span>
            <span
              className={`mt-0.5 block text-xs font-bold tabular-nums ${
                stock.metrics.rsRating >= 80 ? 'text-emerald-500' : 'text-amber-500'
              }`}
            >
              {stock.metrics.rsRating}/99
            </span>
          </div>

          <div className="rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] p-1.5">
            <span className="block text-[9px] uppercase tracking-wide text-[var(--text-muted)]">52W Dist</span>
            <span className="mt-0.5 block text-xs font-bold tabular-nums text-[var(--text-primary)]">
              {stock.metrics.distFrom52WHighPct.toFixed(1)}%
            </span>
          </div>

          <div className="rounded-lg bg-[var(--bg-input)] border border-[var(--border-subtle)] p-1.5">
            <span className="block text-[9px] uppercase tracking-wide text-[var(--text-muted)]">20D Val</span>
            <span className="mt-0.5 block text-xs font-bold tabular-nums text-[var(--text-primary)]">
              ₹{stock.metrics.avgTurnoverCr.toFixed(0)}Cr
            </span>
          </div>

          <div className="rounded-lg bg-[var(--bg-input)] border border-rose-500/35 p-1.5">
            <span className="flex items-center justify-center gap-0.5 text-[9px] uppercase tracking-wide text-rose-500">
              <ShieldAlert className="w-2.5 h-2.5" />
              Swing SL
            </span>
            <span className="mt-0.5 block text-xs font-bold tabular-nums text-rose-500">
              ₹{stock.metrics.swingLowStop.toFixed(0)}
            </span>
          </div>
        </div>
      </footer>

      {/* Sticky Bottom Stock Switcher Bar */}
      <nav
        aria-label="Stock List Navigation"
        className="shrink-0 bg-[var(--bg-card)] border-t border-[var(--border-subtle)] px-2.5 py-2 flex items-center justify-between gap-2"
      >
        <button
          type="button"
          onClick={onPrev}
          disabled={!prevSymbol}
          aria-label={prevSymbol ? `Previous stock ${prevSymbol}` : 'No previous stock'}
          className="min-h-[44px] min-w-[84px] max-w-[128px] px-2.5 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] hover:border-emerald-500/40 text-xs font-bold text-[var(--text-primary)] flex items-center justify-start gap-1 active:scale-95 transition disabled:opacity-40 disabled:pointer-events-none"
        >
          <ChevronLeft className="w-4 h-4 text-emerald-500 shrink-0" />
          <span className="truncate">{prevSymbol || 'Prev'}</span>
        </button>

        <div className="min-w-0 px-2.5 py-1.5 rounded-full bg-[var(--bg-input)] border border-[var(--border-subtle)] text-[11px] font-bold tabular-nums text-[var(--text-secondary)] truncate text-center">
          {listLabel === 'Watchlist'
            ? `Watchlist ${listPosition} / ${listTotal}`
            : `${listPosition} / ${listTotal} • ${listLabel}`}
        </div>

        <button
          type="button"
          onClick={onNext}
          disabled={!nextSymbol}
          aria-label={nextSymbol ? `Next stock ${nextSymbol}` : 'No next stock'}
          className="min-h-[44px] min-w-[84px] max-w-[128px] px-2.5 rounded-xl bg-[var(--bg-input)] border border-[var(--border-subtle)] hover:border-emerald-500/40 text-xs font-bold text-[var(--text-primary)] flex items-center justify-end gap-1 active:scale-95 transition disabled:opacity-40 disabled:pointer-events-none"
        >
          <span className="truncate">{nextSymbol || 'Next'}</span>
          <ChevronRight className="w-4 h-4 text-emerald-500 shrink-0" />
        </button>
      </nav>
    </div>
  );
}
