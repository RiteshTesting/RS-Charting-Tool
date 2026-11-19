package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.remote.NseDataClient
import com.example.model.AlertCondition
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class PriceAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).stockDao()
        val activeAlerts = dao.getActivePriceAlerts()
        if (activeAlerts.isEmpty()) {
            return Result.success()
        }

        val nseClient = NseDataClient()
        val groupedBySymbol = activeAlerts.groupBy { it.symbol }
        val semaphore = Semaphore(4)
        var anyFetchSucceeded = false

        coroutineScope {
            groupedBySymbol.entries.map { (symbol, alertsForSymbol) ->
                async {
                    semaphore.withPermit {
                        val quote = nseClient.fetchSingleNseQuote(symbol)
                        if (quote == null || quote.lastPrice <= 0.0) {
                            Log.w(TAG, "Unable to fetch live NSE quote for $symbol")
                            return@withPermit
                        }
                        anyFetchSucceeded = true
                        val livePrice = quote.lastPrice
                        val now = System.currentTimeMillis()

                        for (alert in alertsForSymbol) {
                            val cond = runCatching { AlertCondition.valueOf(alert.condition) }
                                .getOrDefault(AlertCondition.ABOVE)
                            if (evaluateThreshold(livePrice, alert.targetPrice, cond)) {
                                dao.markPriceAlertTriggered(
                                    alertId = alert.id,
                                    triggeredPrice = livePrice,
                                    triggeredAt = now
                                )
                                PriceAlertNotificationHelper.showPriceAlertNotification(
                                    context = applicationContext,
                                    alertId = alert.id,
                                    symbol = alert.symbol,
                                    companyName = alert.companyName.ifBlank { quote.companyName },
                                    livePrice = livePrice,
                                    targetPrice = alert.targetPrice,
                                    condition = cond
                                )
                            } else {
                                dao.updatePriceAlertLastChecked(
                                    alertId = alert.id,
                                    lastPrice = livePrice
                                )
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        return if (anyFetchSucceeded) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "PriceAlertWorker"

        fun evaluateThreshold(
            livePrice: Double,
            targetPrice: Double,
            condition: AlertCondition
        ): Boolean {
            return when (condition) {
                AlertCondition.ABOVE -> livePrice >= targetPrice
                AlertCondition.BELOW -> livePrice <= targetPrice
            }
        }
    }
}
