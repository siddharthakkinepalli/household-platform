package com.household.app.ui.compose.state

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Restaurant
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.household.app.data.AppDatabase
import com.household.app.data.DashboardPrefs
import com.household.app.data.WalletDataLoader
import com.household.app.data.WalletUserDataStore
import com.household.app.domain.utils.FiscalDateUtils
import com.household.app.domain.utils.MerchantNameCleaner
import com.household.app.ui.compose.navigation.Screen
import com.household.app.ui.compose.theme.DocsColor
import com.household.app.ui.compose.theme.FamilyColor
import com.household.app.ui.compose.theme.MealsColor
import com.household.app.ui.compose.theme.WalletColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db by lazy { AppDatabase.getInstance(getApplication()) }
    private val walletLoader by lazy { WalletDataLoader(getApplication()) }

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    val modules: List<Module> = listOf(
        Module("wallet",  "Wallet",    Icons.Rounded.AccountBalanceWallet, WalletColor, "Transactions & budget", Screen.Wallet.route),
        Module("meals",   "Meals",     Icons.Rounded.Restaurant,           MealsColor,  "Meal planning",         Screen.Meals.route),
        Module("docs",    "Documents", Icons.Rounded.FolderOpen,           DocsColor,   "Vault & receipts",      Screen.Docs.route),
        Module("family",  "Family",    Icons.Rounded.Group,                FamilyColor, "Private by design",     Screen.Family.route)
    )

    init {
        handle(HomeIntent.Load)
        observeAnchorDay()
    }

    private fun observeAnchorDay() {
        viewModelScope.launch {
            db.dashboardPrefsDao()
                .observeAnchorDay()
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1) // skip initial emission already loaded by loadAll()
                .collect { loadAll() }
        }
    }

    fun handle(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.Load            -> loadAll()
            is HomeIntent.RefreshInsights -> loadAll()
            is HomeIntent.DismissInsight  -> dismissInsight(intent.id)
        }
    }

    private fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val anchorDay     = DashboardPrefs.getSalaryAnchorDay(ctx)
                    val monthlyBudget = DashboardPrefs.getMonthlyBudget(ctx)

                    val merged = WalletUserDataStore.loadMergedTransactions(ctx, walletLoader.loadTransactions())

                    // Budget left for current salary cycle (25th–25th)
                    val today      = LocalDate.now()
                    val cycleStart = FiscalDateUtils.getFiscalCycleStart(today, anchorDay)
                    val budgetCategories = setOf("Grocery", "Groceries", "Eat out", "Eat Out", "Travel", "Shopping")
                    val spent = merged
                        .filter { !it.excluded && it.amount < 0 && !it.date.isBefore(cycleStart) && it.category in budgetCategories }
                        .sumOf { abs(it.amount) }
                    val budgetLeft = (monthlyBudget - spent).coerceAtLeast(0.0)

                    val unlinkedCount = db.vaultDao().getUnlinkedCount()
                    val recentVault   = db.vaultDao().getRecentEntries(5)
                    val recentTx = merged
                        .filter { !it.excluded && it.amount < 0 }
                        .sortedByDescending { it.date }
                        .take(5)

                    val activity = buildActivity(recentVault, recentTx)

                    _state.update {
                        it.copy(
                            balanceValue      = budgetLeft,
                            balanceFormatted  = "€%,.2f".format(budgetLeft),
                            salaryAnchorDay   = anchorDay,
                            unlinkedVaultCount = unlinkedCount,
                            recentActivity    = activity,
                            loading           = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun buildActivity(
        vault: List<com.household.app.data.entities.VaultEntity>,
        transactions: List<WalletDataLoader.WalletTransaction>
    ): List<ActivityItem> {
        val vaultItems = vault.map {
            ActivityItem.Scan(
                id       = it.id,
                merchant = it.merchantName?.takeIf { m -> m.isNotBlank() } ?: "Receipt",
                amount   = it.totalAmount,
                currency = it.currency,
                epochMs  = it.dateEpoch
            )
        }
        val txItems = transactions.map {
            ActivityItem.Spend(
                id          = it.id,
                description = MerchantNameCleaner.clean(it.title),
                amount      = it.amount,
                category    = it.category,
                epochMs     = it.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        }
        return (vaultItems + txItems)
            .sortedByDescending { it.epochMs }
            .take(5)
    }

    private fun dismissInsight(id: String) {
        _state.update { state -> state.copy(insights = state.insights.filterNot { it.id == id }) }
    }
}
