package com.household.app.ui.compose.state

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.household.app.data.AppDatabase
import com.household.app.data.DashboardPrefs
import com.household.app.data.WalletDataLoader
import com.household.app.data.WalletUserDataStore
import com.household.app.domain.utils.FiscalDateUtils
import com.household.app.domain.utils.MerchantNameCleaner
import com.household.app.ui.compose.navigation.Screen
import com.household.app.ui.compose.theme.DocsColor
import com.household.app.ui.compose.theme.FamilyColor
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.WalletColor
import androidx.compose.material.icons.rounded.AutoAwesome
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
        Module("wallet",  "Wallet",    Icons.Rounded.AccountBalanceWallet, WalletColor,  "Expenses & budget", Screen.Wallet.route),
        // Meals module removed — stub with no real data flow (JUGAAD OS: no half-built tabs)
        Module("docs",    "Documents", Icons.Rounded.FolderOpen,           DocsColor,    "Vault & receipts",  Screen.Docs.route),
        Module("family",  "Family",    Icons.Rounded.Group,                FamilyColor,  "Family hub",        Screen.Family.route),
        Module("pantry",  "Pantry",    Icons.Rounded.ShoppingCart,         LumeEmerald,  "Stock & consume",   Screen.Pantry.route),
        Module("astro",   "Astro",     Icons.Rounded.AutoAwesome,         LumeAmber,    "Vedic insights",    Screen.Astro.route)
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
                    val familyCount   = db.familyMemberDao().getAllMembersList().size
                    val recentVault   = db.vaultDao().getRecentEntries(5)
                    val recentTx = merged
                        .filter { !it.excluded && it.amount < 0 }
                        .sortedByDescending { it.date }
                        .take(5)

                    val activity = buildActivity(recentVault, recentTx)

                    // Per-category budgets
                    val cycleTransactions = merged
                        .filter { !it.excluded && it.amount < 0 && !it.date.isBefore(cycleStart) }

                    val dbThresholds = db.categoryThresholdDao().getAllThresholds()
                        .associateBy { it.categoryId }

                    val defaultLimits = mapOf(
                        "groceries" to 600.0,
                        "travel"    to 150.0,
                        "dining"    to 120.0,
                        "shopping"  to 120.0
                    )

                    val categoryMatchMap: Map<String, List<String>> = mapOf(
                        "groceries" to listOf("grocery", "groceries"),
                        "travel"    to listOf("travel", "transport"),
                        "dining"    to listOf("eat out", "dining"),
                        "shopping"  to listOf("shopping")
                    )

                    val categoryColors: Map<String, Color> = mapOf(
                        "groceries" to Color(0xFF22C55E),
                        "travel"    to Color(0xFF60A5FA),
                        "dining"    to Color(0xFFFB7185),
                        "shopping"  to Color(0xFFEC4899)
                    )

                    val categoryNames: Map<String, String> = mapOf(
                        "groceries" to "Groceries",
                        "travel"    to "Travel",
                        "dining"    to "Dining",
                        "shopping"  to "Shopping"
                    )

                    val categoryBudgets = defaultLimits.keys.map { catId ->
                        val limit = dbThresholds[catId]?.limitAmount?.toDouble()
                            ?: defaultLimits[catId]!!
                        val matchPatterns = categoryMatchMap[catId] ?: emptyList()
                        val spent = cycleTransactions
                            .filter { tx -> matchPatterns.any { tx.category.lowercase() == it } }
                            .sumOf { abs(it.amount) }
                        CategoryBudget(
                            id    = catId,
                            name  = categoryNames[catId] ?: catId,
                            spent = spent,
                            limit = limit,
                            color = categoryColors[catId] ?: Color.Gray
                        )
                    }

                    _state.update {
                        it.copy(
                            balanceValue       = budgetLeft,
                            balanceFormatted   = "€%,.2f".format(budgetLeft),
                            salaryAnchorDay    = anchorDay,
                            unlinkedVaultCount = unlinkedCount,
                            recentActivity     = activity,
                            categoryBudgets    = categoryBudgets,
                            familyMemberCount  = familyCount,
                            loading            = false
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
