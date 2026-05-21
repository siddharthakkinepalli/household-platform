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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
        composable(route = Screen.Home.route) {
            V2HomeScreen(
                onNavigateToVault = { navController.navigate(Screen.Docs.route) },
                onNavigateToModule = { route ->
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
        composable(route = Screen.Config.route) {
            V2ConfigHubScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMerchantRules = { navController.navigate(Screen.MerchantRules.route) }
            )
        }

        composable(
            route = Screen.Docs.route,
            deepLinks = listOf(
                androidx.navigation.navDeepLink { uriPattern = "jugaad://${Screen.Docs.route}" }
            )
        ) {
            V2DocumentVaultScreen(
                onScanClick = { navController.navigate(Screen.VaultScanner.route) },
                onStagingRequested = { vaultId ->
                    navController.navigate(Screen.PantryStaging.route(vaultId))
                },
                onPantryClick = { navController.navigate(Screen.Pantry.route) }
            )
        }

        composable(
            route = Screen.PantryStaging.route,
            arguments = listOf(navArgument("vaultId") { type = NavType.LongType })
        ) { backStackEntry ->
            val vaultId = backStackEntry.arguments?.getLong("vaultId") ?: return@composable
            PantryStagingScreen(
                vaultId = vaultId,
                onBack = { navController.popBackStack() },
                onConfirmed = { navController.popBackStack() }
            )
        }

        composable(route = Screen.VaultScanner.route) {
            V2ScannerScreen(
                onBack = { navController.popBackStack() },
                onScanProcessed = { navController.popBackStack() }
            )
        }

        composable(route = Screen.Pantry.route) {
            PantryScreen(onBack = { navController.popBackStack() })
        }

        composable(route = Screen.MerchantRules.route) {
            MerchantRulesScreen(navController = navController)
        }

        composable(route = Screen.Documents.route) {
            DocumentsScreen(navController = navController)
        }
    }
}