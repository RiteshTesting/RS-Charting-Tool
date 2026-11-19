package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.EodFeedPreferences
import com.example.domain.TechnicalIndicatorCalculator
import com.example.model.Candle
import com.example.model.DataSource
import com.example.model.IndicatorSnapshot
import com.example.model.NseStockItem
import com.example.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class EodScreenerPayload(
    val lastUpdated: String,
    val stocks: List<NseStockItem>,
    val originLabel: String
)

class EodScreenerDataClient(
    private val context: Context? = null
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchScreenerData(
        configuredUrl: String? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ): EodScreenerPayload? = withContext(Dispatchers.IO) {
        // 1. Try remote HTTP(S) URL with cache-busting query `?t=<timestamp>`
        val candidateUrls = buildCandidateUrls(configuredUrl)
        for (baseUrl in candidateUrls) {
            val cacheBustedUrl = appendCacheBustQuery(baseUrl, timestampMillis)
            val body = executeHttpGet(cacheBustedUrl)
            if (body != null) {
                val parsed = parseScreenerJson(body, originLabel = "EOD Remote")
                if (parsed != null && parsed.stocks.isNotEmpty()) {
                    return@withContext parsed
                }
            }
        }

        // 2. Try locally saved / imported screener_data.json in app filesDir
        val appContext = context?.applicationContext
        if (appContext != null) {
            val localFile = File(appContext.filesDir, EodFeedPreferences.LOCAL_EOD_FILENAME)
            if (localFile.exists() && localFile.canRead()) {
                val parsed = runCatching { localFile.readText() }
                    .getOrNull()
                    ?.let { parseScreenerJson(it, originLabel = "EOD Local File") }
                if (parsed != null && parsed.stocks.isNotEmpty()) {
                    return@withContext parsed
                }
            }

            // 3. Try bundled asset `assets/screener_data.json`
            val assetText = runCatching {
                appContext.assets.open(EodFeedPreferences.LOCAL_EOD_FILENAME)
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrNull()
            if (assetText != null) {
                val parsed = parseScreenerJson(assetText, originLabel = "EOD Asset")
                if (parsed != null && parsed.stocks.isNotEmpty()) {
                    return@withContext parsed
                }
            }
        }

        null
    }

    private fun executeHttpGet(url: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string()
                if (body.isNullOrBlank() || body.trimStart().startsWith("<")) return@runCatching null
                body
            }
        }.onFailure {
            Log.d(TAG, "EOD fetch skipped/failed for $url: ${it.message}")
        }.getOrNull()
    }

    companion object {
        private const val TAG = "EodScreenerDataClient"

        fun appendCacheBustQuery(baseUrl: String, timestampMillis: Long = System.currentTimeMillis()): String {
            val trimmed = baseUrl.trim()
            val separator = if (trimmed.contains("?")) "&" else "?"
            return "${trimmed}${separator}t=$timestampMillis"
        }

        fun buildCandidateUrls(configuredUrl: String?): List<String> {
            val raw = configuredUrl?.trim().orEmpty()
            if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
                return listOf(raw)
            }
            val path = when {
                raw.isEmpty() -> "/screener_data.json"
                raw.startsWith("/") -> raw
                else -> "/$raw"
            }
            // Local emulator/dev-server loopback endpoints for relative `/screener_data.json`
            return listOf(
                "http://10.0.2.2:5173$path",
                "http://10.0.2.2:8000$path"
            )
        }

        fun parseScreenerJson(jsonStr: String, originLabel: String = "EOD JSON"): EodScreenerPayload? {
            return runCatching {
                val root = JSONObject(jsonStr)
                val lastUpdated = root.optString("lastUpdated", "")
                    .ifBlank { root.optString("last_updated", "") }
                    .ifBlank { "Daily EOD" }
                val stocksArray = root.optJSONArray("stocks") ?: return null
                val parsedStocks = mutableListOf<NseStockItem>()

                for (i in 0 until stocksArray.length()) {
                    val obj = stocksArray.optJSONObject(i) ?: continue
                    val item = parseStockObject(obj) ?: continue
                    parsedStocks.add(item)
                }

                if (parsedStocks.isEmpty()) return null
                EodScreenerPayload(
                    lastUpdated = lastUpdated,
                    stocks = parsedStocks,
                    originLabel = originLabel
                )
            }.onFailure {
                Log.w(TAG, "Failed to parse screener_data.json: ${it.message}")
            }.getOrNull()
        }

        private fun parseStockObject(obj: JSONObject): NseStockItem? {
            val symbol = obj.optString("symbol", "").trim().uppercase(Locale.US)
            if (symbol.isEmpty()) return null

            val candles = parseCandles(obj.optJSONArray("candles"))
            val price = obj.optDoubleOrNull("price")
                ?: candles.lastOrNull()?.close
                ?: return null
            if (price <= 0.0) return null

            val changePct = obj.optDoubleOrNull("changePct") ?: 0.0
            val denom = 1.0 + (changePct / 100.0)
            val prevClose = if (denom > 0.01) price / denom else price
            val change = price - prevClose

            val volume = obj.optLongOrNull("volume") ?: candles.lastOrNull()?.volume ?: 0L
            val volRatio = obj.optDoubleOrNull("volRatio")
            val high52w = obj.optDoubleOrNull("high52w")
                ?: candles.maxOfOrNull { it.high }
                ?: price
            val low52w = obj.optDoubleOrNull("low52w")
                ?: candles.minOfOrNull { it.low }
                ?: price
            val distFrom52wPct = obj.optDoubleOrNull("distFrom52wPct")
                ?: if (high52w > 0.0) ((price - high52w) / high52w) * 100.0 else null
            val rsRating = obj.optDoubleOrNull("rsRating")

            val companyName = obj.optString("companyName", "")
                .ifBlank { obj.optString("name", symbol) }
                .ifBlank { symbol }
            val sector = obj.optString("sector", "")
                .ifBlank { "NSE EOD" }

            val dayHigh = obj.optDoubleOrNull("dayHigh")
                ?: candles.lastOrNull()?.high
                ?: price
            val dayLow = obj.optDoubleOrNull("dayLow")
                ?: candles.lastOrNull()?.low
                ?: price

            val rsi14 = obj.optDoubleOrNull("rsi14")
            val rsi14Ema45 = obj.optDoubleOrNull("rsi14Ema45")
            val rsi9 = obj.optDoubleOrNull("rsi9")
            val hmEma3 = obj.optDoubleOrNull("hmEma3")
            val hmWma21 = obj.optDoubleOrNull("hmWma21")
            val sma20 = obj.optDoubleOrNull("sma20")
            val sma50 = obj.optDoubleOrNull("sma50")
            val sma200 = obj.optDoubleOrNull("sma200")
            val ema9 = obj.optDoubleOrNull("ema9")
            val ema20 = obj.optDoubleOrNull("ema20")
            val ema45 = obj.optDoubleOrNull("ema45")
            val bbUpper = obj.optDoubleOrNull("bbUpper")
            val bbLower = obj.optDoubleOrNull("bbLower")

            // Build full candle + indicator history and overlay exact EOD JSON values on the latest D1 snapshot
            val computedD1 = if (candles.isNotEmpty()) {
                TechnicalIndicatorCalculator.buildSnapshot(
                    timeframe = Timeframe.D1,
                    candles = candles,
                    livePrice = price,
                    source = DataSource.EOD_JSON
                )
            } else {
                null
            }

            val resolvedRsi14 = rsi14 ?: computedD1?.rsi14
            val resolvedEma20 = ema20 ?: computedD1?.ema20
            val resolvedSma50 = sma50 ?: computedD1?.sma50
            val resolvedSma200 = sma200 ?: computedD1?.sma200

            val dailySnapshot = IndicatorSnapshot(
                timeframe = Timeframe.D1,
                rsi9 = rsi9 ?: computedD1?.rsi9,
                rsi14 = resolvedRsi14,
                rsi21 = computedD1?.rsi21,
                rsi14Ema45 = rsi14Ema45 ?: computedD1?.rsi14Ema45,
                prevRsi14 = computedD1?.prevRsi14,
                hmEma3 = hmEma3 ?: computedD1?.hmEma3,
                hmWma21 = hmWma21 ?: computedD1?.hmWma21,
                sma20 = sma20 ?: computedD1?.sma20,
                sma50 = resolvedSma50,
                sma100 = computedD1?.sma100,
                sma200 = resolvedSma200,
                ema9 = ema9 ?: computedD1?.ema9,
                ema20 = resolvedEma20,
                ema45 = ema45 ?: computedD1?.ema45,
                ema50 = computedD1?.ema50,
                ema100 = computedD1?.ema100,
                ema200 = computedD1?.ema200,
                bbUpper = bbUpper ?: computedD1?.bbUpper,
                bbLower = bbLower ?: computedD1?.bbLower,
                volRatio = volRatio ?: computedD1?.volRatio,
                prevClose = computedD1?.prevClose ?: prevClose,
                prevEma20 = computedD1?.prevEma20,
                prevSma50 = computedD1?.prevSma50,
                prevSma200 = computedD1?.prevSma200,
                avgVolume20 = computedD1?.avgVolume20,
                rsiHistory = computedD1?.rsiHistory ?: emptyList(),
                rsi14Ema45History = computedD1?.rsi14Ema45History ?: emptyList(),
                ema9History = computedD1?.ema9History ?: emptyList(),
                ema20History = computedD1?.ema20History ?: emptyList(),
                ema45History = computedD1?.ema45History ?: emptyList(),
                sma50History = computedD1?.sma50History ?: emptyList(),
                sma200History = computedD1?.sma200History ?: emptyList(),
                bbUpperHistory = computedD1?.bbUpperHistory ?: emptyList(),
                bbLowerHistory = computedD1?.bbLowerHistory ?: emptyList(),
                candles = computedD1?.candles ?: candles,
                signal = TechnicalIndicatorCalculator.evaluateSignal(
                    price = price,
                    rsi14 = resolvedRsi14,
                    ema20 = resolvedEma20,
                    sma50 = resolvedSma50,
                    sma200 = resolvedSma200
                ),
                source = DataSource.EOD_JSON
            )

            val indicators = mutableMapOf<Timeframe, IndicatorSnapshot>(
                Timeframe.D1 to dailySnapshot
            )

            // Also build Weekly and Monthly resampled views directly from EOD daily candles
            if (candles.size >= 15) {
                val weeklyCandles = TechnicalIndicatorCalculator.resampleDailyCandles(candles, Timeframe.W1)
                if (weeklyCandles.size >= 5) {
                    indicators[Timeframe.W1] = TechnicalIndicatorCalculator.buildSnapshot(
                        timeframe = Timeframe.W1,
                        candles = weeklyCandles,
                        livePrice = price,
                        source = DataSource.EOD_JSON
                    )
                }
                val monthlyCandles = TechnicalIndicatorCalculator.resampleDailyCandles(candles, Timeframe.MN1)
                if (monthlyCandles.size >= 3) {
                    indicators[Timeframe.MN1] = TechnicalIndicatorCalculator.buildSnapshot(
                        timeframe = Timeframe.MN1,
                        candles = monthlyCandles,
                        livePrice = price,
                        source = DataSource.EOD_JSON
                    )
                }
            }

            return NseStockItem(
                symbol = symbol,
                companyName = companyName,
                sector = sector,
                lastPrice = price,
                change = change,
                changePercent = changePct,
                dayHigh = dayHigh,
                dayLow = dayLow,
                volume = volume,
                yearHigh = high52w,
                yearLow = low52w,
                volRatio = volRatio ?: dailySnapshot.volRatio,
                distFrom52wPct = distFrom52wPct,
                rsRating = rsRating,
                indicators = indicators
            )
        }

        private fun parseCandles(array: JSONArray?): List<Candle> {
            if (array == null || array.length() == 0) return emptyList()
            val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }
            val isoDateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }

            val list = ArrayList<Candle>(array.length())
            for (i in 0 until array.length()) {
                val c = array.optJSONObject(i) ?: continue
                val ts = parseCandleTimestamp(c, isoDateFormat, isoDateTimeFormat) ?: continue
                val close = c.optDoubleOrNull("close") ?: c.optDoubleOrNull("c") ?: continue
                if (close <= 0.0) continue
                val open = c.optDoubleOrNull("open") ?: c.optDoubleOrNull("o") ?: close
                val high = c.optDoubleOrNull("high") ?: c.optDoubleOrNull("h") ?: close
                val low = c.optDoubleOrNull("low") ?: c.optDoubleOrNull("l") ?: close
                val vol = c.optLongOrNull("volume") ?: c.optLongOrNull("v") ?: 0L
                list.add(
                    Candle(
                        timestampMillis = ts,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = vol
                    )
                )
            }
            return list.sortedBy { it.timestampMillis }
        }

        private fun parseCandleTimestamp(
            obj: JSONObject,
            isoDateFormat: SimpleDateFormat,
            isoDateTimeFormat: SimpleDateFormat
        ): Long? {
            val rawTime = obj.opt("time")
                ?: obj.opt("timestamp")
                ?: obj.opt("date")
                ?: obj.opt("t")
                ?: return null

            return when (rawTime) {
                is Number -> {
                    val v = rawTime.toLong()
                    if (v <= 0L) null else if (v < 10_000_000_000L) v * 1000L else v
                }
                is String -> {
                    val trimmed = rawTime.trim()
                    trimmed.toLongOrNull()?.let { num ->
                        return if (num < 10_000_000_000L) num * 1000L else num
                    }
                    runCatching { isoDateFormat.parse(trimmed.take(10))?.time }.getOrNull()
                        ?: runCatching { isoDateTimeFormat.parse(trimmed.take(19))?.time }.getOrNull()
                }
                is JSONObject -> {
                    val year = rawTime.optInt("year", 0)
                    val month = rawTime.optInt("month", 0)
                    val day = rawTime.optInt("day", 0)
                    if (year > 1900 && month in 1..12 && day in 1..31) {
                        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
                            set(year, month - 1, day, 15, 30, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    } else {
                        null
                    }
                }
                else -> null
            }
        }

        private fun JSONObject.optDoubleOrNull(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            val v = optDouble(key, Double.NaN)
            return if (v.isNaN() || v.isInfinite()) null else v
        }

        private fun JSONObject.optLongOrNull(key: String): Long? {
            if (!has(key) || isNull(key)) return null
            return runCatching { getDouble(key).toLong() }.getOrNull()
        }
    }
}
