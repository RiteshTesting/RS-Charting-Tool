import json
import os
from datetime import datetime, timezone, timedelta
import numpy as np
import pandas as pd
import yfinance as yf

# अपनी वॉचलिस्ट / NIFTY 500 स्टॉक्स यहाँ जोड़ें (.NS के साथ)
SYMBOLS = [
    "RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "ICICIBANK.NS", "INFY.NS",
    "BHARTIARTL.NS", "SBIN.NS", "LTIM.NS", "LT.NS", "AXISBANK.NS",
    "TATAMOTORS.NS", "SUNPHARMA.NS", "MARUTI.NS", "NTPC.NS", "BEL.NS",
    "HAL.NS", "TRENT.NS", "TVSMOTOR.NS", "BAJFINANCE.NS", "TITAN.NS",
    "M&M.NS", "ADANIPORTS.NS", "POWERGRID.NS", "COALINDIA.NS", "TATASTEEL.NS",
    "HINDALCO.NS", "JSWSTEEL.NS", "ONGC.NS", "VBL.NS", "ZOMATO.NS",
    "DIXON.NS", "POLYCAB.NS", "PERSISTENT.NS", "COFORGE.NS", "KPITTECH.NS",
    "SUZLON.NS", "IRFC.NS", "RVNL.NS", "MAZDOCK.NS", "CDSL.NS"
]

BENCHMARK = "^NSEI"  # NIFTY 50

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
    print("Downloading NIFTY 50 benchmark...")
    nifty = yf.download(BENCHMARK, period="1y", interval="1d", progress=False)
    if isinstance(nifty.columns, pd.MultiIndex):
        nifty.columns = nifty.columns.get_level_values(0)
    nifty_ret_3m = (nifty["Close"].iloc[-1] / nifty["Close"].iloc[-63] - 1) if len(nifty) > 63 else 0

    stocks_output = []
    raw_rs_scores = []

    for sym in SYMBOLS:
        try:
            df = yf.download(sym, period="1y", interval="1d", progress=False)
            if isinstance(df.columns, pd.MultiIndex):
                df.columns = df.columns.get_level_values(0)
            if df.empty or len(df) < 65:
                continue

            close = df["Close"]
            high = df["High"]
            low = df["Low"]
            vol = df["Volume"]

            # Moving Averages
            sma20 = close.rolling(20).mean()
            sma50 = close.rolling(50).mean()
            sma200 = close.rolling(200).mean()
            ema9 = close.ewm(span=9, adjust=False).mean()
            ema20 = close.ewm(span=20, adjust=False).mean()
            ema45 = close.ewm(span=45, adjust=False).mean()

            # Bollinger Bands (20, 2)
            std20 = close.rolling(20).std()
            bb_upper = sma20 + 2 * std20
            bb_lower = sma20 - 2 * std20

            # RSI(14) + 45 EMA Smoothing (आपकी मुख्य स्ट्रेटेजी)
            rsi14 = calc_rsi(close, 14)
            rsi14_ema45 = rsi14.ewm(span=45, adjust=False).mean()

            # Hilega Milega: RSI(9), 3 EMA, 21 WMA
            rsi9 = calc_rsi(close, 9)
            hm_ema3 = rsi9.ewm(span=3, adjust=False).mean()
            hm_wma21 = calc_wma(rsi9, 21)

            # Relative Strength vs NIFTY 50 (3M & 6M weighted)
            ret_3m = (close.iloc[-1] / close.iloc[-63] - 1)
            ret_6m = (close.iloc[-1] / close.iloc[-126] - 1) if len(close) > 126 else ret_3m
            rs_raw = (0.6 * (ret_3m - nifty_ret_3m)) + (0.4 * ret_6m)
            raw_rs_scores.append(rs_raw)

            # Volume & 52W High
            vol_avg20 = vol.rolling(20).mean().iloc[-1]
            vol_ratio = round(float(vol.iloc[-1] / vol_avg20), 2) if vol_avg20 > 0 else 1.0
            high_52w = float(high.max())
            curr_close = float(close.iloc[-1])
            prev_close = float(close.iloc[-2])
            change_pct = round(((curr_close - prev_close) / prev_close) * 100, 2)

            # चार्ट के लिए आखिरी 180 कैंडल्स
            candles = []
            tail_df = df.tail(180)
            for idx, row in tail_df.iterrows():
                candles.append({
                    "time": idx.strftime("%Y-%m-%d"),
                    "open": round(float(row["Open"]), 2),
                    "high": round(float(row["High"]), 2),
                    "low": round(float(row["Low"]), 2),
                    "close": round(float(row["Close"]), 2),
                    "volume": int(row["Volume"])
                })

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
        except Exception as e:
            print(f"Error {sym}: {e}")

    # RS Rating को 1 से 99 के स्केल पर रैंक करें
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
        json.dump(payload, f)
    print(f"Saved {len(stocks_output)} stocks to public/screener_data.json")

if __name__ == "__main__":
    main()
