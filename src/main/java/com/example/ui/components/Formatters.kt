package com.example.ui.components

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object IndianFormatters {
    private val inrLocale = Locale("en", "IN")

    fun formatInr(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(inrLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "₹${formatter.format(value)}"
    }

    fun formatSignedPercent(pct: Double): String {
        val sign = if (pct > 0) "+" else ""
        return String.format(Locale.US, "%s%.2f%%", sign, pct)
    }

    fun formatSignedPrice(change: Double): String {
        val sign = if (change > 0) "+" else ""
        return String.format(Locale.US, "%s%.2f", sign, change)
    }

    fun formatRsi(rsi: Double?): String {
        return if (rsi == null) "--" else String.format(Locale.US, "%.1f", rsi)
    }

    fun formatVolume(vol: Long): String {
        return when {
            vol >= 10_000_000L -> String.format(Locale.US, "%.2f Cr", vol / 10_000_000.0)
            vol >= 100_000L -> String.format(Locale.US, "%.2f L", vol / 100_000.0)
            vol >= 1_000L -> String.format(Locale.US, "%.1f K", vol / 1_000.0)
            else -> vol.toString()
        }
    }

    fun formatCandleTime(timestampMillis: Long, isIntraday: Boolean): String {
        val pattern = if (isIntraday) "dd MMM HH:mm" else "dd MMM yyyy"
        val sdf = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return sdf.format(Date(timestampMillis))
    }

    fun formatLastUpdated(timestampMillis: Long?): String {
        if (timestampMillis == null) return "Syncing…"
        val sdf = SimpleDateFormat("HH:mm:ss 'IST'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return sdf.format(Date(timestampMillis))
    }
}
