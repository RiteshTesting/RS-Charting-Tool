export function formatINR(value, decimals = 2) {
  if (value == null || Number.isNaN(Number(value))) return '₹0.00';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(Number(value));
}

export function formatNumberIN(value, decimals = 0) {
  if (value == null || Number.isNaN(Number(value))) return '0';
  return new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(Number(value));
}

export function formatPct(value, showPlus = true) {
  if (value == null || Number.isNaN(Number(value))) return '0.00%';
  const num = Number(value);
  const sign = showPlus && num > 0 ? '+' : '';
  return `${sign}${num.toFixed(2)}%`;
}

export function exportTextFile(filename, mimeType, content) {
  if (
    typeof window !== 'undefined' &&
    window.AndroidBridge &&
    typeof window.AndroidBridge.exportFile === 'function'
  ) {
    window.AndroidBridge.exportFile(filename, mimeType, content);
    return { method: 'android', filename };
  }
  if (
    typeof window !== 'undefined' &&
    window.AndroidBridge &&
    typeof window.AndroidBridge.exportCsv === 'function'
  ) {
    window.AndroidBridge.exportCsv(filename, content);
    return { method: 'android', filename };
  }

  const blob = new Blob([content], { type: `${mimeType};charset=utf-8;` });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
  return { method: 'web', filename };
}

export function exportWatchlistToCsv(stocks, notesMap) {
  const headers = [
    'Symbol',
    'Name',
    'Sector',
    'Setup',
    'Close_INR',
    'Change_Pct',
    'RS_Rating',
    'Daily_RSI14',
    'Weekly_RSI14',
    'Vol_Multiplier',
    'Avg_20D_Turnover_Cr',
    'Swing_Low_SL_INR',
    'Notes',
  ];

  const escapeCsv = (val) => {
    const str = String(val ?? '').replace(/"/g, '""');
    return `"${str}"`;
  };

  const rows = stocks.map((s) => [
    s.symbol,
    s.name,
    s.sector,
    s.setupBadge,
    s.metrics.close.toFixed(2),
    s.metrics.changePct.toFixed(2),
    s.metrics.rsRating,
    s.metrics.dailyRsi.toFixed(2),
    s.metrics.weeklyRsi.toFixed(2),
    s.metrics.volMultiplier.toFixed(2),
    s.metrics.avgTurnoverCr.toFixed(1),
    s.metrics.swingLowStop.toFixed(2),
    notesMap[s.symbol] || '',
  ]);

  const csvContent = [headers, ...rows]
    .map((row) => row.map(escapeCsv).join(','))
    .join('\n');

  const filename = `swingpulse_watchlist_${new Date().toISOString().slice(0, 10)}.csv`;
  return exportTextFile(filename, 'text/csv', csvContent);
}

export function exportJournalToCsv(entries) {
  const headers = [
    'ID',
    'Symbol',
    'Status',
    'Entry_Date',
    'Setup_Tag',
    'Quantity',
    'Entry_Price_INR',
    'Stop_Loss_INR',
    'Target_Exit_INR',
    'Net_PnL_INR',
    'R_Multiple',
    'Trade_Notes',
  ];

  const escapeCsv = (val) => {
    const str = String(val ?? '').replace(/"/g, '""');
    return `"${str}"`;
  };

  const rows = entries.map((e) => {
    const entry = parseFloat(e.entryPrice) || 0;
    const sl = parseFloat(e.stopLoss) || 0;
    const exit = parseFloat(e.exitPrice) || 0;
    const qty = parseFloat(e.quantity) || 0;
    const isClosed = e.status !== 'Open';
    const pnl = isClosed ? (exit - entry) * qty : 0;
    const riskPerShare = entry - sl;
    const rMult = isClosed && riskPerShare > 0 ? (exit - entry) / riskPerShare : null;

    return [
      e.id,
      e.symbol,
      e.status,
      e.entryDate,
      e.setupTag,
      qty,
      entry.toFixed(2),
      sl.toFixed(2),
      exit.toFixed(2),
      isClosed ? pnl.toFixed(2) : '',
      rMult !== null ? rMult.toFixed(2) : '',
      e.notes || '',
    ];
  });

  const csvContent = [headers, ...rows]
    .map((row) => row.map(escapeCsv).join(','))
    .join('\n');

  const filename = `swingpulse_journal_${new Date().toISOString().slice(0, 10)}.csv`;
  return exportTextFile(filename, 'text/csv', csvContent);
}
