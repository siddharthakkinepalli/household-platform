package com.household.app.ui.compose

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.household.app.ui.compose.navigation.Screen
import com.household.app.ui.compose.motion.Motion

/**
 * AppNavHost — sets up Compose Navigation with global transition defaults.
 *
 * Transition strategy:
 *   - Enter: fade + slide in from the end (forward navigation)
 *   - Exit:  fade + slide out to start
 *   - Pop enter/exit: reverse direction (back navigation)
 *
 * slideIntoContainer is preferred over slideInHorizontally — uses container bounds,
 * not screen width, so travel distance is proportionally correct.
 *
 * Home screen is a Compose composable.
 * All other destinations use LegacyFragmentHost to preserve existing Fragment screens
 * until they are individually migrated to Compose.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    fragmentManager: FragmentManager,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController   = navController,
        startDestination = Screen.Home.route,
        modifier        = modifier,
        enterTransition = {
            fadeIn(tween(Motion.DURATION_ENTER)) +
            slideInHorizontally(tween(Motion.DURATION_SLIDE)) { it / 8 }
        },
        exitTransition = {
            fadeOut(tween(Motion.DURATION_EXIT)) +
            slideOutHorizontally(tween(Motion.DURATION_SLIDE)) { -it / 8 }
        },
        popEnterTransition = {
            fadeIn(tween(Motion.DURATION_ENTER)) +
            slideInHorizontally(tween(Motion.DURATION_SLIDE)) { -it / 8 }
        },
        popExitTransition = {
            fadeOut(tween(Motion.DURATION_EXIT)) +
            slideOutHorizontally(tween(Motion.DURATION_SLIDE)) { it / 8 }
        }
    ) {
        // Home — Compose composable
        composable(route = Screen.Home.route) {
            HomeScreen()
        }

        // Legacy Fragment destinations — inline deep-link scaffold for future use
        listOf(Screen.Wallet, Screen.Meals, Screen.Docs, Screen.Family).forEach { screen ->
            composable(
                route      = screen.route,
                deepLinks  = listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jugaad://${screen.route}" }
                )
            ) {
                LegacyFragmentHost(
                    route           = screen.route,
                    fragmentManager = fragmentManager
                )
            }
        }
    }
}
