package com.household.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.household.app.data.AppDatabase
import com.household.app.data.DashboardPrefs
import com.household.app.data.WalletDataLoader
import com.household.app.data.WalletUserDataStore
import com.household.app.data.config.RuleEngineService
import com.household.app.data.entities.MerchantRuleEntity
import com.household.app.domain.utils.FiscalDateUtils
import com.household.app.domain.utils.MerchantNameCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

data class Transaction(
    val id: String,
    val localId: Int,
    val description: String,
    val amount: Double,
    val category: String,
    val date: String,
    val bookedOn: LocalDate
)

data class CategorySummary(
    val category: String,
    val totalAmount: Double,
    val transactionCount: Int
)

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val db by lazy { AppDatabase.getInstance(appContext) }
    private val walletDataLoader by lazy { WalletDataLoader(appContext) }

    private var allTransactions: List<Transaction> = emptyList()
    private var monthlyBudget: Int = 3000
    private var salaryAnchorDay: Int = 25

    private val _recentTransactions = MutableLiveData<List<Transaction>>()
    val recentTransactions: LiveData<List<Transaction>> = _recentTransactions

    private val _categorySummary = MutableLiveData<List<CategorySummary>>()
    val categorySummary: LiveData<List<CategorySummary>> = _categorySummary

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _selectedCategory = MutableLiveData("All")
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _selectedTimeFilter = MutableLiveData("Current Cycle")
    val selectedTimeFilter: LiveData<String> = _selectedTimeFilter

    private val _activePeriodLabel = MutableLiveData("")
    val activePeriodLabel: LiveData<String> = _activePeriodLabel

    private val _budgetLeft = MutableLiveData(0.0)
    val budgetLeft: LiveData<Double> = _budgetLeft

    init {
        refreshTransactions()
    }

    fun refreshTransactions() {
        viewModelScope.launch {
            try {
                val rules = withContext(Dispatchers.IO) { db.merchantRuleDao().getEnabledRules() }
                salaryAnchorDay = withContext(Dispatchers.IO) { DashboardPrefs.getSalaryAnchorDay(appContext) }
                monthlyBudget = withContext(Dispatchers.IO) { DashboardPrefs.getMonthlyBudget(appContext) }

                val mergedTransactions = withContext(Dispatchers.IO) {
                    WalletUserDataStore.loadMergedTransactions(appContext, walletDataLoader.loadTransactions())
                }

                allTransactions = mergedTransactions
                    .map { transaction ->
                        val rule = RuleEngineService.pickRule(transaction.title, rules)
                        val resolvedCategory = when {
                            transaction.excluded -> "Excluded"
                            rule?.isExclusion == true -> "Excluded"
                            rule != null -> rule.targetCategoryId
                            else -> transaction.category
                        }
                        Transaction(
                            id = transaction.id.toString(),
                            localId = transaction.id,
                            description = MerchantNameCleaner.clean(transaction.title),
                            amount = transaction.amount,
                            category = resolvedCategory,
                            date = transaction.date.toString(),
                            bookedOn = transaction.date
                        )
                    }
                    .sortedByDescending { it.bookedOn }

                publishVisibleState()
                _errorMessage.value = null
            } catch (error: Exception) {
                _errorMessage.value = "Failed to load wallet data: ${error.message}"
            }
        }
    }

    fun filterByCategory(category: String) {
        _selectedCategory.value = if (category.isBlank()) "All" else category
    }

    fun selectTimeFilter(filter: String) {
        _selectedTimeFilter.value = when (filter) {
            "This Month" -> "Current Cycle"
            else -> filter
        }
        publishVisibleState()
    }

    fun reclassifyTransaction(
        transactionId: String,
        merchantName: String,
        newCategory: String,
        applyToHistory: Boolean
    ) {
        val localId = transactionId.toIntOrNull() ?: return
        val normalizedMerchant = normalizeMerchant(merchantName)
        val normalizedCategory = if (newCategory.equals("Exclude", ignoreCase = true)) {
            "Excluded"
        } else {
            newCategory
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (applyToHistory) {
                    db.merchantRuleDao().upsertRule(
                        MerchantRuleEntity(
                            merchantPattern = normalizedMerchant,
                            targetCategoryId = normalizedCategory,
                            isExclusion = normalizedCategory == "Excluded",
                            isEnabled = true,
                            priority = 100,
                            updatedAt = System.currentTimeMillis().toString()
                        )
                    )
                } else {
                    if (normalizedCategory == "Excluded") {
                        WalletUserDataStore.setTransactionExcluded(appContext, localId, true)
                    } else {
                        WalletUserDataStore.setTransactionExcluded(appContext, localId, false)
                        WalletUserDataStore.setCategoryOverride(appContext, localId, normalizedCategory)
                    }
                }
            }
            refreshTransactions()
        }
    }

    private fun publishVisibleState() {
        val timeFilteredTransactions = filterByTime(allTransactions)
        _recentTransactions.value = timeFilteredTransactions
        _categorySummary.value = buildCategorySummary(timeFilteredTransactions)
        _activePeriodLabel.value = buildActivePeriodLabel()

        // Budget only counts: Groceries, Eat out, Travel, Shopping (NOT Utilities or Transfers)
        val budgetCategories = setOf("Grocery", "Groceries", "Eat out", "Travel", "Shopping")
        val spent = timeFilteredTransactions
            .filter { !it.category.equals("Excluded", ignoreCase = true) && it.amount < 0 && it.category in budgetCategories }
            .sumOf { abs(it.amount) }
        _budgetLeft.value = monthlyBudget - spent
    }

    private fun filterByTime(transactions: List<Transaction>): List<Transaction> {
        val today = LocalDate.now()
        return when (_selectedTimeFilter.value ?: "Current Cycle") {
            "All Time" -> transactions
            "Previous Cycle" -> {
                val range = FiscalDateUtils.getPreviousFiscalCycleRange(today, salaryAnchorDay)
                transactions.filter { !it.bookedOn.isBefore(range.first) && !it.bookedOn.isAfter(range.second) }
            }
            else -> {
                val range = FiscalDateUtils.getFiscalCycleRange(today, salaryAnchorDay)
                transactions.filter { !it.bookedOn.isBefore(range.first) && !it.bookedOn.isAfter(range.second) }
            }
        }
    }

    private fun buildActivePeriodLabel(): String {
        val today = LocalDate.now()
        return when (_selectedTimeFilter.value ?: "Current Cycle") {
            "All Time" -> "All imported and baseline wallet activity"
            "Previous Cycle" -> {
                val range = FiscalDateUtils.getPreviousFiscalCycleRange(today, salaryAnchorDay)
                "Previous cycle • ${FiscalDateUtils.formatRangeLabel(range)}"
            }
            else -> {
                val range = FiscalDateUtils.getFiscalCycleRange(today, salaryAnchorDay)
                "Current cycle • ${FiscalDateUtils.formatRangeLabel(range)}"
            }
        }
    }

    private fun buildCategorySummary(transactions: List<Transaction>): List<CategorySummary> {
        return transactions
            .filterNot { it.category.equals("Excluded", ignoreCase = true) }
            .groupBy { it.category }
            .map { (category, items) ->
                CategorySummary(
                    category = category,
                    totalAmount = items.sumOf { it.amount },
                    transactionCount = items.size
                )
            }
            .sortedByDescending { abs(it.totalAmount) }
    }

    private fun normalizeMerchant(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
    }
}
