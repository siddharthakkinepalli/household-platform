package com.household.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.household.app.ui.compose.AppShell

/**
 * MainActivity — entry point.
 *
 * Phase 2: delegates entirely to AppShell (Compose).
 * AppShell owns the NavController, NavigationRail, and all screen routing.
 *
 * The old XML layout (activity_main.xml) and the Fragment back-stack managed by
 * NavHostFragment are no longer used. Existing Fragment screens are hosted inside
 * Compose via LegacyFragmentHost (AndroidView / FragmentContainerView).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            AppShell(
                onFinish        = { finish() }
            )
        }
    }
}


