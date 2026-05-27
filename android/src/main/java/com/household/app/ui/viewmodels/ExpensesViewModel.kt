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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

data class MonthlySpend(
    val label: String,
    val categories: Map<String, Double>,
    val total: Double
)

data class SalaryAllocationData(
    val salaryAmount: Double,
    val salaryDateLabel: String,
    val fixedTotal: Double,
    val fixedBills: List<Pair<String, Double>>,
    val discretionaryTotal: Double,
    val remaining: Double,
    val fixedPercent: Float,
    val discretionaryPercent: Float,
    val remainingPercent: Float
)

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

data class CategorySparkline(
    val category: String,
    val prevPrevAmount: Float,
    val prevAmount: Float,
    val currentAmount: Float,
    val delta: Double  // current - prev (positive = more spent)
)

data class FinancialAlert(
    val emoji: String,
    val label: String,
    val detail: String,
    val amount: Double?,
    val tintKey: String
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
    private var currentSalarySource: com.household.app.data.entities.SalarySourceEntity? = null
    private var currentActiveBills: List<com.household.app.data.entities.RecurringBillEntity> = emptyList()

    private val _recentTransactions = MutableLiveData<List<Transaction>>()
    val recentTransactions: LiveData<List<Transaction>> = _recentTransactions

    private val _ftsResults = MutableStateFlow<List<Transaction>?>(null)
    val ftsResults: StateFlow<List<Transaction>?> = _ftsResults.asStateFlow()

    fun searchFts(query: String) {
        if (query.length < 2) { _ftsResults.value = null; return }
        viewModelScope.launch(Dispatchers.IO) {
            val ftsQuery = "${query.trim()}*"
            val entities = runCatching { db.walletTransactionDao().searchTransactionsFts(ftsQuery) }
                .getOrDefault(emptyList())
            _ftsResults.value = entities.map { e ->
                Transaction(
                    id = e.id.toString(),
                    localId = e.id,
                    description = MerchantNameCleaner.clean(e.title),
                    amount = e.amount,
                    category = e.category,
                    date = e.date.toString(),
                    bookedOn = e.date
                )
            }
        }
    }

    fun clearFtsSearch() { _ftsResults.value = null }

    private val _spendingTrends = MutableStateFlow<List<MonthlySpend>>(emptyList())
    val spendingTrends: StateFlow<List<MonthlySpend>> = _spendingTrends.asStateFlow()

    private fun computeSpendingTrends(transactions: List<Transaction>) {
        val excluded = setOf("income", "salary", "excluded", "transfers")
        val cutoff = YearMonth.now().minusMonths(5)
        val groups = transactions
            .filter { tx ->
                tx.amount < 0 &&
                tx.category.lowercase() !in excluded &&
                !tx.category.equals("Excluded", ignoreCase = true) &&
                YearMonth.from(tx.bookedOn) >= cutoff
            }
            .groupBy { YearMonth.from(it.bookedOn) }

        val months = (0L..5L).map { YearMonth.now().minusMonths(5 - it) }
        _spendingTrends.value = months.map { ym ->
            val txForMonth = groups[ym] ?: emptyList()
            val byCategory = txForMonth
                .groupBy { canonicalTrendCategory(it.category) }
                .mapValues { (_, list) -> list.sumOf { -it.amount } }
            MonthlySpend(
                label = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                categories = byCategory,
                total = byCategory.values.sum()
            )
        }
    }

    private fun canonicalTrendCategory(raw: String): String {
        val key = raw.lowercase()
        return when {
            "grocer" in key -> "Groceries"
            "dining" in key || "takeaway" in key || "mensa" in key || "school lunch" in key -> "Dining"
            "long-distance" in key || "local transit" in key -> "Transport"
            "travel" in key || "transport" in key -> "Transport"
            "gas & fuel" in key || "carsharing" in key -> "Transport"
            "shopping" in key || "clothing" in key || "home & furniture" in key || "diy" in key || "electronics" in key -> "Shopping"
            "housing" in key || "utilities" in key || "broadcasting" in key || "internet" in key -> "Utilities"
            "insurance" in key || "pharmacy" in key -> "Insurance"
            "income" in key -> "Income"
            else -> "Other"
        }
    }

    private val _categorySummary = MutableLiveData<List<CategorySummary>>()
    val categorySummary: LiveData<List<CategorySummary>> = _categorySummary

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _selectedCategory = MutableLiveData("All")
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _selectedTimeFilter = MutableLiveData("Current Cycle")
    val selectedTimeFilter: LiveData<String> = _selectedTimeFilter

    private val _customYearMonth = MutableLiveData<YearMonth?>(null)
    val customYearMonth: LiveData<YearMonth?> = _customYearMonth

    private val _activePeriodLabel = MutableLiveData("")
    val activePeriodLabel: LiveData<String> = _activePeriodLabel

    private val _budgetLeft = MutableLiveData(0.0)
    val budgetLeft: LiveData<Double> = _budgetLeft

    private val _householdPulse = MutableLiveData<PulseData>()
    val householdPulse: LiveData<PulseData> = _householdPulse

    private val _salaryAllocation = MutableLiveData<SalaryAllocationData?>()
    val salaryAllocation: LiveData<SalaryAllocationData?> = _salaryAllocation

    private val _incomeTransactions = MutableLiveData<List<Transaction>>(emptyList())
    val incomeTransactions: LiveData<List<Transaction>> = _incomeTransactions

    private val _smartAlerts = MutableStateFlow<List<FinancialAlert>>(emptyList())
    val smartAlerts: StateFlow<List<FinancialAlert>> = _smartAlerts.asStateFlow()

    private val _categoryLimits = MutableLiveData(defaultCategoryLimits())
    val categoryLimits: LiveData<Map<String, Float>> = _categoryLimits

    private val _categorySparklines = MutableLiveData<List<CategorySparkline>>(emptyList())
    val categorySparklines: LiveData<List<CategorySparkline>> = _categorySparklines

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

                computeSpendingTrends(allTransactions)
                currentSalarySource = withContext(Dispatchers.IO) { db.salarySourceDao().getSalarySource() }
                currentActiveBills = withContext(Dispatchers.IO) { db.recurringBillDao().getActiveBills() }

                val cycleRange = com.household.app.domain.utils.FiscalDateUtils.getFiscalCycleRange(LocalDate.now(), salaryAnchorDay)
                val cycleTransactions = allTransactions.filter {
                    !it.bookedOn.isBefore(cycleRange.first) && !it.bookedOn.isAfter(cycleRange.second)
                }
                computeSmartAlerts(cycleTransactions)
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
        if (filter != "Custom") _customYearMonth.value = null
        _selectedTimeFilter.value = when (filter) {
            "This Month" -> "Current Cycle"
            else -> filter
        }
        publishVisibleState()
    }

    fun selectCustomMonth(ym: YearMonth) {
        _customYearMonth.value = ym
        _selectedTimeFilter.value = "Custom"
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

    private val taxDeductibleCategories = setOf(
        "school fees & education", "school lunch / mensa", "local activities & sports",
        "insurance", "pharmacy & health", "broadcasting license"
    )

    private suspend fun computeSmartAlerts(cycleTransactions: List<Transaction>) {
        val alerts = mutableListOf<FinancialAlert>()
        val today = LocalDate.now()

        // 1. New subscriptions — spotted exactly twice (just became recurring)
        currentActiveBills.filter { it.cycleCount == 2 && it.isActive }.take(2).forEach { bill ->
            alerts.add(FinancialAlert(
                emoji = "🔔",
                label = "New subscription",
                detail = "${bill.merchantPattern.replaceFirstChar { it.uppercase() }} · €${"%.2f".format(bill.normalizedAmount)}/mo",
                amount = bill.normalizedAmount,
                tintKey = "amber"
            ))
        }

        // 2. Upcoming bills in next 10 days based on 30-day cadence from lastSeenDate
        currentActiveBills.filter { it.isActive }.forEach { bill ->
            val lastSeen = LocalDate.ofEpochDay(bill.lastSeenDate / 86_400_000L)
            val nextExpected = lastSeen.plusDays(30)
            val daysUntil = ChronoUnit.DAYS.between(today, nextExpected).toInt()
            if (daysUntil in 0..10) {
                alerts.add(FinancialAlert(
                    emoji = "📅",
                    label = if (daysUntil == 0) "Due today" else "Due in $daysUntil day${if (daysUntil == 1) "" else "s"}",
                    detail = "${bill.merchantPattern.replaceFirstChar { it.uppercase() }} · €${"%.0f".format(bill.normalizedAmount)}",
                    amount = bill.normalizedAmount,
                    tintKey = if (daysUntil <= 3) "red" else "amber"
                ))
            }
        }

        // 3. Upcoming document expiry alerts (unacknowledged, within 30 days)
        runCatching {
            withContext(Dispatchers.IO) { db.documentAlertDao().getAlertsDueWithinDaysList(30) }
        }.getOrNull()?.take(2)?.forEach { alert ->
            alerts.add(FinancialAlert(
                emoji = "📄",
                label = "Expires in ${alert.daysUntil}d",
                detail = alert.message.take(50),
                amount = null,
                tintKey = if (alert.daysUntil <= 7) "red" else "cyan"
            ))
        }

        // 4. Tax-deductible spending this cycle
        val taxTotal = cycleTransactions
            .filter { it.amount < 0 && it.category.lowercase() in taxDeductibleCategories }
            .sumOf { abs(it.amount) }
        if (taxTotal >= 20.0) {
            alerts.add(FinancialAlert(
                emoji = "💶",
                label = "Tax-deductible",
                detail = "€${"%.0f".format(taxTotal)} trackable this cycle",
                amount = taxTotal,
                tintKey = "emerald"
            ))
        }

        // 5. Uncategorized nudge
        val uncategorizedCount = cycleTransactions.count {
            it.category.equals("Uncategorized", ignoreCase = true)
        }
        if (uncategorizedCount > 0) {
            alerts.add(FinancialAlert(
                emoji = "❓",
                label = "$uncategorizedCount uncategorized",
                detail = "Tap a transaction to assign a category",
                amount = null,
                tintKey = "purple"
            ))
        }

        _smartAlerts.value = alerts
    }

    private fun publishVisibleState() {
        val timeFilteredTransactions = filterByTime(allTransactions)
        _recentTransactions.value = timeFilteredTransactions
        _categorySummary.value = buildCategorySummary(timeFilteredTransactions)
        _activePeriodLabel.value = buildActivePeriodLabel()

        val budgetCategoryNames = setOf("Groceries", "Dining", "Travel", "Shopping")
        val categorySpent = timeFilteredTransactions
            .filter { !it.category.equals("Excluded", ignoreCase = true) && it.amount < 0 }
            .filter { canonicalCategoryVM(it.category) in budgetCategoryNames }
            .sumOf { abs(it.amount) }
        val totalCategoryLimit = (_categoryLimits.value ?: defaultCategoryLimits()).values.sum().toDouble()
        _budgetLeft.value = totalCategoryLimit - categorySpent
        _householdPulse.value = computePulse(timeFilteredTransactions, categorySpent, totalCategoryLimit)

        // Income transactions this cycle (positive amounts categorised as Income)
        val incomeThisCycle = timeFilteredTransactions.filter { it.category.equals("Income", ignoreCase = true) }
        _incomeTransactions.value = incomeThisCycle

        // Salary allocation bar
        val salary = currentSalarySource
        if (salary != null) {
            val fixedTotal = currentActiveBills.sumOf { it.normalizedAmount }
            val bills = currentActiveBills
                .sortedByDescending { it.normalizedAmount }
                .take(5)
                .map { it.merchantPattern.replaceFirstChar { c -> c.uppercase() } to it.normalizedAmount }
            val salaryAmt = salary.lastAmount
            val total = salaryAmt.coerceAtLeast(1.0)
            val dateLabel = java.time.Instant.ofEpochMilli(salary.confirmedAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
            _salaryAllocation.value = SalaryAllocationData(
                salaryAmount = salaryAmt,
                salaryDateLabel = dateLabel,
                fixedTotal = fixedTotal,
                fixedBills = bills,
                discretionaryTotal = categorySpent,
                remaining = (salaryAmt - fixedTotal - categorySpent).coerceAtLeast(0.0),
                fixedPercent = (fixedTotal / total).toFloat().coerceIn(0f, 1f),
                discretionaryPercent = (categorySpent / total).toFloat().coerceIn(0f, 1f),
                remainingPercent = ((salaryAmt - fixedTotal - categorySpent).coerceAtLeast(0.0) / total).toFloat().coerceIn(0f, 1f)
            )
        } else {
            _salaryAllocation.value = null
        }

        // 3-cycle sparklines for category tiles
        val today = LocalDate.now()
        val currentRange = FiscalDateUtils.getFiscalCycleRange(today, salaryAnchorDay)
        val prevRange = FiscalDateUtils.getPreviousFiscalCycleRange(today, salaryAnchorDay)
        val prevPrevRange = FiscalDateUtils.getPreviousFiscalCycleRange(prevRange.first.minusDays(1), salaryAnchorDay)

        fun amountForCategory(range: Pair<LocalDate, LocalDate>, cat: String): Float {
            return allTransactions
                .filter { !it.bookedOn.isBefore(range.first) && !it.bookedOn.isAfter(range.second) }
                .filter { canonicalCategoryVM(it.category) == cat && it.amount < 0 }
                .sumOf { abs(it.amount) }
                .toFloat()
        }

        val sparkCategories = listOf("Groceries", "Dining", "Travel", "Shopping")
        _categorySparklines.value = sparkCategories.map { cat ->
            val prev = amountForCategory(prevRange, cat)
            val current = amountForCategory(currentRange, cat)
            CategorySparkline(
                category = cat,
                prevPrevAmount = amountForCategory(prevPrevRange, cat),
                prevAmount = prev,
                currentAmount = current,
                delta = (current - prev).toDouble()
            )
        }
    }

    private fun filterByTime(transactions: List<Transaction>): List<Transaction> {
        val today = LocalDate.now()
        return when (_selectedTimeFilter.value ?: "Current Cycle") {
            "All Time" -> transactions
            "Previous Cycle" -> {
                val range = FiscalDateUtils.getPreviousFiscalCycleRange(today, salaryAnchorDay)
                transactions.filter { !it.bookedOn.isBefore(range.first) && !it.bookedOn.isAfter(range.second) }
            }
            "Custom" -> {
                val ym = _customYearMonth.value
                val range = if (ym != null) {
                    val startDate = ym.atDay(salaryAnchorDay.coerceIn(1, ym.lengthOfMonth()))
                    FiscalDateUtils.getFiscalCycleRange(startDate, salaryAnchorDay)
                } else {
                    FiscalDateUtils.getFiscalCycleRange(today, salaryAnchorDay)
                }
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
            "Custom" -> {
                val ym = _customYearMonth.value
                if (ym == null) {
                    val range = FiscalDateUtils.getFiscalCycleRange(today, salaryAnchorDay)
                    "Current cycle • ${FiscalDateUtils.formatRangeLabel(range)}"
                } else {
                    val startDate = ym.atDay(salaryAnchorDay.coerceIn(1, ym.lengthOfMonth()))
                    val range = FiscalDateUtils.getFiscalCycleRange(startDate, salaryAnchorDay)
                    val fmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
                    "${ym.format(fmt)} cycle • ${FiscalDateUtils.formatRangeLabel(range)}"
                }
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
        "Dining"    to 100f,
        "Travel"    to 195f,
        "Shopping"  to 100f
    )

    private fun buildCategoryLimitsMap(entities: List<CategoryThresholdEntity>): Map<String, Float> {
        val persisted = entities.associateBy { it.categoryId }
        return mapOf(
            "Groceries" to (persisted["groceries"]?.limitAmount ?: 600f),
            "Dining"    to (persisted["dining"]?.limitAmount    ?: 100f),
            "Travel"    to (persisted["travel"]?.limitAmount    ?: 195f),
            "Shopping"  to (persisted["shopping"]?.limitAmount  ?: 100f)
        )
    }

    private fun canonicalCategoryVM(category: String): String {
        val key = category.lowercase()
        return when {
            "grocer" in key -> "Groceries"
            "dining" in key || "takeaway" in key || "mensa" in key || "school lunch" in key -> "Dining"
            "transport" in key || "transit" in key || "travel" in key || "gas & fuel" in key || "carsharing" in key -> "Travel"
            "utilit" in key || "housing" in key || "rent" in key || "broadcasting" in key || "internet" in key || "mobile" in key -> "Utilities"
            "transfer" in key || "sepa" in key || "interbank" in key -> "Transfers"
            "shop" in key || "clothing" in key || "home & furniture" in key || "diy" in key || "electronics" in key || "discount" in key -> "Shopping"
            "insurance" in key || "pharmacy" in key || "health" in key -> "Insurance"
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
