import React, { useEffect, useRef, useState } from 'react';
import {
  Layers,
  ChevronDown,
  Check,
  Eye,
  EyeOff,
  X,
  RotateCcw,
} from 'lucide-react';
import {
  INDICATOR_CATALOG,
  INDICATOR_BY_ID,
  formatIndicatorLiveValue,
} from '../utils/chartIndicatorsConfig';

export function IndicatorDropdownButton({
  indicatorState,
  theme,
  onToggleAdd,
  onToggleHide,
  onResetDefaults,
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        setOpen(false);
      }
    };
    window.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('keydown', onKeyDown, true);
    return () => {
      window.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('keydown', onKeyDown, true);
    };
  }, [open]);

  const overlays = INDICATOR_CATALOG.filter((i) => i.group !== 'oscillator');
  const oscillators = INDICATOR_CATALOG.filter((i) => i.group === 'oscillator');
  const activeCount = indicatorState.added.length;

  const renderRow = (item) => {
    const isAdded = indicatorState.added.includes(item.id);
    const isHidden = Boolean(indicatorState.hidden[item.id]);
    const dotColor = theme === 'light' ? item.lightColor : item.darkColor;

    return (
      <div
        key={item.id}
        className={`flex items-center justify-between gap-1.5 px-2.5 py-1.5 rounded-lg transition ${
          isAdded
            ? 'bg-emerald-500/10 text-[var(--text-primary)]'
            : 'hover:bg-[var(--bg-elevated)] text-[var(--text-secondary)]'
        }`}
      >
        <button
          type="button"
          onClick={() => onToggleAdd(item.id)}
          className="flex-1 min-w-0 flex items-center gap-2 text-left text-xs font-semibold py-0.5"
        >
          <span
            className="w-2.5 h-2.5 rounded-full shrink-0"
            style={{ backgroundColor: dotColor, opacity: isAdded && isHidden ? 0.35 : 1 }}
          />
          <span className={`truncate ${isAdded && isHidden ? 'line-through opacity-60' : ''}`}>
            {item.label}
          </span>
        </button>

        <div className="flex items-center gap-1 shrink-0">
          {isAdded && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onToggleHide(item.id);
              }}
              aria-label={isHidden ? `Show ${item.label}` : `Hide ${item.label}`}
              title={isHidden ? 'Show indicator' : 'Hide indicator'}
              className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-[var(--bg-input)] text-[var(--text-muted)] hover:text-[var(--text-primary)] transition"
            >
              {isHidden ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5 text-emerald-500" />}
            </button>
          )}
          <button
            type="button"
            onClick={() => onToggleAdd(item.id)}
            aria-label={isAdded ? `Remove ${item.label}` : `Add ${item.label}`}
            className={`w-6 h-6 flex items-center justify-center rounded-md border transition ${
              isAdded
                ? 'bg-emerald-500 border-emerald-500 text-white'
                : 'border-[var(--border-subtle)] text-transparent hover:border-emerald-500/60'
            }`}
          >
            <Check className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    );
  };

  return (
    <div ref={rootRef} className="relative shrink-0">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-haspopup="menu"
        className={`h-8 px-2.5 rounded-lg border text-[11px] font-semibold flex items-center gap-1.5 active:scale-95 transition ${
          open
            ? 'bg-emerald-500/15 border-emerald-500/60 text-emerald-600 dark:text-emerald-300'
            : 'bg-[var(--bg-card)] border-[var(--border-subtle)] text-[var(--text-primary)] hover:border-emerald-500/40'
        }`}
      >
        <Layers className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
        <span>Indicators</span>
        {activeCount > 0 && (
          <span className="px-1.5 py-0.2 rounded-full bg-emerald-500/20 text-emerald-600 dark:text-emerald-300 text-[10px] font-bold tabular-nums">
            {activeCount}
          </span>
        )}
        <ChevronDown className={`w-3.5 h-3.5 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div
          role="menu"
          className="fixed sm:absolute left-2 right-2 sm:left-0 sm:right-auto mt-1.5 sm:w-64 max-h-[65vh] overflow-y-auto rounded-xl bg-[var(--bg-card)] border border-[var(--border-subtle)] shadow-2xl p-2 z-50"
        >
          <div className="px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            Price Overlays & Volume
          </div>
          <div className="space-y-0.5">{overlays.map(renderRow)}</div>

          <div className="mt-2 pt-2 border-t border-[var(--border-subtle)] px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            Oscillators (Sub-Pane)
          </div>
          <div className="space-y-0.5">{oscillators.map(renderRow)}</div>

          <div className="mt-2 pt-2 border-t border-[var(--border-subtle)] flex items-center justify-between px-1">
            <button
              type="button"
              onClick={() => {
                onResetDefaults();
                setOpen(false);
              }}
              className="text-[11px] font-semibold text-[var(--text-muted)] hover:text-[var(--text-primary)] flex items-center gap-1 px-2 py-1 rounded-md hover:bg-[var(--bg-elevated)] transition"
            >
              <RotateCcw className="w-3 h-3" />
              Reset Default
            </button>
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="text-[11px] font-bold text-emerald-500 px-2 py-1 rounded-md hover:bg-emerald-500/10 transition"
            >
              Done
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export function FloatingIndicatorPills({
  indicatorState,
  val,
  activeBar,
  theme,
  onToggleHide,
  onRemove,
}) {
  const [collapsed, setCollapsed] = useState(false);

  const priceIds = indicatorState.added.filter((id) => {
    const meta = INDICATOR_BY_ID[id];
    return meta && meta.group !== 'oscillator';
  });

  if (priceIds.length === 0) return null;

  return (
    <div className="absolute top-1.5 left-1.5 z-20 pointer-events-none flex flex-wrap items-center gap-1 max-w-[calc(100%-68px)]">
      {priceIds.length > 2 && (
        <button
          type="button"
          onClick={() => setCollapsed((c) => !c)}
          aria-label={collapsed ? 'Expand indicator legend' : 'Collapse indicator legend'}
          className="pointer-events-auto h-6 px-1.5 rounded-md bg-[var(--bg-card)] border border-[var(--border-subtle)] text-[10px] font-bold text-[var(--text-secondary)] hover:text-[var(--text-primary)] shadow-sm flex items-center gap-0.5"
        >
          <Layers className="w-3 h-3 text-emerald-500" />
          <span>{collapsed ? `${priceIds.length}` : ''}</span>
          <ChevronDown className={`w-3 h-3 transition-transform ${collapsed ? '-rotate-90' : ''}`} />
        </button>
      )}

      {!collapsed &&
        priceIds.map((id) => {
          const meta = INDICATOR_BY_ID[id];
          if (!meta) return null;
          const isHidden = Boolean(indicatorState.hidden[id]);
          const dotColor = theme === 'light' ? meta.lightColor : meta.darkColor;
          const liveValue = formatIndicatorLiveValue(id, val, activeBar);

          return (
            <div
              key={id}
              className={`pointer-events-auto inline-flex items-center gap-1 pl-1.5 pr-0.5 h-6 rounded-md bg-[var(--bg-card)] border border-[var(--border-subtle)] shadow-sm text-[10px] tabular-nums transition ${
                isHidden ? 'opacity-55' : 'opacity-95'
              }`}
            >
              <span
                className="w-2 h-2 rounded-full shrink-0"
                style={{ backgroundColor: dotColor }}
              />
              <span className={`font-semibold text-[var(--text-primary)] ${isHidden ? 'line-through' : ''}`}>
                {meta.shortLabel}
              </span>
              {!isHidden && (
                <span className="font-bold" style={{ color: dotColor }}>
                  {liveValue}
                </span>
              )}
              <button
                type="button"
                onClick={() => onToggleHide(id)}
                aria-label={isHidden ? `Show ${meta.label}` : `Hide ${meta.label}`}
                title={isHidden ? `Show ${meta.label}` : `Hide ${meta.label}`}
                className="w-5 h-5 flex items-center justify-center rounded hover:bg-[var(--bg-elevated)] text-[var(--text-muted)] hover:text-[var(--text-primary)] transition"
              >
                {isHidden ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
              </button>
              <button
                type="button"
                onClick={() => onRemove(id)}
                aria-label={`Remove ${meta.label}`}
                title={`Remove ${meta.label}`}
                className="w-5 h-5 flex items-center justify-center rounded hover:bg-rose-500/15 text-[var(--text-muted)] hover:text-rose-500 transition"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          );
        })}
    </div>
  );
}
