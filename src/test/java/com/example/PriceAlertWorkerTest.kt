package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.local.AppDatabase
import com.example.data.repository.NseStockRepository
import com.example.model.AlertCondition
import com.example.notifications.PriceAlertScheduler
import com.example.notifications.PriceAlertWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PriceAlertWorkerTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: NseStockRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NseStockRepository(database.stockDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `evaluateThreshold detects ABOVE and BELOW hits accurately`() {
        assertTrue(PriceAlertWorker.evaluateThreshold(3005.0, 3000.0, AlertCondition.ABOVE))
        assertTrue(PriceAlertWorker.evaluateThreshold(3000.0, 3000.0, AlertCondition.ABOVE))
        assertFalse(PriceAlertWorker.evaluateThreshold(2999.5, 3000.0, AlertCondition.ABOVE))

        assertTrue(PriceAlertWorker.evaluateThreshold(2850.0, 2900.0, AlertCondition.BELOW))
        assertTrue(PriceAlertWorker.evaluateThreshold(2900.0, 2900.0, AlertCondition.BELOW))
        assertFalse(PriceAlertWorker.evaluateThreshold(2905.0, 2900.0, AlertCondition.BELOW))
    }

    @Test
    fun `creating and toggling alert persists in Room and schedules WorkManager`() = runTest {
        val alertId = repository.createPriceAlert(
            symbol = "RELIANCE",
            companyName = "Reliance Industries Limited",
            targetPrice = 3050.0,
            condition = AlertCondition.ABOVE,
            lastKnownPrice = 2950.0
        )

        val alerts = repository.priceAlerts.first()
        assertEquals(1, alerts.size)
        assertTrue(alerts.first().isActive)
        assertEquals("RELIANCE", alerts.first().symbol)

        PriceAlertScheduler.syncBackgroundMonitoring(context, hasActiveAlerts = true)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("nse_price_alert_monitor_periodic")
            .get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)

        repository.setPriceAlertActive(alertId, false)
        assertFalse(repository.priceAlerts.first().first().isActive)
    }
}
