package com.household.app.ui.compose.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.household.app.ui.compose.navigation.Screen
import com.household.app.ui.compose.theme.DocsColor
import com.household.app.ui.compose.theme.FamilyColor
import com.household.app.ui.compose.theme.MealsColor
import com.household.app.ui.compose.theme.WalletColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Restaurant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HomeViewModel — MVI pattern.
 *
 * Phase 2: insights are generic placeholders so the public repo does not ship household data.
 * Phase 3: replace loadInsights() with a real InsightRepository call to GET /api/v1/insights.
 */
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    // ── Static module definitions ─────────────────────────────────────────
    val modules: List<Module> = listOf(
        Module("wallet",  "Wallet",    Icons.Rounded.AccountBalanceWallet, WalletColor, "Placeholder data", Screen.Wallet.route),
        Module("meals",   "Meals",     Icons.Rounded.Restaurant,           MealsColor,  "Template plan",   Screen.Meals.route),
        Module("docs",    "Documents", Icons.Rounded.FolderOpen,           DocsColor,   "Starter docs",    Screen.Docs.route),
        Module("family",  "Family",    Icons.Rounded.Group,                FamilyColor, "Private by design", Screen.Family.route)
    )

    init {
        handle(HomeIntent.Load)
    }

    fun handle(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.Load             -> loadAll()
            is HomeIntent.RefreshInsights  -> loadInsights()
            is HomeIntent.DismissInsight   -> dismissInsight(intent.id)
        }
    }

    private fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            loadInsights()
            // Phase 3: load balance from WalletRepository.
            // Public builds use placeholders instead of real household totals.
            _state.update { it.copy(balanceFormatted = "€0.00", deltaPercent = 0f, loading = false) }
        }
    }

    private fun loadInsights() {
        viewModelScope.launch {
            // Phase 2: generic placeholder insight.
            // Phase 3: replace with: insightRepository.getInsights().fold(...)
            val mockInsights = listOf(
                Insight(
                    id       = "mock_1",
                    type     = InsightType.INFO,
                    category = "NOTICE",
                    title    = "Public repository notice",
                    message  = "This public build ships placeholder content only. JUGAAD assets and app content are copyrighted and are not licensed for republishing in other apps.",
                    action   = ""
                )
            )
            _state.update { it.copy(insights = mockInsights) }
        }
    }

    private fun dismissInsight(id: String) {
        _state.update { state ->
            state.copy(insights = state.insights.filterNot { it.id == id })
        }
    }
}
