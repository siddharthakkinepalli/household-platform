package com.household.app.ui.v2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentManager
import androidx.navigation.compose.rememberNavController
import com.household.app.domain.usecases.ApplyTransferCategoryMigrationUseCase
import com.household.app.ui.compose.theme.HouseholdPlatformTheme
import com.household.app.ui.v2.components.DeepBackground
import com.household.app.ui.v2.components.EliteBottomNav

@Composable
fun V2AppShell(
    fragmentManager: FragmentManager,
    onFinish: () -> Unit
) {
    val context = LocalContext.current

    // Run one-time migration on app launch
    LaunchedEffect(Unit) {
        ApplyTransferCategoryMigrationUseCase.execute(context)
    }

    HouseholdPlatformTheme {
        val navController = rememberNavController()
        val currentRoute by remember {
            derivedStateOf { navController.currentBackStackEntry?.destination?.route }
        }

        BackHandler {
            if (!navController.popBackStack()) onFinish()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            DeepBackground()

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                bottomBar = {
                    EliteBottomNav(
                        navController = navController,
                        currentRoute = currentRoute
                    )
                }
            ) { padding ->
                V2AppNavHost(
                    navController = navController,
                    fragmentManager = fragmentManager,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}