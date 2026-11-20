import io
import json
import os
import urllib.request
from datetime import datetime, timezone, timedelta
import numpy as np
import pandas as pd
import yfinance as yf

BENCHMARK = "^NSEI"

def get_nifty500_symbols():
    url = "https://nsearchives.nseindia.com/content/indices/ind_nifty500list.csv"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            csv_text = resp.read().decode("utf-8")
        df_eq = pd.read_csv(io.StringIO(csv_text))
        symbols = [f"{s.strip()}.NS" for s in df_eq["Symbol"].dropna().unique()]
        print(f"Fetched {len(symbols)} symbols from NIFTY 500 list.")
        return symbols
    except Exception as e:
        print(f"NIFTY 500 CSV fallback triggered: {e}")
        return ["RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "ICICIBANK.NS", "INFY.NS", "BEL.NS", "HAL.NS", "TRENT.NS"]

def calc_rsi(series, period=14):
    delta = series.diff()
    gain = delta.clip(lower=0).ewm(alpha=1/period, adjust=False).mean()
    loss = (-delta.clip(upper=0)).ewm(alpha=1/period, adjust=False).mean()
    rs = gain / loss.replace(0, np.nan)
    return 100 - (100 / (1 + rs))

def calc_wma(series, period):
    weights = np.arange(1, period + 1)
    return series.rolling(period).apply(lambda x: np.dot(x, weights) / weights.sum(), raw=True)

def main():
    symbols = get_nifty500_symbols()
    nifty = yf.download(BENCHMARK, period="2y", interval="1d", progress=False)
    if isinstance(nifty.columns, pd.MultiIndex):
        nifty.columns = nifty.columns.get_level_values(0)
    nifty_ret_3m = float(nifty["Close"].iloc[-1] / nifty["Close"].iloc[-63] - 1) if len(nifty) > 63 else 0.0

    stocks_output = []
    batch_size = 250

    for i in range(0, len(symbols), batch_size):
        batch = symbols[i:i + batch_size]
        print(f"Downloading batch {i // batch_size + 1} ({len(batch)} stocks)...")
        data = yf.download(batch, period="2y", interval="1d", group_by="ticker", threads=True, progress=False)

        for sym in batch:
            try:
                df = data[sym].dropna(subset=["Close"]) if sym in data.columns.get_level_values(0) else pd.DataFrame()
                if df.empty or len(df) < 65:
                    continue

                close = df["Close"]
                high = df["High"]
                vol = df["Volume"]

                sma20 = close.rolling(20).mean()
                sma50 = close.rolling(50).mean()
                sma200 = close.rolling(200).mean()
                ema9 = close.ewm(span=9, adjust=False).mean()
                ema20 = close.ewm(span=20, adjust=False).mean()
                ema45 = close.ewm(span=45, adjust=False).mean()

                std20 = close.rolling(20).std()
                bb_upper = sma20 + 2 * std20
                bb_lower = sma20 - 2 * std20

                rsi14 = calc_rsi(close, 14)
                rsi14_ema45 = rsi14.ewm(span=45, adjust=False).mean()

                rsi9 = calc_rsi(close, 9)
                hm_ema3 = rsi9.ewm(span=3, adjust=False).mean()
                hm_wma21 = calc_wma(rsi9, 21)

                ret_3m = float(close.iloc[-1] / close.iloc[-63] - 1)
                ret_6m = float(close.iloc[-1] / close.iloc[-126] - 1) if len(close) > 126 else ret_3m
                rs_raw = (0.6 * (ret_3m - nifty_ret_3m)) + (0.4 * ret_6m)

                vol_avg20 = float(vol.rolling(20).mean().iloc[-1])
                vol_ratio = round(float(vol.iloc[-1]) / vol_avg20, 2) if vol_avg20 > 0 else 1.0
                high_52w = float(high.tail(252).max())
                curr_close = float(close.iloc[-1])
                prev_close = float(close.iloc[-2])
                change_pct = round(((curr_close - prev_close) / prev_close) * 100, 2)

                # ~500 daily candles (2 years) for smooth D / W / M resampling
                candles = [
                    {
                        "time": idx.strftime("%Y-%m-%d"),
                        "open": round(float(r["Open"]), 2),
                        "high": round(float(r["High"]), 2),
                        "low": round(float(r["Low"]), 2),
                        "close": round(float(r["Close"]), 2),
                        "volume": int(r["Volume"])
                    }
                    for idx, r in df.tail(500).iterrows()
                ]

                stocks_output.append({
                    "symbol": sym.replace(".NS", ""),
                    "price": round(curr_close, 2),
                    "changePct": change_pct,
                    "volume": int(vol.iloc[-1]),
                    "volRatio": vol_ratio,
                    "high52w": round(high_52w, 2),
                    "distFrom52wPct": round(((high_52w - curr_close) / high_52w) * 100, 2),
                    "rsi14": round(float(rsi14.iloc[-1]), 2),
                    "rsi14Ema45": round(float(rsi14_ema45.iloc[-1]), 2),
                    "rsi9": round(float(rsi9.iloc[-1]), 2),
                    "hmEma3": round(float(hm_ema3.iloc[-1]), 2),
                    "hmWma21": round(float(hm_wma21.iloc[-1]), 2) if not np.isnan(hm_wma21.iloc[-1]) else 50.0,
                    "sma20": round(float(sma20.iloc[-1]), 2),
                    "sma50": round(float(sma50.iloc[-1]), 2),
                    "sma200": round(float(sma200.iloc[-1]), 2) if not np.isnan(sma200.iloc[-1]) else round(curr_close, 2),
                    "ema9": round(float(ema9.iloc[-1]), 2),
                    "ema20": round(float(ema20.iloc[-1]), 2),
                    "ema45": round(float(ema45.iloc[-1]), 2),
                    "bbUpper": round(float(bb_upper.iloc[-1]), 2),
                    "bbLower": round(float(bb_lower.iloc[-1]), 2),
                    "_rs_raw": rs_raw,
                    "candles": candles
                })
            except Exception:
                continue

    if stocks_output:
        sorted_scores = sorted([s["_rs_raw"] for s in stocks_output])
        n = len(sorted_scores)
        for s in stocks_output:
            rank = sorted_scores.index(s["_rs_raw"])
            s["rsRating"] = max(1, min(99, int(round(((rank + 1) / n) * 99))))
            del s["_rs_raw"]

    ist = timezone(timedelta(hours=5, minutes=30))
    payload = {
        "lastUpdated": datetime.now(ist).strftime("%d %b %Y, %I:%M %p IST"),
        "totalStocks": len(stocks_output),
        "stocks": sorted(stocks_output, key=lambda x: x["rsRating"], reverse=True)
    }

    os.makedirs("public", exist_ok=True)
    with open("public/screener_data.json", "w", encoding="utf-8") as f:
        json.dump(payload, f, separators=(",", ":"))
    print(f"Saved {len(stocks_output)} NIFTY 500 stocks.")

if __name__ == "__main__":
    main()
