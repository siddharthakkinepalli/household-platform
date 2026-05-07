package com.household.app.ui.v2

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
import com.household.app.ui.compose.motion.Motion
import com.household.app.ui.compose.navigation.Screen

@Composable
fun V2AppNavHost(
    navController: NavHostController,
    fragmentManager: FragmentManager,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
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
        composable(route = Screen.Home.route) { V2HomeScreen() }
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
            V2DocumentVaultScreen(
                onScanClick = { navController.navigate(Screen.VaultScanner.route) }
            )
        }

        composable(route = Screen.VaultScanner.route) {
            V2ScannerScreen(
                onBack = { navController.popBackStack() },
                onScanProcessed = { navController.popBackStack() }
            )
        }
    }
}