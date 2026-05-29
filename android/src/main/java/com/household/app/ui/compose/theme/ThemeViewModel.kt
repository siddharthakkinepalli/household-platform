package com.household.app.ui.compose.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(private val manager: ThemePreferencesManager) : ViewModel() {

    val currentTheme: StateFlow<JugaadThemeSelection> = manager.themeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JugaadThemeSelection.LUMINESCENT_GLASS
    )

    fun setTheme(theme: JugaadThemeSelection) {
        viewModelScope.launch { manager.saveTheme(theme) }
    }
}
