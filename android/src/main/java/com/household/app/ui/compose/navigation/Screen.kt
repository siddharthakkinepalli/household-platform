package com.household.app.ui.compose.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sealed class defining every nav destination.
 * route   — used by NavHost and navController.navigate()
 * icon    — Material Rounded icon shown in the nav rail
 * label   — displayed when rail is expanded
 *
 * Screen.all drives the rail items and the NavHost route loop.
 * Deep-link URIs: jugaad://<route>
 */
sealed class Screen(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object Home   : Screen("home",   Icons.Rounded.Cottage,               "Home")
    object Wallet : Screen("wallet", Icons.Rounded.AccountBalanceWallet,  "Wallet")
    object Meals  : Screen("meals",  Icons.Rounded.Restaurant,            "Meals")
    object Docs   : Screen("docs",   Icons.Rounded.FolderOpen,            "Documents")
    object Family : Screen("family", Icons.Rounded.Group,                 "Family")
    object Config : Screen("config", Icons.Rounded.Tune,                  "Config")

    companion object {
        /** Ordered list used to build primary rail items and NavHost destinations. */
        val all: List<Screen> = listOf(Home, Wallet, Meals, Docs, Family, Config)
    }
}
