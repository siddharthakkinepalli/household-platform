package com.household.app.ui.compose.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

class ThemePreferencesManager(private val context: Context) {

    companion object {
        val THEME_KEY = stringPreferencesKey("selected_theme")
    }

    val themeFlow: Flow<JugaadThemeSelection> = context.themeDataStore.data.map { prefs ->
        val name = prefs[THEME_KEY] ?: JugaadThemeSelection.LUMINESCENT_GLASS.name
        runCatching { JugaadThemeSelection.valueOf(name) }
            .getOrDefault(JugaadThemeSelection.LUMINESCENT_GLASS)
    }

    suspend fun saveTheme(theme: JugaadThemeSelection) {
        context.themeDataStore.edit { prefs -> prefs[THEME_KEY] = theme.name }
    }
}
