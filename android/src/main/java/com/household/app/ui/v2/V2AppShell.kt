package com.household.app.ui.v2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.navigation.compose.rememberNavController
import com.household.app.data.OnboardingDataStore
import com.household.app.domain.usecases.ApplyTransferCategoryMigrationUseCase
import com.household.app.ui.v2.components.DeepBackground
import com.household.app.ui.v2.components.EliteBottomNav
import kotlinx.coroutines.launch

@Composable
fun V2AppShell(
    fragmentManager: FragmentManager,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showOnboarding by remember { mutableStateOf<Boolean?>(null) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted state handled by system; worker fires regardless on older APIs */ }

    LaunchedEffect(Unit) {
        ApplyTransferCategoryMigrationUseCase.execute(context)
        showOnboarding = !OnboardingDataStore.hasOnboarded(context)
    }

    // Request POST_NOTIFICATIONS once onboarding is done (Android 13+)
    LaunchedEffect(showOnboarding) {
        if (showOnboarding == false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showOnboarding == null) return

    if (showOnboarding == true) {
        OnboardingScreen(
            onFinished = {
                scope.launch {
                    OnboardingDataStore.markOnboarded(context)
                    showOnboarding = false
                }
            }
        )
        return
    }

    // Theme is owned by MainActivity (JugaadTheme wraps the full setContent block).
    // V2AppShell only owns navigation + scaffold — no theme wrapper here.
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
