package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AlertCondition
import com.example.ui.components.IndianFormatters

object PriceAlertNotificationHelper {

    const val CHANNEL_ID = "nse_price_alerts"
    const val EXTRA_OPEN_SYMBOL = "open_symbol"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NSE Price Threshold Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when watched NSE stocks cross your target price thresholds"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showPriceAlertNotification(
        context: Context,
        alertId: Long,
        symbol: String,
        companyName: String,
        livePrice: Double,
        targetPrice: Double,
        condition: AlertCondition
    ) {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SYMBOL, symbol)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alertId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val verb = if (condition == AlertCondition.ABOVE) "crossed above" else "dropped below"
        val title = "$symbol Price Alert: ${IndianFormatters.formatInr(livePrice)}"
        val body = "$companyName $verb your target ${IndianFormatters.formatInr(targetPrice)} on NSE."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pulse)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(alertId.toInt() + 10_000, notification)
        }
    }
}
