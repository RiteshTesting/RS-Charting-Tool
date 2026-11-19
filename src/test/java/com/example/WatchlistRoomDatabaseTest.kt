package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.NseStockRepository
import com.example.model.NseStockItem
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
class WatchlistRoomDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: NseStockRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun `save stock by watchlist name creates named watchlist and persists stock`() = runTest {
        val reliance = NseStockItem(
            symbol = "RELIANCE",
            companyName = "Reliance Industries Limited",
            sector = "Oil & Gas",
            lastPrice = 2950.0,
            change = 15.0,
            changePercent = 0.51,
            dayHigh = 2965.0,
            dayLow = 2920.0,
            volume = 5_000_000L,
            yearHigh = 3217.0,
            yearLow = 2220.0
        )

        repository.ensureDefaultWatchlist()
        repository.saveStockToWatchlistByName(reliance, "Swing Picks")

        val watchlists = repository.namedWatchlists.first()
        assertEquals(2, watchlists.size)
        val swingList = watchlists.first { it.name == "Swing Picks" }
        assertTrue("RELIANCE" in swingList.symbols)

        // Toggle removes it cleanly
        repository.toggleStockInWatchlist(reliance, swingList.id)
        val updated = repository.namedWatchlists.first().first { it.id == swingList.id }
        assertFalse("RELIANCE" in updated.symbols)
    }
}
