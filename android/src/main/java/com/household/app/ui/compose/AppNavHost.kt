package com.household.app.ui.compose

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.household.app.ui.compose.navigation.Screen
import com.household.app.ui.compose.motion.Motion
import com.household.app.ui.v2.V2ConfigHubScreen
import com.household.app.ui.v2.V2DocumentVaultScreen
import com.household.app.ui.v2.V2FamilyScreen
import com.household.app.ui.v2.V2FinanceScreen
import com.household.app.ui.v2.V2MealsScreen

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
 * Home screen is the active Phase 2 Compose destination.
 * Other destinations stay on their already-working v2 implementations so the shell
 * migration does not disturb existing feature workflows.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController   = navController,
        startDestination = Screen.Home.route,
        modifier        = modifier,
        enterTransition = {
            fadeIn(Motion.FadeIn) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    Motion.Slide
                )
        },
        exitTransition = {
            fadeOut(Motion.FadeOut) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    Motion.Slide
                )
        },
        popEnterTransition = {
            fadeIn(Motion.FadeIn) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    Motion.Slide
                )
        },
        popExitTransition = {
            fadeOut(Motion.FadeOut) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    Motion.Slide
                )
        }
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(route = Screen.Wallet.route) { V2FinanceScreen() }
        composable(route = Screen.Meals.route) { V2MealsScreen() }
        composable(route = Screen.Family.route) { V2FamilyScreen() }
        composable(route = Screen.Config.route) { V2ConfigHubScreen() }

        composable(
            route = Screen.Docs.route,
            deepLinks = listOf(
                androidx.navigation.navDeepLink { uriPattern = "jugaad://${Screen.Docs.route}" }
            )
        ) {
            V2DocumentVaultScreen()
        }
    }
}
