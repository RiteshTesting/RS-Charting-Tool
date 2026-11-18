/**
 * SwingPulse NSE - Local Notification Service
 *
 * Uses the standard browser Notification API (`window.Notification`) and installs a seamless
 * polyfill backed by `window.AndroidBridge` when running inside the Android WebView APK
 * where `window.Notification` is not natively defined.
 */

let pendingPermissionResolver = null;

export function installAndroidNotificationPolyfillIfNeeded() {
  if (typeof window === 'undefined') return;
  if (typeof window.Notification !== 'undefined') return;
  if (!window.AndroidBridge || typeof window.AndroidBridge.showLocalNotification !== 'function') {
    return;
  }

  window.__onAndroidNotificationPermission = (status) => {
    const normalized = status === 'granted' ? 'granted' : 'denied';
    AndroidNotification.permission = normalized;
    if (pendingPermissionResolver) {
      pendingPermissionResolver(normalized);
      pendingPermissionResolver = null;
    }
  };

  class AndroidNotification {
    constructor(title, options = {}) {
      this.title = title;
      this.body = options.body || '';
      this.tag = options.tag || '';
      if (AndroidNotification.permission === 'granted') {
        window.AndroidBridge.showLocalNotification(this.title, this.body, this.tag);
      }
    }

    static requestPermission() {
      return new Promise((resolve) => {
        const current = window.AndroidBridge.getNotificationPermission();
        AndroidNotification.permission = current;
        if (current === 'granted') {
          resolve('granted');
          return;
        }
        pendingPermissionResolver = resolve;
        window.AndroidBridge.requestNotificationPermission();
      });
    }

    close() {}
  }

  AndroidNotification.permission = window.AndroidBridge.getNotificationPermission() || 'default';
  window.Notification = AndroidNotification;
}

export function getNotificationPermissionStatus() {
  installAndroidNotificationPolyfillIfNeeded();
  if (typeof window === 'undefined' || typeof window.Notification === 'undefined') {
    return 'unsupported';
  }
  return window.Notification.permission || 'default';
}

export async function requestBrowserNotificationPermission() {
  installAndroidNotificationPolyfillIfNeeded();
  if (typeof window === 'undefined' || typeof window.Notification === 'undefined') {
    return 'unsupported';
  }
  try {
    const result = await window.Notification.requestPermission();
    return result;
  } catch {
    return window.Notification.permission || 'denied';
  }
}

export function dispatchBrowserNotification(title, body, tag) {
  installAndroidNotificationPolyfillIfNeeded();
  if (typeof window === 'undefined' || typeof window.Notification === 'undefined') {
    return false;
  }
  if (window.Notification.permission !== 'granted') {
    return false;
  }
  try {
    new window.Notification(title, {
      body,
      tag,
      renotify: true,
    });
    return true;
  } catch {
    return false;
  }
}

/**
 * Evaluates all armed watchlist alerts against latest stock prices/candles.
 * Returns `{ triggeredAlerts, updatedAlertsMap }`.
 */
export function evaluateWatchlistAlerts(stocks, watchlist, alertsMap) {
  const stockBySymbol = new Map(stocks.map((s) => [s.symbol, s]));
  const updatedAlertsMap = { ...alertsMap };
  const triggeredAlerts = [];
  const nowIso = new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });

  for (const symbol of watchlist) {
    const cfg = alertsMap[symbol];
    const stock = stockBySymbol.get(symbol);
    if (!cfg || !cfg.enabled || !stock) continue;

    const latest = stock.candles[stock.candles.length - 1];
    const close = stock.metrics.close;
    const high = latest?.high ?? close;
    const low = latest?.low ?? close;

    const entryTarget = parseFloat(cfg.entryPrice);
    const slTarget = parseFloat(cfg.stopLossPrice);
    const nextCfg = { ...cfg };

    // Entry trigger check
    if (Number.isFinite(entryTarget) && entryTarget > 0 && !cfg.triggeredEntry) {
      const mode = cfg.entryMode || 'above';
      const entryHit =
        mode === 'below'
          ? close <= entryTarget || low <= entryTarget
          : close >= entryTarget || high >= entryTarget;

      if (entryHit) {
        nextCfg.triggeredEntry = true;
        nextCfg.lastTriggeredAt = nowIso;
        const title = `${symbol} Entry Alert Hit (₹${entryTarget.toFixed(2)})`;
        const body = `${symbol} reached ₹${close.toFixed(2)} (${
          stock.metrics.changePct >= 0 ? '+' : ''
        }${stock.metrics.changePct.toFixed(2)}%) • RS ${stock.metrics.rsRating}`;

        dispatchBrowserNotification(title, body, `entry-${symbol}-${entryTarget}`);
        triggeredAlerts.push({
          id: `${symbol}-ENTRY-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          symbol,
          type: 'ENTRY',
          targetPrice: entryTarget,
          currentPrice: close,
          time: nowIso,
        });
      }
    }

    // Stop-Loss trigger check
    if (Number.isFinite(slTarget) && slTarget > 0 && !cfg.triggeredSl) {
      const slHit = close <= slTarget || low <= slTarget;
      if (slHit) {
        nextCfg.triggeredSl = true;
        nextCfg.lastTriggeredAt = nowIso;
        const title = `${symbol} Stop-Loss Hit (₹${slTarget.toFixed(2)})`;
        const body = `${symbol} breached stop-loss at ₹${close.toFixed(2)} (${
          stock.metrics.changePct >= 0 ? '+' : ''
        }${stock.metrics.changePct.toFixed(2)}%)`;

        dispatchBrowserNotification(title, body, `sl-${symbol}-${slTarget}`);
        triggeredAlerts.push({
          id: `${symbol}-SL-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          symbol,
          type: 'STOP_LOSS',
          targetPrice: slTarget,
          currentPrice: close,
          time: nowIso,
        });
      }
    }

    updatedAlertsMap[symbol] = nextCfg;
  }

  return { triggeredAlerts, updatedAlertsMap };
}
