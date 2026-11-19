package com.example.data.remote

import android.util.Log
import com.example.domain.TechnicalIndicatorCalculator
import com.example.model.Candle
import com.example.model.DataSource
import com.example.model.NseIndexUniverse
import com.example.model.NseSearchSuggestion
import com.example.model.NseStockItem
import com.example.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class CandleSeriesResult(
    val candles: List<Candle>,
    val source: DataSource,
    val quotePatch: QuotePatch? = null
)

data class QuotePatch(
    val symbol: String,
    val companyName: String,
    val lastPrice: Double,
    val change: Double,
    val changePercent: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val volume: Long,
    val yearHigh: Double,
    val yearLow: Double
)

class NseDataClient {
    private val cookieJar = NseCookieJar()
    private val sessionMutex = Mutex()
    @Volatile
    private var lastSessionWarmupMs: Long = 0L

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val browserUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private suspend fun ensureNseSession(forceRefresh: Boolean = false) {
        sessionMutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh &&
                cookieJar.hasCookiesFor("www.nseindia.com") &&
                (now - lastSessionWarmupMs) < 180_000L
            ) {
                return
            }
            if (forceRefresh) {
                cookieJar.clear()
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    val homeReq = Request.Builder()
                        .url("https://www.nseindia.com/")
                        .header("User-Agent", browserUserAgent)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()
                    client.newCall(homeReq).execute().use { it.body?.close() }

                    val marketReq = Request.Builder()
                        .url("https://www.nseindia.com/market-data/live-equity-market")
                        .header("User-Agent", browserUserAgent)
                        .header("Referer", "https://www.nseindia.com/")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()
                    client.newCall(marketReq).execute().use { it.body?.close() }
                    lastSessionWarmupMs = System.currentTimeMillis()
                }.onFailure {
                    Log.w(TAG, "NSE session warmup warning: ${it.message}")
                }
            }
        }
    }

    private suspend fun getNseJson(url: String): String? {
        ensureNseSession(forceRefresh = false)
        val firstAttempt = executeNseGet(url)
        if (firstAttempt != null) return firstAttempt

        // Retry once with a fresh NSE cookie handshake if blocked (401/403)
        ensureNseSession(forceRefresh = true)
        return executeNseGet(url)
    }

    private suspend fun executeNseGet(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.nseindia.com/market-data/live-equity-market")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "NSE API HTTP ${response.code} for $url")
                    return@runCatching null
                }
                val body = response.body?.string()
                if (body.isNullOrBlank() || body.trimStart().startsWith("<")) {
                    return@runCatching null
                }
                body
            }
        }.getOrNull()
    }

    suspend fun fetchIndexQuotes(universe: NseIndexUniverse): Pair<List<NseStockItem>, DataSource> {
        val encoded = URLEncoder.encode(universe.nseQueryParam, "UTF-8").replace("+", "%20")
        val url = "https://www.nseindia.com/api/equity-stockIndices?index=$encoded"
        val jsonStr = getNseJson(url)
        if (jsonStr != null) {
            val parsed = parseNseIndexQuotes(jsonStr)
            if (parsed.isNotEmpty()) {
                return parsed to DataSource.NSE_DIRECT
            }
        }
        // Fallback: return seed NSE constituent symbols with live .NS quote fetches in repository
        return emptyList<NseStockItem>() to DataSource.NSE_FEED
    }

    private fun parseNseIndexQuotes(jsonStr: String): List<NseStockItem> {
        return runCatching {
            val root = JSONObject(jsonStr)
            val dataArray = root.optJSONArray("data") ?: return emptyList()
            val results = mutableListOf<NseStockItem>()
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.optJSONObject(i) ?: continue
                val symbol = obj.optString("symbol", "").trim()
                if (symbol.isEmpty() || symbol.contains(" ") || obj.optInt("priority", 0) == 1) {
                    continue
                }
                val meta = obj.optJSONObject("meta")
                val companyName = meta?.optString("companyName", symbol)?.ifBlank { symbol } ?: symbol
                val sector = meta?.optString("industry", "NSE Equity")?.ifBlank { "NSE Equity" } ?: "NSE Equity"
                val ltp = obj.optDouble("lastPrice", 0.0)
                if (ltp <= 0.0) continue

                results.add(
                    NseStockItem(
                        symbol = symbol,
                        companyName = companyName,
                        sector = sector,
                        lastPrice = ltp,
                        change = obj.optDouble("change", 0.0),
                        changePercent = obj.optDouble("pChange", 0.0),
                        dayHigh = obj.optDouble("dayHigh", ltp),
                        dayLow = obj.optDouble("dayLow", ltp),
                        volume = obj.optLong("totalTradedVolume", 0L),
                        yearHigh = obj.optDouble("yearHigh", ltp),
                        yearLow = obj.optDouble("yearLow", ltp)
                    )
                )
            }
            results
        }.getOrElse { emptyList() }
    }

    suspend fun searchNseSymbols(query: String): List<NseSearchSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val nseUrl = "https://www.nseindia.com/api/search/autocomplete?q=$encoded"
        val nseBody = getNseJson(nseUrl)
        if (nseBody != null) {
            val suggestions = parseNseAutocomplete(nseBody)
            if (suggestions.isNotEmpty()) return suggestions
        }

        // Fallback to NSE (.NS) search endpoint filtered strictly to NSI (NSE India) exchange
        return searchYahooNseSymbols(trimmed)
    }

    private fun parseNseAutocomplete(jsonStr: String): List<NseSearchSuggestion> {
        return runCatching {
            val root = JSONObject(jsonStr)
            val symbols = root.optJSONArray("symbols") ?: return emptyList()
            val list = mutableListOf<NseSearchSuggestion>()
            for (i in 0 until symbols.length()) {
                val item = symbols.optJSONObject(i) ?: continue
                val subType = item.optString("result_sub_type", "")
                if (subType.isNotEmpty() && subType != "equity") continue
                val symbol = item.optString("symbol", "").trim()
                if (symbol.isEmpty()) continue
                val name = item.optString("symbol_info", symbol).ifBlank { symbol }
                list.add(NseSearchSuggestion(symbol = symbol, companyName = name, resultType = "NSE Equity"))
            }
            list.distinctBy { it.symbol }.take(12)
        }.getOrElse { emptyList() }
    }

    private suspend fun searchYahooNseSymbols(query: String): List<NseSearchSuggestion> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://query2.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=20&newsCount=0"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", browserUserAgent)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching emptyList()
                    val body = resp.body?.string() ?: return@runCatching emptyList()
                    val root = JSONObject(body)
                    val quotes = root.optJSONArray("quotes") ?: return@runCatching emptyList()
                    val list = mutableListOf<NseSearchSuggestion>()
                    for (i in 0 until quotes.length()) {
                        val q = quotes.optJSONObject(i) ?: continue
                        val exchange = q.optString("exchange", "")
                        val rawSymbol = q.optString("symbol", "")
                        if (exchange == "NSI" || rawSymbol.endsWith(".NS")) {
                            val nseSymbol = rawSymbol.removeSuffix(".NS")
                            val longName = q.optString("longname", "")
                                .ifBlank { q.optString("shortname", nseSymbol) }
                            list.add(
                                NseSearchSuggestion(
                                    symbol = nseSymbol,
                                    companyName = longName,
                                    resultType = "NSE Equity"
                                )
                            )
                        }
                    }
                    list.distinctBy { it.symbol }.take(12)
                }
            }.getOrElse { emptyList() }
        }

    suspend fun fetchSingleNseQuote(symbol: String): NseStockItem? {
        val clean = symbol.trim().uppercase(Locale.US)
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val nseUrl = "https://www.nseindia.com/api/quote-equity?symbol=$encoded"
        val body = getNseJson(nseUrl)
        if (body != null) {
            val parsed = parseNseSingleQuote(clean, body)
            if (parsed != null) return parsed
        }

        // Fallback via .NS chart metadata
        val chartRes = fetchYahooNseCandles(clean, Timeframe.D1)
        val patch = chartRes?.quotePatch ?: return null
        return NseStockItem(
            symbol = patch.symbol,
            companyName = patch.companyName,
            sector = "NSE Equity",
            lastPrice = patch.lastPrice,
            change = patch.change,
            changePercent = patch.changePercent,
            dayHigh = patch.dayHigh,
            dayLow = patch.dayLow,
            volume = patch.volume,
            yearHigh = patch.yearHigh,
            yearLow = patch.yearLow
        )
    }

    private fun parseNseSingleQuote(symbol: String, jsonStr: String): NseStockItem? {
        return runCatching {
            val root = JSONObject(jsonStr)
            val info = root.optJSONObject("info")
            val priceInfo = root.optJSONObject("priceInfo") ?: return null
            val industryInfo = root.optJSONObject("industryInfo")
            val intraDay = priceInfo.optJSONObject("intraDayHighLow")
            val weekHighLow = priceInfo.optJSONObject("weekHighLow")

            val ltp = priceInfo.optDouble("lastPrice", 0.0)
            if (ltp <= 0.0) return null

            NseStockItem(
                symbol = info?.optString("symbol", symbol)?.ifBlank { symbol } ?: symbol,
                companyName = info?.optString("companyName", symbol)?.ifBlank { symbol } ?: symbol,
                sector = industryInfo?.optString("sector", "NSE Equity")?.ifBlank { "NSE Equity" } ?: "NSE Equity",
                lastPrice = ltp,
                change = priceInfo.optDouble("change", 0.0),
                changePercent = priceInfo.optDouble("pChange", 0.0),
                dayHigh = intraDay?.optDouble("max", ltp) ?: ltp,
                dayLow = intraDay?.optDouble("min", ltp) ?: ltp,
                volume = 0L,
                yearHigh = weekHighLow?.optDouble("max", ltp) ?: ltp,
                yearLow = weekHighLow?.optDouble("min", ltp) ?: ltp
            )
        }.getOrNull()
    }

    suspend fun fetchCandlesForTimeframe(
        symbol: String,
        timeframe: Timeframe
    ): CandleSeriesResult? {
        val clean = symbol.trim().uppercase(Locale.US)

        // 1. Try NSE India Official API first for Daily/Weekly/Monthly or Intraday ticks
        if (timeframe == Timeframe.D1 || timeframe == Timeframe.W1 || timeframe == Timeframe.MN1) {
            val nseDaily = fetchNseHistoricalDaily(clean, timeframe.nseLookbackDays)
            if (nseDaily.size >= 40) {
                val resampled = TechnicalIndicatorCalculator.resampleDailyCandles(nseDaily, timeframe)
                if (resampled.size >= 20) {
                    return CandleSeriesResult(candles = resampled, source = DataSource.NSE_DIRECT)
                }
            }
        } else {
            val ticks = fetchNseIntradayTicks(clean)
            val mins = if (timeframe == Timeframe.M15) 15 else 60
            val intraday = TechnicalIndicatorCalculator.resampleIntradayTicks(ticks, mins)
            if (intraday.size >= 30) {
                return CandleSeriesResult(candles = intraday, source = DataSource.NSE_DIRECT)
            }
        }

        // 2. Fallback to NSE (.NS) exchange feed (provides multi-day 15m/1H & multi-year 1D/1W/1M)
        return fetchYahooNseCandles(clean, timeframe)
    }

    private suspend fun fetchNseHistoricalDaily(symbol: String, lookbackDays: Int): List<Candle> {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val toDate = Date()
        val fromDate = Date(toDate.time - lookbackDays.toLong() * 86_400_000L)
        val encodedSymbol = URLEncoder.encode(symbol, "UTF-8")
        val url = "https://www.nseindia.com/api/historical/cm/equity" +
            "?symbol=$encodedSymbol&series=%5B%22EQ%22%5D" +
            "&from=${sdf.format(fromDate)}&to=${sdf.format(toDate)}"

        val body = getNseJson(url) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: return emptyList()
            val isoParser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }
            val list = mutableListOf<Candle>()
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val dateStr = obj.optString("CH_TIMESTAMP", "")
                val ts = runCatching { isoParser.parse(dateStr)?.time }.getOrNull() ?: continue
                val close = obj.optDouble("CH_CLOSING_PRICE", 0.0)
                if (close <= 0.0) continue
                list.add(
                    Candle(
                        timestampMillis = ts,
                        open = obj.optDouble("CH_OPENING_PRICE", close),
                        high = obj.optDouble("CH_TRADE_HIGH_PRICE", close),
                        low = obj.optDouble("CH_TRADE_LOW_PRICE", close),
                        close = close,
                        volume = obj.optLong("CH_TOT_TRADED_QTY", 0L)
                    )
                )
            }
            list.sortedBy { it.timestampMillis }
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchNseIntradayTicks(symbol: String): List<Pair<Long, Double>> {
        val encoded = URLEncoder.encode("${symbol}EQN", "UTF-8")
        val url = "https://www.nseindia.com/api/chart-databyindex?index=$encoded"
        val body = getNseJson(url) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val graphData = root.optJSONArray("grapthData") ?: return emptyList()
            val ticks = mutableListOf<Pair<Long, Double>>()
            for (i in 0 until graphData.length()) {
                val pair = graphData.optJSONArray(i) ?: continue
                if (pair.length() < 2) continue
                val ts = pair.optLong(0, 0L)
                val price = pair.optDouble(1, 0.0)
                if (ts > 0L && price > 0.0) {
                    ticks.add(ts to price)
                }
            }
            ticks
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchYahooNseCandles(
        symbol: String,
        timeframe: Timeframe
    ): CandleSeriesResult? = withContext(Dispatchers.IO) {
        runCatching {
            val encodedTicker = URLEncoder.encode("$symbol.NS", "UTF-8")
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encodedTicker" +
                "?interval=${timeframe.yahooInterval}&range=${timeframe.yahooRange}&includePrePost=false"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .header("Accept", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                parseYahooChartResponse(symbol, body)
            }
        }.getOrNull()
    }

    private fun parseYahooChartResponse(symbol: String, jsonStr: String): CandleSeriesResult? {
        return runCatching {
            val root = JSONObject(jsonStr)
            val chart = root.optJSONObject("chart") ?: return null
            val resultArr = chart.optJSONArray("result") ?: return null
            if (resultArr.length() == 0) return null
            val resultObj = resultArr.getJSONObject(0)

            val meta = resultObj.optJSONObject("meta")
            val timestamps = resultObj.optJSONArray("timestamp") ?: return null
            val indicators = resultObj.optJSONObject("indicators") ?: return null
            val quoteArr = indicators.optJSONArray("quote") ?: return null
            if (quoteArr.length() == 0) return null
            val quote = quoteArr.getJSONObject(0)

            val opens = quote.optJSONArray("open")
            val highs = quote.optJSONArray("high")
            val lows = quote.optJSONArray("low")
            val closes = quote.optJSONArray("close") ?: return null
            val volumes = quote.optJSONArray("volume")

            val candles = mutableListOf<Candle>()
            for (i in 0 until timestamps.length()) {
                val tsSec = timestamps.optLong(i, 0L)
                if (tsSec <= 0L) continue
                val closeVal = closes.optDoubleOrNull(i) ?: continue
                if (closeVal <= 0.0) continue

                val openVal = opens?.optDoubleOrNull(i) ?: closeVal
                val highVal = highs?.optDoubleOrNull(i) ?: closeVal
                val lowVal = lows?.optDoubleOrNull(i) ?: closeVal
                val volVal = volumes?.optLongOrZero(i) ?: 0L

                candles.add(
                    Candle(
                        timestampMillis = tsSec * 1000L,
                        open = openVal,
                        high = highVal,
                        low = lowVal,
                        close = closeVal,
                        volume = volVal
                    )
                )
            }

            if (candles.isEmpty()) return null

            val lastPrice = meta?.optDouble("regularMarketPrice", candles.last().close)
                ?.takeIf { !it.isNaN() && it > 0.0 } ?: candles.last().close
            val prevClose = meta?.optDouble("chartPreviousClose", 0.0)
                ?.takeIf { !it.isNaN() && it > 0.0 }
                ?: meta?.optDouble("previousClose", 0.0)?.takeIf { !it.isNaN() && it > 0.0 }
                ?: if (candles.size >= 2) candles[candles.size - 2].close else candles.last().open
            val change = lastPrice - prevClose
            val changePct = if (prevClose > 0.0) (change / prevClose) * 100.0 else 0.0

            val quotePatch = QuotePatch(
                symbol = symbol,
                companyName = meta?.optString("longName", "")
                    ?.ifBlank { meta.optString("shortName", symbol) }
                    ?.ifBlank { symbol } ?: symbol,
                lastPrice = lastPrice,
                change = change,
                changePercent = changePct,
                dayHigh = meta?.optDouble("regularMarketDayHigh", candles.last().high)
                    ?.takeIf { !it.isNaN() } ?: candles.last().high,
                dayLow = meta?.optDouble("regularMarketDayLow", candles.last().low)
                    ?.takeIf { !it.isNaN() } ?: candles.last().low,
                volume = meta?.optLong("regularMarketVolume", candles.last().volume)
                    ?: candles.last().volume,
                yearHigh = meta?.optDouble("fiftyTwoWeekHigh", candles.maxOf { it.high })
                    ?.takeIf { !it.isNaN() } ?: candles.maxOf { it.high },
                yearLow = meta?.optDouble("fiftyTwoWeekLow", candles.minOf { it.low })
                    ?.takeIf { !it.isNaN() } ?: candles.minOf { it.low }
            )

            CandleSeriesResult(
                candles = candles,
                source = DataSource.NSE_FEED,
                quotePatch = quotePatch
            )
        }.getOrNull()
    }

    private fun JSONArray.optDoubleOrNull(index: Int): Double? {
        if (index < 0 || index >= length() || isNull(index)) return null
        val v = optDouble(index, Double.NaN)
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    private fun JSONArray.optLongOrZero(index: Int): Long {
        if (index < 0 || index >= length() || isNull(index)) return 0L
        return optLong(index, 0L)
    }

    companion object {
        private const val TAG = "NseDataClient"
    }
}
