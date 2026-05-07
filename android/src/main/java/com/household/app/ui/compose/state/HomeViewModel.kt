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
import com.household.app.ui.compose.state.InsightPriority.HIGH
import com.household.app.ui.compose.state.InsightPriority.LOW
import com.household.app.ui.compose.state.InsightPriority.MEDIUM
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
            _state.update {
                it.copy(
                    userName = "Siddharth",
                    balanceValue = 2480.0,
                    balanceFormatted = "€2,480.00",
                    deltaPercent = 18f,
                    loading = false
                )
            }
        }
    }

    private fun loadInsights() {
        viewModelScope.launch {
            val mockInsights = listOf(
                Insight(
                    id       = "mock_1",
                    type     = InsightType.SUCCESS,
                    category = "BUDGET",
                    priority = MEDIUM,
                    title    = "Spending trend improved",
                    message  = "You're 18% under last month. Review Wallet for the strongest savings categories.",
                    action   = Screen.Wallet.route
                ),
                Insight(
                    id       = "mock_2",
                    type     = InsightType.WARNING,
                    category = "RENEWAL",
                    priority = HIGH,
                    title    = "Insurance renewal",
                    message  = "Insurance renewal is due in 12 days.",
                    action   = Screen.Docs.route
                ),
                Insight(
                    id       = "mock_3",
                    type     = InsightType.INFO,
                    category = "MEALS",
                    priority = LOW,
                    title    = "Meal planning hint",
                    message  = "One dinner slot is still unplanned this week.",
                    action   = Screen.Meals.route
                )
            ).sortedBy { insightPriorityRank(it.priority) }
            _state.update { it.copy(insights = mockInsights) }
        }
    }

    private fun dismissInsight(id: String) {
        _state.update { state ->
            state.copy(insights = state.insights.filterNot { it.id == id })
        }
    }

    private fun insightPriorityRank(priority: InsightPriority): Int {
        return when (priority) {
            HIGH -> 0
            MEDIUM -> 1
            LOW -> 2
        }
    }
}
