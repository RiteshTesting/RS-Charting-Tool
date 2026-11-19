package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode(val label: String) {
    SYSTEM("Auto"),
    DAY("Day"),
    NIGHT("Night")
}

private val Context.themeDataStore by preferencesDataStore(name = "nsepulse_theme_prefs")

class ThemePreferences(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("app_theme_mode")

    val themeMode: Flow<AppThemeMode> = context.themeDataStore.data.map { prefs ->
        val raw = prefs[themeModeKey]
        runCatching { AppThemeMode.valueOf(raw ?: AppThemeMode.SYSTEM.name) }
            .getOrDefault(AppThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }
}
