package com.household.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.household.app.ui.compose.AppShell
import com.household.app.ui.compose.theme.JugaadTheme
import com.household.app.ui.compose.theme.ThemePreferencesManager
import com.household.app.ui.compose.theme.ThemeViewModel
import com.household.app.ui.compose.theme.ThemeViewModelFactory
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity — entry point.
 * Owns the theme lifecycle: instantiates ThemeViewModel, collects the persisted
 * JugaadThemeSelection from DataStore, and wraps the entire app in JugaadTheme.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val themeViewModel: ThemeViewModel by viewModels {
        ThemeViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            val selectedTheme by themeViewModel.currentTheme.collectAsState()

            JugaadTheme(themeSelection = selectedTheme) {
                AppShell(
                    fragmentManager = supportFragmentManager,
                    onFinish        = { finish() }
                )
            }
        }
    }
}
