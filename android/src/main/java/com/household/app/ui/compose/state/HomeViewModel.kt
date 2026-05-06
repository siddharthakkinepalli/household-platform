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
 * Phase 2: insights are hardcoded mocks so the UI can be validated.
 * Phase 3: replace loadInsights() with a real InsightRepository call to GET /api/v1/insights.
 */
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    // ── Static module definitions ─────────────────────────────────────────
    val modules: List<Module> = listOf(
        Module("wallet",  "Wallet",    Icons.Rounded.AccountBalanceWallet, WalletColor, "€0.00 spent",  Screen.Wallet.route),
        Module("meals",   "Meals",     Icons.Rounded.Restaurant,           MealsColor,  "2 planned",    Screen.Meals.route),
        Module("docs",    "Documents", Icons.Rounded.FolderOpen,           DocsColor,   "0 files",      Screen.Docs.route),
        Module("family",  "Family",    Icons.Rounded.Group,                FamilyColor, "1 member",     Screen.Family.route)
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
            // Phase 3: load balance from WalletRepository
            _state.update { it.copy(balanceFormatted = "€1,240.00", deltaPercent = -12f, loading = false) }
        }
    }

    private fun loadInsights() {
        viewModelScope.launch {
            // Phase 2: hardcoded mock insight.
            // Phase 3: replace with: insightRepository.getInsights().fold(...)
            val mockInsights = listOf(
                Insight(
                    id       = "mock_1",
                    type     = InsightType.WARNING,
                    category = "SUBSCRIPTION",
                    title    = "Subscription detected",
                    message  = "2 subscriptions renewing this week — review in Wallet",
                    action   = Screen.Wallet.route
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
