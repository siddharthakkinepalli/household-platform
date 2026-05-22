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
import com.household.app.data.entities.CategoryThresholdEntity
import com.household.app.data.entities.MerchantRuleEntity
import com.household.app.domain.utils.FiscalDateUtils
import com.household.app.domain.utils.MerchantNameCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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

enum class PulseStatus { SAFE, WARNING, CRITICAL }

data class PulseData(
    val status: PulseStatus,
    val headline: String,
    val projectedAmount: Double,
    val projectedLabel: String,
    val topRisk: String?,
    val upcoming: String?,
    val suggestion: String?,
    val daysLeft: Int
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

    private val _householdPulse = MutableLiveData<PulseData>()
    val householdPulse: LiveData<PulseData> = _householdPulse

    private val _categoryLimits = MutableLiveData(defaultCategoryLimits())
    val categoryLimits: LiveData<Map<String, Float>> = _categoryLimits

    init {
        refreshTransactions()
    }

    fun refreshTransactions() {
        viewModelScope.launch {
            try {
                val rules = withContext(Dispatchers.IO) { db.merchantRuleDao().getEnabledRules() }
                salaryAnchorDay = withContext(Dispatchers.IO) { DashboardPrefs.getSalaryAnchorDay(appContext) }
                monthlyBudget = withContext(Dispatchers.IO) { DashboardPrefs.getMonthlyBudget(appContext) }
                val thresholds = withContext(Dispatchers.IO) { db.categoryThresholdDao().getAllThresholds() }
                _categoryLimits.value = buildCategoryLimitsMap(thresholds)

                val mergedTransactions = withContext(Dispatchers.IO) {
                    WalletUserDataStore.loadMergedTransactions(appContext, walletDataLoader.loadTransactions())
                }
                val manualOverrides = withContext(Dispatchers.IO) {
                    db.transactionOverrideDao().getAllOverrides()
                        .filter { it.categoryOverride != null }
                        .associate { it.transactionId to it.categoryOverride!! }
                }

                allTransactions = mergedTransactions
                    .map { transaction ->
                        val hasManualOverride = manualOverrides.containsKey(transaction.id)
                        val rule = RuleEngineService.pickRule(transaction.title, rules)
                        val resolvedCategory = when {
                            transaction.excluded -> "Excluded"
                            rule?.isExclusion == true -> "Excluded"
                            hasManualOverride -> transaction.category
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

        val budgetCategoryNames = setOf("Groceries", "Eat Out", "Travel", "Shopping")
        val categorySpent = timeFilteredTransactions
            .filter { !it.category.equals("Excluded", ignoreCase = true) && it.amount < 0 }
            .filter { canonicalCategoryVM(it.category) in budgetCategoryNames }
            .sumOf { abs(it.amount) }
        val totalCategoryLimit = (_categoryLimits.value ?: defaultCategoryLimits()).values.sum().toDouble()
        _budgetLeft.value = totalCategoryLimit - categorySpent
        _householdPulse.value = computePulse(timeFilteredTransactions, categorySpent, totalCategoryLimit)
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

    private fun computePulse(cycleTransactions: List<Transaction>, categorySpent: Double, totalCategoryLimit: Double): PulseData {
        val today = LocalDate.now()

        if (_selectedTimeFilter.value != "Current Cycle") {
            return PulseData(
                status = PulseStatus.SAFE,
                headline = "Viewing historical data",
                projectedAmount = abs(totalCategoryLimit - categorySpent),
                projectedLabel = "budget remaining",
                topRisk = null, upcoming = null, suggestion = null, daysLeft = 0
            )
        }

        val cycleRange = FiscalDateUtils.getFiscalCycleRange(today, salaryAnchorDay)
        val daysElapsed = ChronoUnit.DAYS.between(cycleRange.first, today).toInt().coerceAtLeast(1)
        val daysLeft = ChronoUnit.DAYS.between(today, cycleRange.second).toInt().coerceAtLeast(0)

        val dailyBurn = categorySpent / daysElapsed
        val projectedSurplus = (totalCategoryLimit - categorySpent) - (dailyBurn * daysLeft)

        val status = when {
            projectedSurplus >= totalCategoryLimit * 0.08 -> PulseStatus.SAFE
            projectedSurplus >= 0 -> PulseStatus.WARNING
            else -> PulseStatus.CRITICAL
        }

        val headline = when (status) {
            PulseStatus.SAFE -> if (daysLeft <= 3) "Almost at salary day" else "Safe until salary"
            PulseStatus.WARNING -> "Spending on track — stay alert"
            PulseStatus.CRITICAL -> "Overspending detected"
        }

        val activeTransactions = cycleTransactions.filter {
            it.amount < 0 && !it.category.equals("Excluded", ignoreCase = true)
        }
        val last7Start = today.minusDays(6)
        val prev7End = today.minusDays(7)
        val prev7Start = today.minusDays(13)

        val recentByCategory = activeTransactions
            .filter { !it.bookedOn.isBefore(last7Start) }
            .groupBy { canonicalCategoryVM(it.category) }
            .mapValues { (_, txs) -> abs(txs.sumOf { it.amount }) }

        val prevByCategory = activeTransactions
            .filter { !it.bookedOn.isBefore(prev7Start) && !it.bookedOn.isAfter(prev7End) }
            .groupBy { canonicalCategoryVM(it.category) }
            .mapValues { (_, txs) -> abs(txs.sumOf { it.amount }) }

        val topRisk: String? = recentByCategory
            .filter { (_, amount) -> amount > 15.0 }
            .mapValues { (cat, amount) ->
                val prev = prevByCategory[cat] ?: 0.0
                if (prev > 0) (amount - prev) / prev else if (amount > 30.0) 1.0 else 0.0
            }
            .filter { (_, delta) -> delta > 0.15 }
            .maxByOrNull { (_, delta) -> delta }
            ?.let { (cat, delta) ->
                val prev = prevByCategory[cat] ?: 0.0
                if (prev == 0.0) "$cat spending spiking this week"
                else "$cat up ${(delta * 100).toInt()}% vs last week"
            }

        val upcoming = when {
            daysLeft == 0 -> "Salary day — cycle resets today"
            daysLeft == 1 -> "Salary arrives tomorrow"
            daysLeft <= 4 -> "Salary in $daysLeft days"
            else -> null
        }

        val topCat = recentByCategory.maxByOrNull { it.value }
        val suggestion: String? = when {
            status == PulseStatus.CRITICAL && daysLeft > 0 -> {
                val dailyCut = abs(projectedSurplus) / daysLeft
                "Spend €${"%.0f".format(dailyCut)} less per day to break even"
            }
            topRisk != null && topCat != null && daysLeft > 2 -> {
                val dailySpend = topCat.value / 7.0
                val saving = (dailySpend * 0.25 * daysLeft).toInt()
                if (saving >= 15) "Cut ${topCat.key.lowercase()} by 25% — save €$saving this cycle"
                else null
            }
            else -> null
        }

        return PulseData(
            status = status,
            headline = headline,
            projectedAmount = abs(projectedSurplus),
            projectedLabel = if (projectedSurplus >= 0) "projected surplus" else "projected shortfall",
            topRisk = topRisk,
            upcoming = upcoming,
            suggestion = suggestion,
            daysLeft = daysLeft
        )
    }

    private fun defaultCategoryLimits() = mapOf(
        "Groceries" to 600f,
        "Eat Out"   to 100f,
        "Travel"    to 195f,
        "Shopping"  to 100f
    )

    private fun buildCategoryLimitsMap(entities: List<CategoryThresholdEntity>): Map<String, Float> {
        val persisted = entities.associateBy { it.categoryId }
        return mapOf(
            "Groceries" to (persisted["groceries"]?.limitAmount ?: 600f),
            "Eat Out"   to (persisted["dining"]?.limitAmount    ?: 100f),
            "Travel"    to (persisted["travel"]?.limitAmount    ?: 195f),
            "Shopping"  to (persisted["shopping"]?.limitAmount  ?: 100f)
        )
    }

    private fun canonicalCategoryVM(category: String): String {
        val key = category.lowercase()
        return when {
            "grocer" in key -> "Groceries"
            "eat" in key || "food" in key || "restaurant" in key || "dining" in key -> "Dining"
            "travel" in key || "transport" in key -> "Travel"
            "utilit" in key || "rent" in key || "bill" in key -> "Utilities"
            "transfer" in key || "sepa" in key || "interbank" in key -> "Transfers"
            "shop" in key -> "Shopping"
            else -> "Other"
        }
    }

    private fun normalizeMerchant(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
    }
}
