package com.household.app.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentManager
import androidx.navigation.compose.rememberNavController
import com.household.app.ui.compose.components.NavigationRailComposable
import com.household.app.ui.compose.components.edgeSwipeRail
import com.household.app.ui.compose.navigation.Screen
import com.household.app.ui.compose.theme.JugaadTheme
import com.household.app.ui.v2.V2AppShell

/**
 * AppShell — root Composable set as the content of MainActivity.
 *
 * Responsibilities:
 * 1. Owns navController and rail expand state.
 * 2. Renders NavigationRailComposable (left) + AppNavHost (right) side by side.
 * 3. Applies edge-swipe gesture to the outer Row.
 * 4. Manages BackHandler priority:
 *    a. If FAB is expanded → collapse FAB (handled inside QuickCaptureFab itself)
 *    b. If nav back stack has entries → pop
 *    c. Otherwise → finish activity (handled in MainActivity via LocalActivity)
 *
 * Key correctness notes:
 * - currentRoute uses derivedStateOf so recomposition only triggers when the route
 *   STRING changes, not on every back-stack entry mutation (saves, metadata, etc.)
 * - Rail toggle (expanded) is rememberSaveable — persists across recompositions.
 * - Nav items never toggle expanded — toggle is only on the dedicated IconButton.
 */
@Composable
fun AppShell(
    fragmentManager: FragmentManager,
    onFinish: () -> Unit
) {
    V2AppShell(
        fragmentManager = fragmentManager,
        onFinish = onFinish
    )
}

@Composable
fun AppShellLegacy(
    fragmentManager: FragmentManager,
    onFinish: () -> Unit
) {
    JugaadTheme {
        val navController = rememberNavController()

        // derivedStateOf: only recomposes consumers when route value changes
        val currentRoute by remember {
            derivedStateOf { navController.currentBackStackEntry?.destination?.route }
        }

        var expanded by rememberSaveable { mutableStateOf(false) }

        // BackHandler: pop back stack, fall through to finish
        BackHandler {
            if (!navController.popBackStack()) onFinish()
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .edgeSwipeRail(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false }
                )
        ) {
            NavigationRailComposable(
                currentRoute = currentRoute,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.fillMaxHeight()
            )

            AppNavHost(
                navController = navController,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}
