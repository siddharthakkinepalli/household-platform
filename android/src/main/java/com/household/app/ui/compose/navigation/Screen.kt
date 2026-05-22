package com.household.app.ui.compose.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
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
    object VaultScanner : Screen("docs/scanner", Icons.Rounded.DocumentScanner, "Scanner")
    object PantryStaging : Screen("pantry_staging/{vaultId}", Icons.Rounded.ShoppingCart, "Pantry") {
        fun route(vaultId: Long) = "pantry_staging/$vaultId"
    }
    object Pantry : Screen("pantry", Icons.Rounded.ShoppingCart, "Pantry")
    object Family : Screen("family", Icons.Rounded.Group,                 "Family")
    object FamilyMemberDetail : Screen("family_member/{memberId}", Icons.Rounded.Group, "Member") {
        fun route(memberId: Long) = "family_member/$memberId"
    }
    object Config : Screen("config", Icons.Rounded.Tune,                  "Config")
    object MerchantRules : Screen("merchant_rules", Icons.Rounded.Tune,   "Merchant Rules")
    object Documents : Screen("documents", Icons.Rounded.FolderOpen,      "Documents")
    object DocumentsForOwner : Screen("documents/owner/{ownerId}", Icons.Rounded.FolderOpen, "Contracts") {
        fun route(ownerId: Long) = "documents/owner/$ownerId"
    }
    object DocsForMember : Screen("docs/for_member/{memberId}", Icons.Rounded.FolderOpen, "Vault") {
        fun route(memberId: Long) = "docs/for_member/$memberId"
    }
    object QrHost : Screen("qr_host", Icons.Rounded.Tune, "Share My Data")
    object QrScan : Screen("qr_scan", Icons.Rounded.Tune, "Scan Partner's QR")

    companion object {
        /** Ordered list used to build primary rail items and NavHost destinations. */
        val all: List<Screen> = listOf(Home, Wallet, Meals, Docs, Family, Config)
    }
}
