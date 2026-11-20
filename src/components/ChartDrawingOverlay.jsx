import React, { useCallback, useEffect, useRef, useState } from 'react';
import { LineStyle } from 'lightweight-charts';
import { timeToXCoordinate, xCoordinateToTime } from '../utils/chartResampler';

const DRAWINGS_STORAGE_KEY = 'rs_chart_drawings_v1';

function readAllDrawings() {
  try {
    const raw = localStorage.getItem(DRAWINGS_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') return parsed;
    }
  } catch {
    // ignore storage errors
  }
  return {};
}

export function loadDrawingsForSymbol(symbol) {
  if (!symbol) return [];
  const all = readAllDrawings();
  return Array.isArray(all[symbol]) ? all[symbol] : [];
}

export function saveDrawingsForSymbol(symbol, drawings) {
  if (!symbol) return;
  try {
    const all = readAllDrawings();
    if (!drawings || drawings.length === 0) {
      delete all[symbol];
    } else {
      all[symbol] = drawings;
    }
    localStorage.setItem(DRAWINGS_STORAGE_KEY, JSON.stringify(all));
  } catch {
    // ignore storage errors
  }
}

export default function ChartDrawingOverlay({
  priceChartRef,
  candleSeriesRef,
  containerRef,
  candles,
  timeframe,
  symbol,
  theme,
  activeTool, // 'cursor' | 'hline' | 'trendline'
  drawings,
  selectedId,
  onSelectDrawing,
  onChangeDrawings,
  onToolComplete,
  priceScaleVersion,
}) {
  const overlayRef = useRef(null);
  const priceLinesMapRef = useRef(new Map());
  const [, setRenderTick] = useState(0);

  // Draft state for 2-tap trendline creation
  const [draftP1, setDraftP1] = useState(null);
  const [draftPreview, setDraftPreview] = useState(null);

  // Active drag state (hline move or trendline p1/p2/body stretch)
  const dragRef = useRef(null);
  const [dragBadge, setDragBadge] = useState(null);

  // Clear pending draft when tool or symbol changes
  useEffect(() => {
    setDraftP1(null);
    setDraftPreview(null);
  }, [activeTool, symbol, timeframe]);

  const forceRepaint = useCallback(() => {
    setRenderTick((t) => (t + 1) % 1000000);
  }, []);

  // Recompute overlay coordinates when chart scrolls, zooms, or resizes
  useEffect(() => {
    const chart = priceChartRef.current;
    const el = containerRef.current;
    if (!chart) return;

    let rafId = null;
    const scheduleRepaint = () => {
      forceRepaint();
      if (rafId != null) cancelAnimationFrame(rafId);
      rafId = requestAnimationFrame(() => {
        rafId = null;
        forceRepaint();
      });
    };

    chart.timeScale().subscribeVisibleLogicalRangeChange(scheduleRepaint);

    const onChartClick = () => {
      if (activeTool === 'cursor') {
        onSelectDrawing(null);
      }
    };
    chart.subscribeClick(onChartClick);

    let ro = null;
    if (el && typeof ResizeObserver !== 'undefined') {
      ro = new ResizeObserver(() => scheduleRepaint());
      ro.observe(el);
    }

    // One deferred tick after mount/data update so lightweight-charts layout settles
    scheduleRepaint();

    return () => {
      if (rafId != null) cancelAnimationFrame(rafId);
      try {
        chart.timeScale().unsubscribeVisibleLogicalRangeChange(scheduleRepaint);
        chart.unsubscribeClick(onChartClick);
      } catch {
        // chart may already be disposed
      }
      ro?.disconnect();
    };
  }, [priceChartRef, containerRef, candles, timeframe, priceScaleVersion, activeTool, onSelectDrawing, forceRepaint]);

  // Sync horizontal lines with native lightweight-charts priceLines so right-axis tags render crisply
  useEffect(() => {
    const series = candleSeriesRef.current;
    if (!series) return;

    const map = priceLinesMapRef.current;
    const activeHlines = drawings.filter((d) => d.type === 'hline');
    const activeIds = new Set(activeHlines.map((d) => d.id));

    // Remove deleted horizontal lines
    for (const [id, pl] of map.entries()) {
      if (!activeIds.has(id)) {
        try {
          series.removePriceLine(pl);
        } catch {
          // ignore
        }
        map.delete(id);
      }
    }

    // Create or update horizontal lines
    activeHlines.forEach((d) => {
      const isSelected = d.id === selectedId;
      const lineOpts = {
        price: d.price,
        color: isSelected ? '#FBBF24' : d.color || '#F59E0B',
        lineWidth: isSelected ? 2 : 2,
        lineStyle: LineStyle.Solid,
        axisLabelVisible: true,
        title: isSelected ? 'H-Line' : '',
      };

      const existing = map.get(d.id);
      if (existing) {
        try {
          existing.applyOptions(lineOpts);
        } catch {
          try {
            series.removePriceLine(existing);
          } catch {
            // ignore
          }
          map.set(d.id, series.createPriceLine(lineOpts));
        }
      } else {
        map.set(d.id, series.createPriceLine(lineOpts));
      }
    });
  }, [candleSeriesRef, drawings, selectedId, symbol]);

  // Clean up all native priceLines when unmounting
  useEffect(() => {
    return () => {
      const series = candleSeriesRef.current;
      const map = priceLinesMapRef.current;
      if (series) {
        for (const pl of map.values()) {
          try {
            series.removePriceLine(pl);
          } catch {
            // ignore
          }
        }
      }
      map.clear();
    };
  }, [candleSeriesRef]);

  const getLocalCoords = (clientX, clientY) => {
    const rect = overlayRef.current?.getBoundingClientRect();
    if (!rect) return null;
    const x = Math.max(0, Math.min(rect.width - 56, clientX - rect.left));
    const y = Math.max(0, Math.min(rect.height, clientY - rect.top));
    return { x, y, width: rect.width, height: rect.height };
  };

  const coordsToPoint = (x, y) => {
    const chart = priceChartRef.current;
    const series = candleSeriesRef.current;
    if (!chart || !series) return null;
    const price = series.coordinateToPrice(y);
    const time = xCoordinateToTime(chart.timeScale(), candles, x);
    if (price == null || !Number.isFinite(price) || !time) return null;
    return { time, price: +price.toFixed(2) };
  };

  // Placement tap/click handler when tool is 'hline' or 'trendline'
  const handlePlacementPointerDown = (e) => {
    if (activeTool === 'cursor') return;
    const pos = getLocalCoords(e.clientX, e.clientY);
    if (!pos) return;
    // Ignore taps on the right price axis strip
    if (e.clientX - overlayRef.current.getBoundingClientRect().left > pos.width - 56) return;

    const pt = coordsToPoint(pos.x, pos.y);
    if (!pt) return;

    e.preventDefault();
    e.stopPropagation();

    if (activeTool === 'hline') {
      const newDrawing = {
        id: `hl_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        type: 'hline',
        price: pt.price,
        color: '#F59E0B',
      };
      onChangeDrawings([...drawings, newDrawing]);
      onSelectDrawing(newDrawing.id);
      onToolComplete();
    } else if (activeTool === 'trendline') {
      if (!draftP1) {
        setDraftP1(pt);
        setDraftPreview(pt);
      } else {
        const newDrawing = {
          id: `tl_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
          type: 'trendline',
          p1: draftP1,
          p2: pt,
          color: '#38BDF8',
        };
        onChangeDrawings([...drawings, newDrawing]);
        onSelectDrawing(newDrawing.id);
        setDraftP1(null);
        setDraftPreview(null);
        onToolComplete();
      }
    }
  };

  const handlePlacementPointerMove = (e) => {
    if (activeTool !== 'trendline' || !draftP1) return;
    const pos = getLocalCoords(e.clientX, e.clientY);
    if (!pos) return;
    const pt = coordsToPoint(pos.x, pos.y);
    if (pt) setDraftPreview(pt);
  };

  // Start dragging an existing horizontal line or trendline handle
  const startDrag = (e, drawing, part) => {
    e.preventDefault();
    e.stopPropagation();
    onSelectDrawing(drawing.id);

    const pos = getLocalCoords(e.clientX, e.clientY);
    if (!pos) return;

    if (e.currentTarget.setPointerCapture) {
      try {
        e.currentTarget.setPointerCapture(e.pointerId);
      } catch {
        // ignore if unsupported
      }
    }

    dragRef.current = {
      id: drawing.id,
      type: drawing.type,
      part, // 'hline' | 'p1' | 'p2' | 'body'
      startX: pos.x,
      startY: pos.y,
      initialDrawing: drawing,
      pointerId: e.pointerId,
    };
  };

  const handleDragMove = (e) => {
    const drag = dragRef.current;
    if (!drag) return;
    const pos = getLocalCoords(e.clientX, e.clientY);
    if (!pos) return;

    const chart = priceChartRef.current;
    const series = candleSeriesRef.current;
    if (!chart || !series) return;

    e.preventDefault();
    e.stopPropagation();

    if (drag.type === 'hline') {
      const nextPrice = series.coordinateToPrice(pos.y);
      if (nextPrice == null || !Number.isFinite(nextPrice)) return;
      const rounded = +nextPrice.toFixed(2);
      onChangeDrawings(
        drawings.map((d) => (d.id === drag.id ? { ...d, price: rounded } : d))
      );
      setDragBadge({ x: Math.min(pos.x, pos.width - 120), y: Math.max(24, pos.y - 26), label: `₹${rounded.toFixed(2)}` });
    } else if (drag.type === 'trendline') {
      if (drag.part === 'p1' || drag.part === 'p2') {
        const pt = coordsToPoint(pos.x, pos.y);
        if (!pt) return;
        onChangeDrawings(
          drawings.map((d) => (d.id === drag.id ? { ...d, [drag.part]: pt } : d))
        );
        setDragBadge({ x: Math.min(pos.x + 12, pos.width - 120), y: Math.max(24, pos.y - 26), label: `₹${pt.price.toFixed(2)}` });
      } else if (drag.part === 'body') {
        const dx = pos.x - drag.startX;
        const dy = pos.y - drag.startY;
        const orig = drag.initialDrawing;
        const x1 = timeToXCoordinate(chart.timeScale(), candles, orig.p1.time);
        const y1 = series.priceToCoordinate(orig.p1.price);
        const x2 = timeToXCoordinate(chart.timeScale(), candles, orig.p2.time);
        const y2 = series.priceToCoordinate(orig.p2.price);
        if (x1 == null || y1 == null || x2 == null || y2 == null) return;
        const nextP1 = coordsToPoint(x1 + dx, y1 + dy);
        const nextP2 = coordsToPoint(x2 + dx, y2 + dy);
        if (!nextP1 || !nextP2) return;
        onChangeDrawings(
          drawings.map((d) => (d.id === drag.id ? { ...d, p1: nextP1, p2: nextP2 } : d))
        );
      }
    }
  };

  const endDrag = (e) => {
    if (!dragRef.current) return;
    if (e?.currentTarget?.releasePointerCapture && dragRef.current.pointerId != null) {
      try {
        e.currentTarget.releasePointerCapture(dragRef.current.pointerId);
      } catch {
        // ignore
      }
    }
    dragRef.current = null;
    setDragBadge(null);
  };

  const chart = priceChartRef.current;
  const series = candleSeriesRef.current;
  const timeScale = chart ? chart.timeScale() : null;

  const isPlacing = activeTool === 'hline' || activeTool === 'trendline';

  // Compute pixel coordinates for trendlines & horizontal lines
  const renderedHlines = [];
  const renderedTrendlines = [];

  if (chart && series && timeScale && candles.length > 0) {
    drawings.forEach((d) => {
      if (d.type === 'hline') {
        const y = series.priceToCoordinate(d.price);
        if (y != null && Number.isFinite(y)) {
          renderedHlines.push({ ...d, y });
        }
      } else if (d.type === 'trendline' && d.p1 && d.p2) {
        const x1 = timeToXCoordinate(timeScale, candles, d.p1.time);
        const y1 = series.priceToCoordinate(d.p1.price);
        const x2 = timeToXCoordinate(timeScale, candles, d.p2.time);
        const y2 = series.priceToCoordinate(d.p2.price);
        if (
          x1 != null &&
          y1 != null &&
          x2 != null &&
          y2 != null &&
          Number.isFinite(x1) &&
          Number.isFinite(y1) &&
          Number.isFinite(x2) &&
          Number.isFinite(y2)
        ) {
          renderedTrendlines.push({ ...d, x1, y1, x2, y2 });
        }
      }
    });
  }

  let draftCoords = null;
  if (draftP1 && chart && series && timeScale && candles.length > 0) {
    const x1 = timeToXCoordinate(timeScale, candles, draftP1.time);
    const y1 = series.priceToCoordinate(draftP1.price);
    const p2 = draftPreview || draftP1;
    const x2 = timeToXCoordinate(timeScale, candles, p2.time);
    const y2 = series.priceToCoordinate(p2.price);
    if (x1 != null && y1 != null) {
      draftCoords = {
        x1,
        y1,
        x2: x2 ?? x1,
        y2: y2 ?? y1,
      };
    }
  }

  return (
    <div
      ref={overlayRef}
      onPointerDown={isPlacing ? handlePlacementPointerDown : undefined}
      onPointerMove={isPlacing ? handlePlacementPointerMove : undefined}
      className={`absolute inset-0 right-[56px] z-10 overflow-hidden ${
        isPlacing ? 'pointer-events-auto cursor-crosshair touch-none' : 'pointer-events-none'
      }`}
    >
      {/* Placement Guidance Pill */}
      {isPlacing && (
        <div className="absolute bottom-2.5 left-2.5 z-30 pointer-events-none px-2.5 py-1 rounded-lg bg-emerald-600 text-white text-[11px] font-semibold shadow-lg">
          {activeTool === 'hline'
            ? 'Tap chart to place Horizontal Line'
            : !draftP1
              ? 'Tap Point A (start of trendline)'
              : 'Tap Point B (end of trendline)'}
        </div>
      )}

      {/* Horizontal Line Draggable Hit Strips */}
      {!isPlacing &&
        renderedHlines.map((hl) => {
          const isSelected = hl.id === selectedId;
          return (
            <div
              key={hl.id}
              onPointerDown={(e) => startDrag(e, hl, 'hline')}
              onPointerMove={handleDragMove}
              onPointerUp={endDrag}
              onPointerCancel={endDrag}
              style={{ top: `${hl.y - 14}px` }}
              title={`Drag up/down to adjust ₹${hl.price.toFixed(2)}`}
              className="absolute left-0 right-0 h-7 pointer-events-auto touch-none cursor-ns-resize flex items-center group"
            >
              {/* Subtle selection halo */}
              {isSelected && (
                <div className="w-full h-[2px] bg-amber-400/90 shadow-[0_0_8px_rgba(251,191,36,0.65)]" />
              )}
              <span
                className={`absolute left-2 px-1.5 py-0.5 rounded text-[9px] font-bold tabular-nums border shadow-sm select-none transition ${
                  isSelected
                    ? 'bg-amber-500 text-slate-950 border-amber-300'
                    : theme === 'light'
                      ? 'bg-white/95 text-amber-700 border-amber-400/70 opacity-85 group-hover:opacity-100'
                      : 'bg-slate-900/95 text-amber-300 border-amber-500/50 opacity-85 group-hover:opacity-100'
                }`}
              >
                ↕ ₹{hl.price.toFixed(1)}
              </span>
            </div>
          );
        })}

      {/* Trendlines + Draft Trendline SVG Layer */}
      <svg className="w-full h-full overflow-visible pointer-events-none">
        {/* Live Rubber-band Draft Trendline */}
        {draftCoords && (
          <g>
            <line
              x1={draftCoords.x1}
              y1={draftCoords.y1}
              x2={draftCoords.x2}
              y2={draftCoords.y2}
              stroke="#38BDF8"
              strokeWidth="2"
              strokeDasharray="6 4"
            />
            <circle
              cx={draftCoords.x1}
              cy={draftCoords.y1}
              r="6"
              fill="#38BDF8"
              stroke="#0B0E14"
              strokeWidth="2"
            />
            <circle
              cx={draftCoords.x2}
              cy={draftCoords.y2}
              r="4"
              fill="#38BDF8"
              opacity="0.7"
            />
          </g>
        )}

        {/* Persisted Trendlines */}
        {renderedTrendlines.map((tl) => {
          const isSelected = tl.id === selectedId;
          const strokeColor = isSelected ? '#38BDF8' : tl.color || '#0EA5E9';
          return (
            <g key={tl.id}>
              {/* Generous touch/click hit target on the line segment */}
              {!isPlacing && (
                <line
                  x1={tl.x1}
                  y1={tl.y1}
                  x2={tl.x2}
                  y2={tl.y2}
                  stroke="transparent"
                  strokeWidth="22"
                  style={{ pointerEvents: 'stroke', touchAction: 'none', cursor: 'move' }}
                  onPointerDown={(e) => startDrag(e, tl, 'body')}
                  onPointerMove={handleDragMove}
                  onPointerUp={endDrag}
                  onPointerCancel={endDrag}
                />
              )}

              {/* Visible crisp trendline */}
              <line
                x1={tl.x1}
                y1={tl.y1}
                x2={tl.x2}
                y2={tl.y2}
                stroke={strokeColor}
                strokeWidth={isSelected ? '2.5' : '2'}
                strokeLinecap="round"
              />

              {/* Endpoint A Handle (visible dot + generous 34px touch target) */}
              <circle
                cx={tl.x1}
                cy={tl.y1}
                r={isSelected ? '6' : '4.5'}
                fill={theme === 'light' ? '#FFFFFF' : '#0B0E14'}
                stroke={strokeColor}
                strokeWidth="2.5"
              />
              {!isPlacing && (
                <circle
                  cx={tl.x1}
                  cy={tl.y1}
                  r="17"
                  fill="transparent"
                  style={{ pointerEvents: 'all', touchAction: 'none', cursor: 'grab' }}
                  onPointerDown={(e) => startDrag(e, tl, 'p1')}
                  onPointerMove={handleDragMove}
                  onPointerUp={endDrag}
                  onPointerCancel={endDrag}
                />
              )}

              {/* Endpoint B Handle (visible dot + generous 34px touch target) */}
              <circle
                cx={tl.x2}
                cy={tl.y2}
                r={isSelected ? '6' : '4.5'}
                fill={theme === 'light' ? '#FFFFFF' : '#0B0E14'}
                stroke={strokeColor}
                strokeWidth="2.5"
              />
              {!isPlacing && (
                <circle
                  cx={tl.x2}
                  cy={tl.y2}
                  r="17"
                  fill="transparent"
                  style={{ pointerEvents: 'all', touchAction: 'none', cursor: 'grab' }}
                  onPointerDown={(e) => startDrag(e, tl, 'p2')}
                  onPointerMove={handleDragMove}
                  onPointerUp={endDrag}
                  onPointerCancel={endDrag}
                />
              )}
            </g>
          );
        })}
      </svg>

      {/* Live Price Tooltip while dragging */}
      {dragBadge && (
        <div
          style={{ left: `${dragBadge.x}px`, top: `${dragBadge.y}px` }}
          className="absolute z-30 pointer-events-none px-2 py-0.5 rounded-md bg-slate-900 text-amber-300 border border-amber-400/50 text-[10px] font-bold tabular-nums shadow-md"
        >
          {dragBadge.label}
        </div>
      )}
    </div>
  );
}
