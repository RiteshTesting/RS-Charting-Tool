import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
  MousePointer2,
  Minus,
  Trash2,
  Eye,
  EyeOff,
  X,
  Maximize2,
  MoveVertical,
} from 'lucide-react';
import { formatINR, formatPct } from '../utils/formatters';
import { computeChartSeriesForTimeframe } from '../utils/chartResampler';
import {
  DEFAULT_INDICATOR_STATE,
  INDICATOR_BY_ID,
  isIndicatorVisible,
  loadIndicatorState,
  saveIndicatorState,
} from '../utils/chartIndicatorsConfig';
import {
  IndicatorDropdownButton,
  FloatingIndicatorPills,
} from './ChartIndicatorMenu';
import ChartDrawingOverlay, {
  loadDrawingsForSymbol,
  saveDrawingsForSymbol,
} from './ChartDrawingOverlay';

const TIMEFRAMES = [
  { key: 'D', label: 'D', full: 'Daily' },
  { key: 'W', label: 'W', full: 'Weekly' },
  { key: 'M', label: 'M', full: 'Monthly' },
];

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

function TrendlineIcon({ className = 'w-3.5 h-3.5' }) {
  return (
    <svg viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <line x1="3" y1="13" x2="13" y2="3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="3" cy="13" r="1.8" fill="currentColor" />
      <circle cx="13" cy="3" r="1.8" fill="currentColor" />
    </svg>
  );
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
  const candleSeriesRef = useRef(null);
  const seriesRefs = useRef({});
  const bbPrimitiveRef = useRef(null);
  const swingSlLineRef = useRef(null);

  const isFirstDataRef = useRef(true);
  const prevBarCountRef = useRef(stock.candles.length);
  const prevTimeframeRef = useRef('D');

  // Timeframe: 'D' | 'W' | 'M'
  const [timeframe, setTimeframe] = useState('D');

  // Indicator state persisted in localStorage
  const [indicatorState, setIndicatorState] = useState(loadIndicatorState);
  const indicatorStateRef = useRef(indicatorState);

  useEffect(() => {
    indicatorStateRef.current = indicatorState;
    saveIndicatorState(indicatorState);
  }, [indicatorState]);

  // Price scale vertical margin state for smooth touch/mouse Y-axis compress/decompress
  const showVolume = isIndicatorVisible(indicatorState, 'volume');
  const defaultBottomMargin = showVolume ? 0.22 : 0.1;
  const priceMarginsRef = useRef({ top: 0.1, bottom: defaultBottomMargin });
  const [isCustomPriceScale, setIsCustomPriceScale] = useState(false);
  const [isDraggingPriceAxis, setIsDraggingPriceAxis] = useState(false);
  const [priceScaleVersion, setPriceScaleVersion] = useState(0);
  const priceAxisDragRef = useRef(null);

  // Interactive drawing tools state (persisted per stock symbol)
  const [activeTool, setActiveTool] = useState('cursor'); // 'cursor' | 'hline' | 'trendline'
  const [drawings, setDrawings] = useState(() => loadDrawingsForSymbol(stock.symbol));
  const [selectedDrawingId, setSelectedDrawingId] = useState(null);

  // Reload drawings whenever stock symbol changes
  useEffect(() => {
    setDrawings(loadDrawingsForSymbol(stock.symbol));
    setSelectedDrawingId(null);
    setActiveTool('cursor');
  }, [stock.symbol]);

  const handleUpdateDrawings = useCallback(
    (nextDrawings) => {
      setDrawings(nextDrawings);
      saveDrawingsForSymbol(stock.symbol, nextDrawings);
    },
    [stock.symbol]
  );

  // Dynamically resample candles & recalculate all indicators for active timeframe (D / W / M)
  const { candles: activeCandles, indicators: activeIndicators } = useMemo(
    () => computeChartSeriesForTimeframe(stock, timeframe),
    [stock, timeframe]
  );

  const latestCandle = activeCandles[activeCandles.length - 1] || stock.candles[stock.candles.length - 1];
  const [hoverInfo, setHoverInfo] = useState(null);

  const indicatorLookup = useMemo(() => {
    const map = new Map();
    activeCandles.forEach((c, idx) => {
      map.set(c.time, {
        candle: c,
        sma20: activeIndicators.sma20[idx],
        sma50: activeIndicators.sma50[idx],
        sma200: activeIndicators.sma200[idx],
        ema9: activeIndicators.ema9[idx],
        ema20: activeIndicators.ema20[idx],
        ema45: activeIndicators.ema45[idx],
        bbUpper: activeIndicators.bbUpper[idx],
        bbMiddle: activeIndicators.bbMiddle[idx],
        bbLower: activeIndicators.bbLower[idx],
        volMa20: activeIndicators.volMa20[idx],
        rsi14: activeIndicators.rsi14[idx],
        rsiEma45: activeIndicators.rsiEma45[idx],
        rsi9: activeIndicators.rsi9[idx],
        hmEma3: activeIndicators.hmEma3[idx],
        hmWma21: activeIndicators.hmWma21[idx],
      });
    });
    return map;
  }, [activeCandles, activeIndicators]);

  const indicatorLookupRef = useRef(indicatorLookup);
  useEffect(() => {
    indicatorLookupRef.current = indicatorLookup;
  }, [indicatorLookup]);

  const hasRsiAdded = indicatorState.added.includes('rsi14');
  const hasHilegaAdded = indicatorState.added.includes('hilega');
  const showRsi = isIndicatorVisible(indicatorState, 'rsi14');
  const showHilega = isIndicatorVisible(indicatorState, 'hilega');
  const hasOscHeader = hasRsiAdded || hasHilegaAdded;
  const showOscCanvas = showRsi || showHilega;

  // Auto Fit (A): reset price scale autoScale + default margins + sync overlay
  const handleAutoFit = useCallback(() => {
    const chart = priceChartRef.current;
    if (!chart) return;
    const bottom = isIndicatorVisible(indicatorStateRef.current, 'volume') ? 0.22 : 0.1;
    priceMarginsRef.current = { top: 0.1, bottom };
    chart.priceScale('right').applyOptions({
      autoScale: true,
      scaleMargins: { top: 0.1, bottom },
    });
    chart.timeScale().applyOptions({ rightOffset: 4 });
    setIsCustomPriceScale(false);
    setPriceScaleVersion((v) => v + 1);
  }, []);

  // Delete selected drawing or clear all drawings for current symbol
  const handleDeleteOrClearDrawings = useCallback(() => {
    if (drawings.length === 0) return;
    if (selectedDrawingId) {
      const next = drawings.filter((d) => d.id !== selectedDrawingId);
      handleUpdateDrawings(next);
      setSelectedDrawingId(null);
    } else {
      handleUpdateDrawings([]);
      setSelectedDrawingId(null);
    }
  }, [drawings, selectedDrawingId, handleUpdateDrawings]);

  // Keyboard shortcuts: Prev/Next stock, Escape, A (Auto Fit), Delete/Backspace (selected drawing)
  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const tag = e.target?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

      if (e.key === 'Escape') {
        e.preventDefault();
        if (activeTool !== 'cursor') {
          setActiveTool('cursor');
        } else {
          onClose();
        }
      } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown' || e.key === ' ' || e.code === 'Space') {
        e.preventDefault();
        onNext?.();
      } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
        e.preventDefault();
        onPrev?.();
      } else if (e.key === 'a' || e.key === 'A') {
        e.preventDefault();
        handleAutoFit();
      } else if ((e.key === 'Delete' || e.key === 'Backspace') && selectedDrawingId) {
        e.preventDefault();
        handleDeleteOrClearDrawings();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose, onPrev, onNext, activeTool, handleAutoFit, selectedDrawingId, handleDeleteOrClearDrawings]);

  // Right-hand Price Axis vertical touch + mouse drag for smooth candle compress/decompress
  const handlePriceAxisPointerDown = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.currentTarget.setPointerCapture) {
      try {
        e.currentTarget.setPointerCapture(e.pointerId);
      } catch {
        // ignore
      }
    }
    priceAxisDragRef.current = {
      startY: e.clientY,
      startTop: priceMarginsRef.current.top,
      startBottom: priceMarginsRef.current.bottom,
      pointerId: e.pointerId,
    };
    setIsDraggingPriceAxis(true);
  };

  const handlePriceAxisPointerMove = (e) => {
    const drag = priceAxisDragRef.current;
    const chart = priceChartRef.current;
    if (!drag || !chart) return;

    e.preventDefault();
    e.stopPropagation();

    const height = Math.max(180, priceContainerRef.current?.clientHeight || 300);
    // Dragging down (dy > 0) increases margins -> compresses candles vertically
    // Dragging up (dy < 0) decreases margins -> decompresses / stretches candles vertically
    const deltaRatio = ((e.clientY - drag.startY) / height) * 0.85;
    const nextTop = Math.max(0.02, Math.min(0.42, +(drag.startTop + deltaRatio).toFixed(3)));
    const nextBottom = Math.max(0.02, Math.min(0.42, +(drag.startBottom + deltaRatio).toFixed(3)));

    priceMarginsRef.current = { top: nextTop, bottom: nextBottom };
    chart.priceScale('right').applyOptions({
      autoScale: true,
      scaleMargins: { top: nextTop, bottom: nextBottom },
    });
    setIsCustomPriceScale(true);
    setPriceScaleVersion((v) => v + 1);
  };

  const handlePriceAxisPointerUp = (e) => {
    const drag = priceAxisDragRef.current;
    if (!drag) return;
    if (e?.currentTarget?.releasePointerCapture && drag.pointerId != null) {
      try {
        e.currentTarget.releasePointerCapture(drag.pointerId);
      } catch {
        // ignore
      }
    }
    priceAxisDragRef.current = null;
    setIsDraggingPriceAxis(false);
  };

  // Initialize charts ONCE on mount so stepping between stocks & timeframes is flicker-free
  useEffect(() => {
    const priceEl = priceContainerRef.current;
    const rsiEl = rsiContainerRef.current;
    if (!priceEl || !rsiEl) return;

    const pal = getChartThemePalette(theme);
    const initInd = indicatorStateRef.current;
    const initOscVisible =
      isIndicatorVisible(initInd, 'rsi14') || isIndicatorVisible(initInd, 'hilega');

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
        autoScale: true,
        scaleMargins: {
          top: 0.1,
          bottom: isIndicatorVisible(initInd, 'volume') ? 0.22 : 0.1,
        },
      },
      handleScroll: true,
      handleScale: {
        axisPressedMouseMove: { time: true, price: true },
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
        visible: !initOscVisible,
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
    candleSeriesRef.current = candleSeries;

    const volumeVisible = isIndicatorVisible(initInd, 'volume');
    const volumeSeries = priceChart.addHistogramSeries({
      priceFormat: { type: 'volume' },
      priceScaleId: 'vol',
      visible: volumeVisible,
    });
    priceChart.priceScale('vol').applyOptions({
      scaleMargins: { top: 0.8, bottom: 0 },
    });

    const volMaSeries = priceChart.addLineSeries({
      priceScaleId: 'vol',
      color: pal.volMa,
      lineWidth: 1,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: volumeVisible,
    });

    const sma20Series = priceChart.addLineSeries({
      color: pal.sma20,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: isIndicatorVisible(initInd, 'sma20'),
    });
    const sma50Series = priceChart.addLineSeries({
      color: pal.sma50,
      lineWidth: 1.75,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: isIndicatorVisible(initInd, 'sma50'),
    });
    const sma200Series = priceChart.addLineSeries({
      color: pal.sma200,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: isIndicatorVisible(initInd, 'sma200'),
    });

    const ema9Series = priceChart.addLineSeries({
      color: pal.ema9,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: isIndicatorVisible(initInd, 'ema9'),
    });
    const ema20Series = priceChart.addLineSeries({
      color: pal.ema20,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: isIndicatorVisible(initInd, 'ema20'),
    });
    const ema45Series = priceChart.addLineSeries({
      color: pal.ema45,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: isIndicatorVisible(initInd, 'ema45'),
    });

    const bbVisible = isIndicatorVisible(initInd, 'bb20');
    const bbUpperSeries = priceChart.addLineSeries({
      color: pal.bbEdge,
      lineWidth: 1.25,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: bbVisible,
    });
    const bbMiddleSeries = priceChart.addLineSeries({
      color: pal.bbMid,
      lineWidth: 1,
      lineStyle: LineStyle.Dotted,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: bbVisible,
    });
    const bbLowerSeries = priceChart.addLineSeries({
      color: pal.bbEdge,
      lineWidth: 1.25,
      priceLineVisible: false,
      lastValueVisible: false,
      visible: bbVisible,
    });

    const bbPrimitive = new BollingerBandFillPrimitive([], [], [], pal.bbFill);
    bbPrimitive.setVisible(bbVisible);
    try {
      bbUpperSeries.attachPrimitive(bbPrimitive);
    } catch {
      // fallback if primitive unsupported
    }
    bbPrimitiveRef.current = bbPrimitive;

    const rsiVis = isIndicatorVisible(initInd, 'rsi14');
    const hmVis = isIndicatorVisible(initInd, 'hilega');

    const rsi14Series = rsiChart.addLineSeries({
      color: pal.rsiLine,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: rsiVis,
      visible: rsiVis,
    });
    const rsiEma45Series = rsiChart.addLineSeries({
      color: pal.rsiEma,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: rsiVis,
      visible: rsiVis,
    });

    const rsi9Series = rsiChart.addLineSeries({
      color: pal.hmRsi9,
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: hmVis,
      visible: hmVis,
    });
    const hmEma3Series = rsiChart.addLineSeries({
      color: pal.hmEma3,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: hmVis,
      visible: hmVis,
    });
    const hmWma21Series = rsiChart.addLineSeries({
      color: pal.hmWma21,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: hmVis,
      visible: hmVis,
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
        const st = indicatorStateRef.current;
        if (isIndicatorVisible(st, 'rsi14') && entry.rsi14 != null) {
          rsiChart.setCrosshairPosition(entry.rsi14, param.time, rsi14Series);
        } else if (isIndicatorVisible(st, 'hilega') && entry.hmEma3 != null) {
          rsiChart.setCrosshairPosition(entry.hmEma3, param.time, hmEma3Series);
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
      candleSeriesRef.current = null;
      seriesRefs.current = {};
      bbPrimitiveRef.current = null;
      swingSlLineRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Update series data in-place on stock switch OR timeframe switch (D / W / M)
  useEffect(() => {
    const s = seriesRefs.current;
    const priceChart = priceChartRef.current;
    const rsiChart = rsiChartRef.current;
    if (!s.candleSeries || !priceChart || !rsiChart || activeCandles.length === 0) return;

    const timeframeChanged = prevTimeframeRef.current !== timeframe;
    const prevRange =
      !isFirstDataRef.current && !timeframeChanged
        ? priceChart.timeScale().getVisibleLogicalRange()
        : null;
    const prevBars = prevBarCountRef.current || activeCandles.length;

    const toLineData = (arr) =>
      activeCandles
        .map((c, idx) => (arr[idx] != null ? { time: c.time, value: arr[idx] } : null))
        .filter(Boolean);

    s.candleSeries.setData(
      activeCandles.map((c) => ({
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
      activeCandles.map((c) => ({
        time: c.time,
        value: c.volume,
        color: c.close >= c.open ? 'rgba(16, 185, 129, 0.35)' : 'rgba(239, 68, 68, 0.35)',
      }))
    );
    s.volMaSeries.setData(toLineData(activeIndicators.volMa20));

    s.sma20Series.setData(toLineData(activeIndicators.sma20));
    s.sma50Series.setData(toLineData(activeIndicators.sma50));
    s.sma200Series.setData(toLineData(activeIndicators.sma200));

    s.ema9Series.setData(toLineData(activeIndicators.ema9));
    s.ema20Series.setData(toLineData(activeIndicators.ema20));
    s.ema45Series.setData(toLineData(activeIndicators.ema45));

    s.bbUpperSeries.setData(toLineData(activeIndicators.bbUpper));
    s.bbMiddleSeries.setData(toLineData(activeIndicators.bbMiddle));
    s.bbLowerSeries.setData(toLineData(activeIndicators.bbLower));
    bbPrimitiveRef.current?.setData(
      activeCandles,
      activeIndicators.bbUpper,
      activeIndicators.bbLower
    );

    s.rsi14Series.setData(toLineData(activeIndicators.rsi14));
    s.rsiEma45Series.setData(toLineData(activeIndicators.rsiEma45));
    s.rsi9Series.setData(toLineData(activeIndicators.rsi9));
    s.hmEma3Series.setData(toLineData(activeIndicators.hmEma3));
    s.hmWma21Series.setData(toLineData(activeIndicators.hmWma21));

    const totalBars = activeCandles.length;
    let targetRange;
    if (isFirstDataRef.current || timeframeChanged || !prevRange) {
      const defaultBars = timeframe === 'D' ? 120 : timeframe === 'W' ? 60 : 36;
      targetRange = {
        from: Math.max(0, totalBars - defaultBars),
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
    setPriceScaleVersion((v) => v + 1);

    isFirstDataRef.current = false;
    prevBarCountRef.current = totalBars;
    prevTimeframeRef.current = timeframe;
  }, [stock, timeframe, activeCandles, activeIndicators]);

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

  // Apply indicator add/remove & Eye show/hide toggles smoothly
  useEffect(() => {
    const s = seriesRefs.current;
    const vis = (id) => isIndicatorVisible(indicatorState, id);

    s.sma20Series?.applyOptions({ visible: vis('sma20') });
    s.sma50Series?.applyOptions({ visible: vis('sma50') });
    s.sma200Series?.applyOptions({ visible: vis('sma200') });
    s.ema9Series?.applyOptions({ visible: vis('ema9') });
    s.ema20Series?.applyOptions({ visible: vis('ema20') });
    s.ema45Series?.applyOptions({ visible: vis('ema45') });

    const bbVis = vis('bb20');
    s.bbUpperSeries?.applyOptions({ visible: bbVis });
    s.bbMiddleSeries?.applyOptions({ visible: bbVis });
    s.bbLowerSeries?.applyOptions({ visible: bbVis });
    bbPrimitiveRef.current?.setVisible(bbVis);

    const volVis = vis('volume');
    s.volumeSeries?.applyOptions({ visible: volVis });
    s.volMaSeries?.applyOptions({ visible: volVis });

    if (!isCustomPriceScale && priceChartRef.current) {
      const bottom = volVis ? 0.22 : 0.1;
      priceMarginsRef.current = { top: 0.1, bottom };
      priceChartRef.current.priceScale('right').applyOptions({
        autoScale: true,
        scaleMargins: { top: 0.1, bottom },
      });
    }

    const rsiVis = vis('rsi14');
    const hmVis = vis('hilega');
    s.rsi14Series?.applyOptions({ visible: rsiVis, lastValueVisible: rsiVis });
    s.rsiEma45Series?.applyOptions({ visible: rsiVis, lastValueVisible: rsiVis });
    s.rsi9Series?.applyOptions({ visible: hmVis, lastValueVisible: hmVis });
    s.hmEma3Series?.applyOptions({ visible: hmVis, lastValueVisible: hmVis });
    s.hmWma21Series?.applyOptions({ visible: hmVis, lastValueVisible: hmVis });

    const oscVisible = rsiVis || hmVis;
    priceChartRef.current?.applyOptions({
      timeScale: { visible: !oscVisible },
    });
    const currentRange = priceChartRef.current?.timeScale().getVisibleLogicalRange();
    if (oscVisible && currentRange) {
      rsiChartRef.current?.timeScale().setVisibleLogicalRange(currentRange);
    }
    setPriceScaleVersion((v) => v + 1);
  }, [indicatorState, isCustomPriceScale]);

  const handleToggleAddIndicator = (id) => {
    setIndicatorState((prev) => {
      const exists = prev.added.includes(id);
      if (exists) {
        const nextHidden = { ...prev.hidden };
        delete nextHidden[id];
        return {
          added: prev.added.filter((item) => item !== id),
          hidden: nextHidden,
        };
      }
      const nextHidden = { ...prev.hidden };
      delete nextHidden[id];
      return {
        added: [...prev.added, id],
        hidden: nextHidden,
      };
    });
  };

  const handleToggleHideIndicator = (id) => {
    setIndicatorState((prev) => ({
      ...prev,
      hidden: {
        ...prev.hidden,
        [id]: !prev.hidden[id],
      },
    }));
  };

  const handleRemoveIndicator = (id) => {
    setIndicatorState((prev) => {
      const nextHidden = { ...prev.hidden };
      delete nextHidden[id];
      return {
        added: prev.added.filter((item) => item !== id),
        hidden: nextHidden,
      };
    });
  };

  const handleResetDefaultIndicators = () => {
    setIndicatorState(DEFAULT_INDICATOR_STATE);
  };

  const activeBar = hoverInfo?.candle ?? latestCandle;
  const lastIdx = Math.max(0, activeCandles.length - 1);
  const val = hoverInfo || {
    sma20: activeIndicators.sma20[lastIdx],
    sma50: activeIndicators.sma50[lastIdx],
    sma200: activeIndicators.sma200[lastIdx],
    ema9: activeIndicators.ema9[lastIdx],
    ema20: activeIndicators.ema20[lastIdx],
    ema45: activeIndicators.ema45[lastIdx],
    bbUpper: activeIndicators.bbUpper[lastIdx],
    bbMiddle: activeIndicators.bbMiddle[lastIdx],
    bbLower: activeIndicators.bbLower[lastIdx],
    volMa20: activeIndicators.volMa20[lastIdx],
    rsi14: activeIndicators.rsi14[lastIdx],
    rsiEma45: activeIndicators.rsiEma45[lastIdx],
    rsi9: activeIndicators.rsi9[lastIdx],
    hmEma3: activeIndicators.hmEma3[lastIdx],
    hmWma21: activeIndicators.hmWma21[lastIdx],
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

      {/* Compact TradingView-Style Chart Toolbar: [ D | W | M ] • Indicators ▾ • Drawing Tools */}
      <div className="shrink-0 bg-[var(--bg-app)] border-b border-[var(--border-subtle)] px-2.5 py-1.5 flex items-center justify-between gap-2 overflow-x-auto no-scrollbar">
        <div className="flex items-center gap-1.5 shrink-0">
          {/* Timeframe Pill Switcher [ D ] [ W ] [ M ] */}
          <div
            role="group"
            aria-label="Chart timeframe"
            className="inline-flex items-center bg-[var(--bg-card)] p-0.5 rounded-lg border border-[var(--border-subtle)] shrink-0"
          >
            {TIMEFRAMES.map((tf) => {
              const active = timeframe === tf.key;
              return (
                <button
                  key={tf.key}
                  type="button"
                  onClick={() => setTimeframe(tf.key)}
                  aria-pressed={active}
                  title={`${tf.full} candles`}
                  className={`min-w-[28px] h-7 px-2 rounded-md text-[11px] font-bold transition ${
                    active
                      ? 'bg-emerald-500 text-white shadow-sm'
                      : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
                  }`}
                >
                  {tf.label}
                </button>
              );
            })}
          </div>

          {/* Single Compact Indicators Dropdown */}
          <IndicatorDropdownButton
            indicatorState={indicatorState}
            theme={theme}
            onToggleAdd={handleToggleAddIndicator}
            onToggleHide={handleToggleHideIndicator}
            onResetDefaults={handleResetDefaultIndicators}
          />
        </div>

        {/* Interactive Drawing Toolbar: Cursor, Horizontal Line (—), Trendline (╱), Delete/Clear (🗑) */}
        <div
          role="group"
          aria-label="Drawing tools"
          className="inline-flex items-center gap-1 bg-[var(--bg-card)] p-0.5 rounded-lg border border-[var(--border-subtle)] shrink-0"
        >
          <button
            type="button"
            onClick={() => setActiveTool('cursor')}
            aria-label="Pan and select mode"
            title="Pan / Select"
            className={`w-7 h-7 flex items-center justify-center rounded-md transition ${
              activeTool === 'cursor'
                ? 'bg-[var(--bg-elevated)] text-emerald-500'
                : 'text-[var(--text-muted)] hover:text-[var(--text-primary)]'
            }`}
          >
            <MousePointer2 className="w-3.5 h-3.5" />
          </button>

          <button
            type="button"
            onClick={() => setActiveTool((t) => (t === 'hline' ? 'cursor' : 'hline'))}
            aria-label="Horizontal Line tool"
            aria-pressed={activeTool === 'hline'}
            title="Horizontal Support / Resistance Line (—)"
            className={`h-7 px-2 flex items-center gap-1 rounded-md text-[11px] font-bold transition ${
              activeTool === 'hline'
                ? 'bg-amber-500/20 text-amber-500 ring-1 ring-amber-500/50'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            }`}
          >
            <Minus className="w-3.5 h-3.5" />
            <span className="hidden sm:inline text-[10px]">H-Line</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTool((t) => (t === 'trendline' ? 'cursor' : 'trendline'))}
            aria-label="Trendline Stretch tool"
            aria-pressed={activeTool === 'trendline'}
            title="Trendline / Stretch Line (╱)"
            className={`h-7 px-2 flex items-center gap-1 rounded-md text-[11px] font-bold transition ${
              activeTool === 'trendline'
                ? 'bg-sky-500/20 text-sky-500 ring-1 ring-sky-500/50'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            }`}
          >
            <TrendlineIcon className="w-3.5 h-3.5" />
            <span className="hidden sm:inline text-[10px]">Trend</span>
          </button>

          <button
            type="button"
            onClick={handleDeleteOrClearDrawings}
            disabled={drawings.length === 0}
            aria-label={selectedDrawingId ? 'Delete selected line' : 'Clear all drawings'}
            title={
              selectedDrawingId
                ? 'Delete selected drawing'
                : drawings.length > 0
                  ? `Clear all ${drawings.length} drawing(s)`
                  : 'No drawings to clear'
            }
            className={`relative h-7 px-1.5 flex items-center gap-0.5 rounded-md transition disabled:opacity-30 disabled:pointer-events-none ${
              selectedDrawingId
                ? 'bg-rose-500/20 text-rose-500'
                : 'text-[var(--text-muted)] hover:text-rose-500'
            }`}
          >
            <Trash2 className="w-3.5 h-3.5" />
            {drawings.length > 0 && (
              <span className="text-[9px] font-bold tabular-nums">{drawings.length}</span>
            )}
          </button>
        </div>
      </div>

      {/* Clean Compact OHLCV Bar */}
      <div className="shrink-0 px-3 py-1 bg-[var(--bg-app)] border-b border-[var(--border-subtle)] flex items-center gap-x-2.5 gap-y-0.5 text-[10px] tabular-nums text-[var(--text-muted)] overflow-x-auto no-scrollbar">
        <span className="px-1.5 py-0.2 rounded bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 font-bold shrink-0">
          {timeframe}
        </span>
        <span className="text-[var(--text-primary)] font-semibold shrink-0">{activeBar?.time}</span>
        <span className="shrink-0">O:{activeBar?.open.toFixed(1)}</span>
        <span className="shrink-0">H:{activeBar?.high.toFixed(1)}</span>
        <span className="shrink-0">L:{activeBar?.low.toFixed(1)}</span>
        <span className="shrink-0">
          C:<strong className="text-[var(--text-primary)]">{activeBar?.close.toFixed(1)}</strong>
        </span>
        {activeBar?.volume != null && (
          <span className="shrink-0">
            Vol:{(activeBar.volume / 1e6).toFixed(2)}M
          </span>
        )}
      </div>

      {/* Charts Area: Price Pane + Oscillator Sub-Pane */}
      <div className="flex-1 min-h-0 flex flex-col">
        <div
          className={`${showOscCanvas ? 'flex-[66]' : 'flex-1'} min-h-0 w-full relative overflow-hidden`}
        >
          {/* TradingView Candlestick Canvas */}
          <div ref={priceContainerRef} className="w-full h-full" />

          {/* Floating Top-Left Active Indicator Legend Pills with Eye & Remove Controls */}
          <FloatingIndicatorPills
            indicatorState={indicatorState}
            val={val}
            activeBar={activeBar}
            theme={theme}
            onToggleHide={handleToggleHideIndicator}
            onRemove={handleRemoveIndicator}
          />

          {/* Interactive Drawing Overlay (Horizontal Line & Trendline / Stretch Line) */}
          <ChartDrawingOverlay
            priceChartRef={priceChartRef}
            candleSeriesRef={candleSeriesRef}
            containerRef={priceContainerRef}
            candles={activeCandles}
            timeframe={timeframe}
            symbol={stock.symbol}
            theme={theme}
            activeTool={activeTool}
            drawings={drawings}
            selectedId={selectedDrawingId}
            onSelectDrawing={setSelectedDrawingId}
            onChangeDrawings={handleUpdateDrawings}
            onToolComplete={() => setActiveTool('cursor')}
            priceScaleVersion={priceScaleVersion}
          />

          {/* Right-Hand Price Axis Direct Touch/Mouse Vertical Drag Strip (Compress / Decompress Candles) */}
          <div
            role="slider"
            aria-label="Drag vertically to compress or decompress price scale"
            aria-orientation="vertical"
            title="Drag up/down to stretch or compress price bars • Double-tap to Auto Fit"
            onPointerDown={handlePriceAxisPointerDown}
            onPointerMove={handlePriceAxisPointerMove}
            onPointerUp={handlePriceAxisPointerUp}
            onPointerCancel={handlePriceAxisPointerUp}
            onDoubleClick={handleAutoFit}
            className={`absolute top-0 right-0 bottom-0 w-[56px] z-20 touch-none cursor-ns-resize select-none transition-colors flex items-start justify-center pt-1.5 ${
              isDraggingPriceAxis
                ? 'bg-emerald-500/15 border-l border-emerald-500/60'
                : 'hover:bg-emerald-500/5'
            }`}
          >
            {isDraggingPriceAxis && (
              <span className="px-1 py-0.5 rounded bg-emerald-600 text-white text-[9px] font-bold flex items-center gap-0.5 shadow">
                <MoveVertical className="w-2.5 h-2.5" />
                Scale
              </span>
            )}
          </div>

          {/* Bottom-Right Compact Auto Fit (A) Button */}
          <button
            type="button"
            onClick={handleAutoFit}
            aria-label="Auto Fit Price Scale"
            title="Auto Fit price scale (A)"
            className={`absolute bottom-2 right-[62px] z-20 h-7 px-2 rounded-lg border text-[10px] font-bold flex items-center gap-1 shadow-md active:scale-95 transition ${
              isCustomPriceScale
                ? 'bg-emerald-600 border-emerald-400 text-white'
                : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:border-emerald-500/50'
            }`}
          >
            <Maximize2 className="w-3 h-3" />
            <span>Auto Fit (A)</span>
          </button>
        </div>

        {/* Oscillator Sub-Pane Header Pills (RSI(14)+45 EMA & Hilega Milega with Individual Eye & Remove) */}
        {hasOscHeader && (
          <div className="shrink-0 px-2 py-1 bg-[var(--bg-card)] border-y border-[var(--border-subtle)] flex flex-wrap items-center gap-1.5 text-[10px] tabular-nums">
            {hasRsiAdded && (() => {
              const hidden = Boolean(indicatorState.hidden.rsi14);
              const meta = INDICATOR_BY_ID.rsi14;
              return (
                <div
                  className={`inline-flex items-center gap-1.5 pl-2 pr-1 h-6 rounded-md bg-[var(--bg-input)] border border-[var(--border-subtle)] ${
                    hidden ? 'opacity-55' : ''
                  }`}
                >
                  <span className="w-2 h-2 rounded-full bg-sky-400 shrink-0" />
                  <span className={`font-semibold text-[var(--text-primary)] ${hidden ? 'line-through' : ''}`}>
                    RSI(14):
                  </span>
                  {!hidden && (
                    <>
                      <strong className="text-sky-600 dark:text-sky-400">
                        {val.rsi14 != null ? val.rsi14.toFixed(1) : '—'}
                      </strong>
                      <span className="text-[var(--text-muted)]">E45:</span>
                      <strong className="text-amber-600 dark:text-amber-400">
                        {val.rsiEma45 != null ? val.rsiEma45.toFixed(1) : '—'}
                      </strong>
                    </>
                  )}
                  <button
                    type="button"
                    onClick={() => handleToggleHideIndicator('rsi14')}
                    aria-label={hidden ? `Show ${meta.label}` : `Hide ${meta.label}`}
                    className="w-5 h-5 flex items-center justify-center rounded hover:bg-[var(--bg-elevated)] text-[var(--text-muted)] hover:text-[var(--text-primary)]"
                  >
                    {hidden ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleRemoveIndicator('rsi14')}
                    aria-label={`Remove ${meta.label}`}
                    className="w-5 h-5 flex items-center justify-center rounded hover:bg-rose-500/15 text-[var(--text-muted)] hover:text-rose-500"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </div>
              );
            })()}

            {hasHilegaAdded && (() => {
              const hidden = Boolean(indicatorState.hidden.hilega);
              const meta = INDICATOR_BY_ID.hilega;
              return (
                <div
                  className={`inline-flex items-center gap-1.5 pl-2 pr-1 h-6 rounded-md bg-[var(--bg-input)] border border-[var(--border-subtle)] ${
                    hidden ? 'opacity-55' : ''
                  }`}
                >
                  <span className="w-2 h-2 rounded-full bg-purple-400 shrink-0" />
                  <span className={`font-semibold text-[var(--text-primary)] ${hidden ? 'line-through' : ''}`}>
                    Hilega:
                  </span>
                  {!hidden && (
                    <>
                      <strong className="text-blue-500">
                        {val.rsi9 != null ? val.rsi9.toFixed(1) : '—'}
                      </strong>
                      <strong className="text-emerald-500">
                        {val.hmEma3 != null ? val.hmEma3.toFixed(1) : '—'}
                      </strong>
                      <strong className="text-rose-500">
                        {val.hmWma21 != null ? val.hmWma21.toFixed(1) : '—'}
                      </strong>
                    </>
                  )}
                  <button
                    type="button"
                    onClick={() => handleToggleHideIndicator('hilega')}
                    aria-label={hidden ? `Show ${meta.label}` : `Hide ${meta.label}`}
                    className="w-5 h-5 flex items-center justify-center rounded hover:bg-[var(--bg-elevated)] text-[var(--text-muted)] hover:text-[var(--text-primary)]"
                  >
                    {hidden ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleRemoveIndicator('hilega')}
                    aria-label={`Remove ${meta.label}`}
                    className="w-5 h-5 flex items-center justify-center rounded hover:bg-rose-500/15 text-[var(--text-muted)] hover:text-rose-500"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </div>
              );
            })()}
          </div>
        )}

        <div
          ref={rsiContainerRef}
          className={showOscCanvas ? 'flex-[34] min-h-0 w-full relative' : 'hidden'}
        />
      </div>

      {/* Key Metrics Strip */}
      <footer className="shrink-0 bg-[var(--bg-card)] border-t border-[var(--border-subtle)] px-3 py-2">
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
