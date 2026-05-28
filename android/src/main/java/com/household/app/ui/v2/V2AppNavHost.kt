package com.household.app.ui.v2

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.household.app.ui.viewmodels.VaultViewModel
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
        composable(route = Screen.Wallet.route) {
            V2FinanceScreen(onNavigateToTrips = { navController.navigate(Screen.Trips.route) })
        }
        composable(route = Screen.Trips.route) {
            TripTrackerScreen(onBack = { navController.popBackStack() })
        }
        composable(route = Screen.Meals.route) { V2MealsScreen() }
        composable(route = Screen.Family.route) {
            V2FamilyScreen(
                onMemberClick = { id ->
                    navController.navigate(Screen.FamilyMemberDetail.route(id))
                },
                onExpiryAlertClick = { ownerId ->
                    if (ownerId != null) {
                        navController.navigate(Screen.DocumentsForOwner.route(ownerId))
                    } else {
                        navController.navigate(Screen.Documents.route)
                    }
                }
            )
        }
        composable(
            route = Screen.FamilyMemberDetail.route,
            arguments = listOf(navArgument("memberId") { type = NavType.LongType })
        ) { backStackEntry ->
            val memberId = backStackEntry.arguments?.getLong("memberId") ?: return@composable
            FamilyMemberDetailScreen(
                memberId = memberId,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onOpenVault = { id -> navController.navigate(Screen.DocsForMember.route(id)) },
                onOpenContracts = { id, name ->
                    navController.navigate(Screen.DocumentsForOwner.route(id))
                }
            )
        }
        composable(route = Screen.Config.route) {
            V2ConfigHubScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMerchantRules = { navController.navigate(Screen.MerchantRules.route) },
                onNavigateToQrHost = { navController.navigate(Screen.QrHost.route) },
                onNavigateToQrScan = { navController.navigate(Screen.QrScan.route) },
                onNavigateToFamily = {
                    navController.navigate(Screen.Family.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToSteuerKlar = { navController.navigate(Screen.SteuerKlar.route) }
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
                onPantryClick = { navController.navigate(Screen.Pantry.route) },
                onNavigateToFamily = {
                    navController.navigate(Screen.Family.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToSubscriptionHub = { navController.navigate(Screen.SubscriptionHub.route) }
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

        composable(
            route = Screen.DocumentsForOwner.route,
            arguments = listOf(navArgument("ownerId") { type = NavType.LongType })
        ) { backStackEntry ->
            val ownerId = backStackEntry.arguments?.getLong("ownerId") ?: return@composable
            DocumentsScreen(
                navController = navController,
                ownerId = ownerId,
                screenTitle = "Contracts"
            )
        }

        composable(
            route = Screen.DocsForMember.route,
            arguments = listOf(navArgument("memberId") { type = NavType.LongType })
        ) { backStackEntry ->
            val memberId = backStackEntry.arguments?.getLong("memberId") ?: return@composable
            val context = LocalContext.current
            val vaultViewModel: VaultViewModel = viewModel(
                viewModelStoreOwner = context as androidx.activity.ComponentActivity,
                factory = viewModelFactory {
                    initializer { VaultViewModel(context.applicationContext as Application) }
                }
            )
            LaunchedEffect(memberId) {
                val member = com.household.app.data.AppDatabase
                    .getInstance(context)
                    .familyMemberDao()
                    .getMemberById(memberId)
                if (member != null) {
                    vaultViewModel.openMemberIdentityFolder(memberId, member.name)
                }
            }
            V2DocumentVaultScreen(
                onScanClick = { navController.navigate(Screen.VaultScanner.route) },
                onStagingRequested = { vaultId ->
                    navController.navigate(Screen.PantryStaging.route(vaultId))
                },
                onPantryClick = { navController.navigate(Screen.Pantry.route) },
                onNavigateToFamily = {
                    navController.navigate(Screen.Family.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToSubscriptionHub = { navController.navigate(Screen.SubscriptionHub.route) }
            )
        }

        composable(route = Screen.QrHost.route) {
            QrHostScreen(onBack = { navController.popBackStack() })
        }

        composable(route = Screen.QrScan.route) {
            QrScanScreen(onBack = { navController.popBackStack() })
        }

        composable(route = Screen.SteuerKlar.route) {
            SteuerKlarScreen(
                onNavigateToTaxSummary = { navController.navigate(Screen.TaxSummary.route) }
            )
        }

        composable(route = Screen.SubscriptionHub.route) {
            SubscriptionHubScreen()
        }

        composable(route = Screen.TaxSummary.route) {
            TaxSummaryScreen(onBack = { navController.popBackStack() })
        }
    }
}