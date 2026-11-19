package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.eodFeedDataStore by preferencesDataStore(name = "nsepulse_eod_feed_prefs")

class EodFeedPreferences(private val context: Context) {

    private val eodUrlKey = stringPreferencesKey("eod_screener_json_url")

    val eodUrl: Flow<String> = context.eodFeedDataStore.data.map { prefs ->
        prefs[eodUrlKey] ?: DEFAULT_EOD_PATH
    }

    suspend fun setEodUrl(url: String) {
        val cleaned = url.trim().ifEmpty { DEFAULT_EOD_PATH }
        context.eodFeedDataStore.edit { prefs ->
            prefs[eodUrlKey] = cleaned
        }
    }

    fun getLocalEodFile(): File = File(context.filesDir, LOCAL_EOD_FILENAME)

    fun saveLocalEodJson(jsonContent: String) {
        getLocalEodFile().writeText(jsonContent)
    }

    fun clearLocalEodJson() {
        val file = getLocalEodFile()
        if (file.exists()) {
            file.delete()
        }
    }

    companion object {
        const val DEFAULT_EOD_PATH = "/screener_data.json"
        const val LOCAL_EOD_FILENAME = "screener_data.json"
    }
}
