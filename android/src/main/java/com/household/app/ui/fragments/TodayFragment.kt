package com.household.app.ui.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.household.app.R
import com.household.app.data.DashboardPrefs
import com.household.app.data.TransactionCategorizer
import com.household.app.data.WalletDataLoader
import com.household.app.data.WalletUserDataStore
import com.household.app.ui.views.SpendingDonutView
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class TodayFragment : Fragment() {

    private enum class BillingCadence {
        MONTHLY_30,
        AFTER_SALARY
    }

    private data class SubscriptionRule(
        val label: String,
        val keywords: List<String>,
        val cadence: BillingCadence = BillingCadence.MONTHLY_30
    )

    private data class DueItem(
        val title: String,
        val amount: Double,
        val dueDate: LocalDate,
        val type: String
    )

    private data class ExpenseSummary(
        val monthTotal: Double,
        val monthLabel: String,
        val budgetRemaining: Double,
        val budgetUsedPercent: Int,
        val monthDeltaPercent: Double,
        val monthDeltaAbsolute: Double,
        val todaySpend: Double,
        val topCategory: String,
        val topMerchant: String,
        val categoryTotals: List<Pair<String, Double>>,
        val categoryBlocks: Map<String, Double>,
        val recentTransactions: List<WalletDataLoader.WalletTransaction>,
        val sparklineDailyTotals: List<Double>,
        val subscriptionsDueCount: Int,
        val feesDueCount: Int,
        val dueItems: List<DueItem>,
        val insight: String
    )

    private data class DueVisualStyle(
        val cardDrawable: Int,
        val titleColor: Int,
        val bodyColor: Int,
        val chipBgColor: Int,
        val chipTextColor: Int
    )

    private val salaryDay = 1

    private val knownSubscriptionRules = listOf(
        SubscriptionRule(
            label = "Education",
            keywords = listOf("school fee", "school fees", "tuition", "education")
        ),
        SubscriptionRule(
            label = "Housing",
            keywords = listOf("rent", "miete", "housing", "apartment"),
            cadence = BillingCadence.AFTER_SALARY
        ),
        SubscriptionRule(
            label = "Transport",
            keywords = listOf("transport", "mobility", "car share")
        ),
        SubscriptionRule(
            label = "Transit pass",
            keywords = listOf("transit pass", "rail", "bus pass", "ticket")
        ),
        SubscriptionRule(
            label = "Transfer",
            keywords = listOf("transferwise", "wise", "bank transfer", "remittance"),
            cadence = BillingCadence.AFTER_SALARY
        ),
        SubscriptionRule(
            label = "Activities",
            keywords = listOf("activity fee", "club fee", "dance fee")
        ),
        SubscriptionRule(
            label = "Mobile",
            keywords = listOf("mobile", "carrier", "sim")
        ),
        SubscriptionRule(
            label = "Software",
            keywords = listOf("software", "cloud storage", "ai tool", "workspace")
        ),
        SubscriptionRule(
            label = "Internet / Electricity",
            keywords = listOf("internet", "electricity", "utilities", "power")
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_today, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        view.findViewById<TextView>(R.id.text_date_pill).text = dateFormat.format(Date())

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        view.findViewById<TextView>(R.id.text_greeting).text = greeting

        setupNavigationActions(view)
        setupCollapsiblePanel(
            header = view.findViewById(R.id.panel_meals_header),
            content = view.findViewById(R.id.panel_meals_content),
            chevron = view.findViewById(R.id.text_meals_chevron),
            initiallyExpanded = true
        )
        setupCollapsiblePanel(
            header = view.findViewById(R.id.panel_due_header),
            content = view.findViewById(R.id.panel_due_content),
            chevron = view.findViewById(R.id.text_due_chevron),
            initiallyExpanded = true
        )
        setupBudgetSlider(view)

        lifecycleScope.launch {
            bindMealWeightSummary(view)
            bindExpenseSummary(view)
        }
    }

    private fun setupNavigationActions(root: View) {
        val toWallet = View.OnClickListener {
            lifecycleScope.launch {
                DashboardPrefs.setWalletQuickFilter(requireContext(), "Grocery", "")
                findNavController().navigate(R.id.expensesFragment)
            }
        }
        val toMeals = View.OnClickListener { findNavController().navigate(R.id.mealsFragment) }
        val toHealth = View.OnClickListener { findNavController().navigate(R.id.healthFragment) }

        fun openWalletWithFilter(category: String, query: String) {
            lifecycleScope.launch {
                DashboardPrefs.setWalletQuickFilter(requireContext(), category, query)
                findNavController().navigate(R.id.expensesFragment)
            }
        }

        root.findViewById<View>(R.id.button_quick_wallet_top).setOnClickListener(toWallet)
        root.findViewById<View>(R.id.button_quick_meals_top).setOnClickListener(toMeals)
        root.findViewById<View>(R.id.button_quick_health_top).setOnClickListener(toHealth)

        root.findViewById<Button>(R.id.button_quick_wallet).setOnClickListener(toWallet)
        root.findViewById<Button>(R.id.button_quick_meals).setOnClickListener(toMeals)
        root.findViewById<Button>(R.id.text_full_day_link).setOnClickListener(toMeals)
        root.findViewById<Button>(R.id.button_quick_health).setOnClickListener(toHealth)

        root.findViewById<View>(R.id.card_category_grocery).setOnClickListener {
            openWalletWithFilter("Grocery", "")
        }
        root.findViewById<View>(R.id.card_category_travel).setOnClickListener {
            openWalletWithFilter("Travel", "")
        }
        root.findViewById<View>(R.id.card_category_shopping).setOnClickListener {
            openWalletWithFilter("Shopping", "")
        }
        root.findViewById<View>(R.id.card_category_dining).setOnClickListener {
            openWalletWithFilter("Eat out", "")
        }
    }

    private fun setupCollapsiblePanel(
        header: View,
        content: View,
        chevron: TextView,
        initiallyExpanded: Boolean
    ) {
        var expanded = initiallyExpanded
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        chevron.text = if (expanded) "v" else ">"

        header.setOnClickListener {
            expanded = !expanded
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            chevron.text = if (expanded) "v" else ">"
        }
    }

    private fun setupBudgetSlider(root: View) {
        val sliderContainer = root.findViewById<LinearLayout>(R.id.slider_container)
        val buttonToggle = root.findViewById<Button>(R.id.button_toggle_slider)
        val sliderValue = root.findViewById<TextView>(R.id.text_slider_value)
        val slider = root.findViewById<SeekBar>(R.id.seek_budget_slider)

        lifecycleScope.launch {
            val initialBudget = DashboardPrefs.getMonthlyBudget(requireContext())

            buttonToggle.setOnClickListener {
                val showing = sliderContainer.visibility == View.VISIBLE
                sliderContainer.visibility = if (showing) View.GONE else View.VISIBLE
                buttonToggle.text = if (showing) "Open budget slider" else "Close budget slider"
            }

            slider.progress = initialBudget
            sliderValue.text = "Preview budget: €$initialBudget"
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    sliderValue.text = "Preview budget: €$progress"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val budget = seekBar?.progress ?: initialBudget
                    lifecycleScope.launch {
                        DashboardPrefs.setMonthlyBudget(requireContext(), budget)
                        bindExpenseSummary(root)
                    }
                }
            })
        }
    }

    private suspend fun bindMealWeightSummary(root: View) {
        val weightSnapshot = DashboardPrefs.getWeightSnapshot(requireContext())
        val mealsSummary = DashboardPrefs.getMealsSummary(requireContext())

        if (weightSnapshot != null) {
            val delta = if (weightSnapshot.previousKg != null) {
                weightSnapshot.currentKg - weightSnapshot.previousKg
            } else {
                0.0
            }
            val sign = if (delta >= 0) "+" else ""
            root.findViewById<TextView>(R.id.text_weight_current).text =
                "Weight: ${"%.1f".format(weightSnapshot.currentKg)} kg"
            root.findViewById<TextView>(R.id.text_weight_delta).text =
                "Trend: $sign${"%.1f".format(delta)} kg (latest ${weightSnapshot.date})"
        }

        if (mealsSummary != null) {
            root.findViewById<TextView>(R.id.text_meals_today_count).text =
                "Meals today: ${mealsSummary.mealsToday} planned"
            root.findViewById<TextView>(R.id.text_next_meal).text =
                "Next meal: ${mealsSummary.nextMeal}"
        }
    }

    private suspend fun bindExpenseSummary(root: View) {
        val summary = computeExpenseSummary()
        val currency = NumberFormat.getCurrencyInstance(Locale.GERMANY)

        root.findViewById<TextView>(R.id.text_month_total_expenses).text = currency.format(summary.monthTotal)
        root.findViewById<TextView>(R.id.text_month_label).text = summary.monthLabel
        root.findViewById<TextView>(R.id.text_budget_remaining).text = currency.format(summary.budgetRemaining)
        root.findViewById<TextView>(R.id.text_budget_used_ratio).text = "${summary.budgetUsedPercent}% used"
        root.findViewById<ProgressBar>(R.id.progress_budget_usage).progress = summary.budgetUsedPercent

        val deltaPrefix = if (summary.monthDeltaPercent >= 0) "+" else ""
        root.findViewById<TextView>(R.id.text_month_change).text =
            "$deltaPrefix${"%.1f".format(summary.monthDeltaPercent)}% vs last month"
        root.findViewById<TextView>(R.id.text_month_change_sub).text =
            "${if (summary.monthDeltaAbsolute >= 0) "Spent" else "Saved"} ${currency.format(abs(summary.monthDeltaAbsolute))}"

        root.findViewById<TextView>(R.id.text_today_spend).text = "Today: ${currency.format(summary.todaySpend)}"
        root.findViewById<TextView>(R.id.text_top_category).text = "Top: ${summary.topCategory}"
        root.findViewById<TextView>(R.id.text_top_merchant).text = "Top merchant: ${summary.topMerchant}"
        root.findViewById<TextView>(R.id.text_expense_insight).text = summary.insight

        root.findViewById<TextView>(R.id.text_subs_due).text = "${summary.subscriptionsDueCount} subscriptions due"
        root.findViewById<TextView>(R.id.text_fees_due).text = "${summary.feesDueCount} fees due"

        root.findViewById<TextView>(R.id.text_cat_grocery).text = currency.format(summary.categoryBlocks["Grocery"] ?: 0.0)
        root.findViewById<TextView>(R.id.text_cat_travel).text = currency.format(summary.categoryBlocks["Travel"] ?: 0.0)
        root.findViewById<TextView>(R.id.text_cat_shopping).text = currency.format(summary.categoryBlocks["Shopping"] ?: 0.0)
        root.findViewById<TextView>(R.id.text_cat_dining).text = currency.format(summary.categoryBlocks["Eat out"] ?: 0.0)

        renderCategoryRows(root.findViewById(R.id.expense_category_rows_container), summary.categoryTotals, currency)
        renderRecentRows(root.findViewById(R.id.expense_recent_rows_container), summary.recentTransactions, currency)
        renderDueItems(root.findViewById(R.id.due_items_container), summary.dueItems, currency)
        renderSparkline(root.findViewById(R.id.expense_sparkline_container), summary.sparklineDailyTotals)
        renderDonut(
            donutView = root.findViewById(R.id.expense_donut_view),
            categoryTotals = summary.categoryTotals,
            totalView = root.findViewById(R.id.text_donut_total),
            currency = currency,
            monthTotal = summary.monthTotal
        )
    }

    private suspend fun computeExpenseSummary(): ExpenseSummary {
        val today = LocalDate.now()
        val thisMonth = YearMonth.from(today)
        val prevMonth = thisMonth.minusMonths(1)
        val dataLoader = WalletDataLoader(requireContext())
        val categorizer = TransactionCategorizer()

        val txns = WalletUserDataStore.loadMergedTransactions(
            requireContext(),
            dataLoader.loadTransactions()
        ).map { t ->
            val category = categorizer.classifyCategory(t.title, t.category)
            t.copy(category = category)
        }

        val cleanExpenses = txns.filter { it.amount < 0 && !it.excluded && !categorizer.shouldExclude(it.title) }
        val currentMonthExpenses = cleanExpenses.filter { YearMonth.from(it.date) == thisMonth }
        val previousMonthExpenses = cleanExpenses.filter { YearMonth.from(it.date) == prevMonth }
        val currentMonthExpensesNoTransfers = currentMonthExpenses.filter { !isTransferCategory(it.category) }

        val monthTotal = currentMonthExpenses.sumOf { abs(it.amount) }
        val prevTotal = previousMonthExpenses.sumOf { abs(it.amount) }
        val monthDeltaAbsolute = monthTotal - prevTotal
        val monthDeltaPercent = if (prevTotal > 0.0) (monthDeltaAbsolute / prevTotal) * 100.0 else 0.0
        val monthlyBudget = DashboardPrefs.getMonthlyBudget(requireContext()).toDouble()
        val budgetRemaining = max(0.0, monthlyBudget - monthTotal)
        val budgetUsedPercent = ((monthTotal / monthlyBudget) * 100.0).toInt().coerceIn(0, 100)

        val todaySpend = cleanExpenses.filter { it.date == today }.sumOf { abs(it.amount) }

        val categoryTotals = currentMonthExpensesNoTransfers
            .groupBy { it.category.ifBlank { "Other" } }
            .mapValues { (_, items) -> items.sumOf { abs(it.amount) } }
            .toList()
            .sortedByDescending { it.second }

        val merchantTotals = currentMonthExpensesNoTransfers
            .groupBy { normalizeMerchant(it.title) }
            .mapValues { (_, items) -> items.sumOf { abs(it.amount) } }

        val topCategory = categoryTotals.firstOrNull()?.first ?: "No expenses"
        val topMerchant = merchantTotals.maxByOrNull { it.value }?.key ?: "No merchant yet"

        val sparklineDailyTotals = (6 downTo 0).map { delta ->
            val date = today.minusDays(delta.toLong())
            cleanExpenses.filter { it.date == date }.sumOf { abs(it.amount) }
        }

        val categoryBlocks = mapOf(
            "Grocery" to currentMonthExpenses.filter { isGrocery(it) }.sumOf { abs(it.amount) },
            "Travel" to currentMonthExpenses.filter { isTravel(it) }.sumOf { abs(it.amount) },
            "Shopping" to currentMonthExpenses.filter { isShopping(it) }.sumOf { abs(it.amount) },
            "Eat out" to currentMonthExpenses.filter { isEatOut(it) }.sumOf { abs(it.amount) }
        )

        val dueWindowEnd = today.plusDays(21)

        val ruleBasedSubscriptions = knownSubscriptionRules.mapNotNull { rule ->
            val matching = cleanExpenses.filter { txn ->
                val low = txn.title.lowercase(Locale.getDefault())
                rule.keywords.any { keyword -> low.contains(keyword) }
            }.sortedBy { it.date }

            if (matching.isEmpty()) return@mapNotNull null

            val lastDate = matching.last().date
            val nextDue = when (rule.cadence) {
                BillingCadence.MONTHLY_30 -> lastDate.plusDays(30)
                BillingCadence.AFTER_SALARY -> nextSalaryDate(today)
            }

            if (nextDue.isBefore(today) || nextDue.isAfter(dueWindowEnd)) return@mapNotNull null

            DueItem(
                title = rule.label,
                amount = matching.takeLast(2).map { abs(it.amount) }.average(),
                dueDate = nextDue,
                type = "Subscription"
            )
        }

        // Strict mode: only user-approved explicit subscription rules are considered.
        val subscriptionDueItems = ruleBasedSubscriptions
            .distinctBy { it.title }

        val feeMarkers = listOf("fee", "gebuehr", "gebühr", "provision", "bank")
        val feesDueItems = currentMonthExpenses
            .filter { tx -> feeMarkers.any { tx.title.lowercase(Locale.getDefault()).contains(it) } }
            .take(3)
            .mapIndexed { index, tx ->
                DueItem(
                    title = "Banking fee",
                    amount = abs(tx.amount),
                    dueDate = today.plusDays((7 + index).toLong()),
                    type = "Fee"
                )
            }

        val upcomingExpenseFallback = listOf(
            DueItem("Utilities reserve", 120.0, today.plusDays(12), "Upcoming"),
            DueItem("Groceries top-up", 85.0, today.plusDays(5), "Upcoming")
        )

        val dueItems = (subscriptionDueItems + feesDueItems)
            .sortedBy { it.dueDate }
            .take(6)
            .ifEmpty { upcomingExpenseFallback }

        val subscriptionsDueCount = subscriptionDueItems.size
        val feesDueCount = feesDueItems.size

        val monthTotalNoTransfers = currentMonthExpensesNoTransfers.sumOf { abs(it.amount) }
        val topTwoPct = if (monthTotalNoTransfers > 0.0) {
            categoryTotals.take(2).sumOf { it.second } / monthTotalNoTransfers * 100.0
        } else {
            0.0
        }
        val insight = when {
            monthTotal == 0.0 -> "No expenses recorded this month yet. Add transactions in Wallet to unlock full intelligence."
            subscriptionsDueCount > 0 -> "$subscriptionsDueCount subscriptions are due soon. Review them before auto-debit dates."
            topTwoPct >= 60.0 -> "Top 2 categories are ${"%.0f".format(topTwoPct)}% of spending. Consider tightening one high-cost bucket."
            monthDeltaPercent > 10 -> "Spending is rising vs last month. Keep an eye on discretionary categories."
            else -> "Spending is balanced and on-track this month."
        }

        return ExpenseSummary(
            monthTotal = monthTotal,
            monthLabel = thisMonth.month.name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + thisMonth.year,
            budgetRemaining = budgetRemaining,
            budgetUsedPercent = budgetUsedPercent,
            monthDeltaPercent = monthDeltaPercent,
            monthDeltaAbsolute = monthDeltaAbsolute,
            todaySpend = todaySpend,
            topCategory = topCategory,
            topMerchant = topMerchant,
            categoryTotals = categoryTotals.take(5),
            categoryBlocks = categoryBlocks,
            recentTransactions = currentMonthExpenses.sortedByDescending { it.date }.take(5),
            sparklineDailyTotals = sparklineDailyTotals,
            subscriptionsDueCount = subscriptionsDueCount,
            feesDueCount = feesDueCount,
            dueItems = dueItems,
            insight = insight
        )
    }

    private fun renderCategoryRows(
        container: LinearLayout,
        categoryTotals: List<Pair<String, Double>>,
        currency: NumberFormat
    ) {
        container.removeAllViews()
        if (categoryTotals.isEmpty()) {
            container.addView(createSimpleRow("No category data", ""))
            return
        }

        categoryTotals.take(4).forEach { (name, amount) ->
            container.addView(createCategoryRow(name, currency.format(amount)))
        }
    }

    private fun renderRecentRows(
        container: LinearLayout,
        transactions: List<WalletDataLoader.WalletTransaction>,
        currency: NumberFormat
    ) {
        container.removeAllViews()
        if (transactions.isEmpty()) {
            container.addView(createSimpleRow("No recent expenses", ""))
            return
        }

        transactions.forEach { txn ->
            container.addView(createSimpleRow(normalizeMerchant(txn.title), currency.format(abs(txn.amount))))
        }
    }

    private fun renderDueItems(container: LinearLayout, items: List<DueItem>, currency: NumberFormat) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(createSimpleRow("No due items", ""))
            return
        }

        items.forEach { item ->
            container.addView(createDueItemCard(item, currency))
        }
    }

    private fun createDueItemCard(item: DueItem, currency: NumberFormat): View {
        val style = resolveDueStyle(item.type)
        val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), item.dueDate)
        val status = if (daysLeft >= 0) {
            "in ${daysLeft}d"
        } else {
            "overdue ${kotlin.math.abs(daysLeft)}d"
        }

        val row = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = ContextCompat.getDrawable(requireContext(), style.cardDrawable)
        }

        val top = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val chip = TextView(requireContext()).apply {
            text = item.type.uppercase(Locale.getDefault())
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(8, 3, 8, 3)
            setTextColor(style.chipTextColor)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_orange)?.mutate()?.apply {
                setTint(style.chipBgColor)
            }
        }

        val dueDate = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }
            text = item.dueDate.format(DateTimeFormatter.ofPattern("dd MMM")) + "  ·  " + status
            textSize = 11f
            setTextColor(style.bodyColor)
            gravity = Gravity.END
        }

        val title = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            text = item.title
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(style.titleColor)
        }

        val amount = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 2 }
            text = currency.format(item.amount)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(style.titleColor)
        }

        top.addView(chip)
        top.addView(dueDate)
        row.addView(top)
        row.addView(title)
        row.addView(amount)
        return row
    }

    private fun resolveDueStyle(type: String): DueVisualStyle {
        val t = type.lowercase(Locale.getDefault())
        return when {
            t.contains("subscription") -> DueVisualStyle(
                cardDrawable = R.drawable.bg_due_card_subscription,
                titleColor = ContextCompat.getColor(requireContext(), R.color.green_dark),
                bodyColor = ContextCompat.getColor(requireContext(), R.color.text_secondary),
                chipBgColor = ContextCompat.getColor(requireContext(), R.color.green_soft),
                chipTextColor = ContextCompat.getColor(requireContext(), R.color.green_dark)
            )
            t.contains("fee") -> DueVisualStyle(
                cardDrawable = R.drawable.bg_due_card_fee,
                titleColor = ContextCompat.getColor(requireContext(), R.color.orange),
                bodyColor = ContextCompat.getColor(requireContext(), R.color.text_secondary),
                chipBgColor = ContextCompat.getColor(requireContext(), R.color.orange_soft),
                chipTextColor = ContextCompat.getColor(requireContext(), R.color.orange)
            )
            else -> DueVisualStyle(
                cardDrawable = R.drawable.bg_due_card_upcoming,
                titleColor = ContextCompat.getColor(requireContext(), R.color.purple),
                bodyColor = ContextCompat.getColor(requireContext(), R.color.text_secondary),
                chipBgColor = ContextCompat.getColor(requireContext(), R.color.purple_soft),
                chipTextColor = ContextCompat.getColor(requireContext(), R.color.purple)
            )
        }
    }

    private fun renderSparkline(container: LinearLayout, dailyTotals: List<Double>) {
        container.removeAllViews()
        if (dailyTotals.isEmpty()) return

        val maxVal = dailyTotals.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        dailyTotals.forEach { value ->
            val bar = View(requireContext()).apply {
                val height = (14 + ((value / maxVal) * 42)).toInt().coerceIn(14, 56)
                layoutParams = LinearLayout.LayoutParams(0, height, 1f).apply {
                    marginEnd = 4
                    gravity = Gravity.BOTTOM
                }
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_sparkline_bar)
            }
            container.addView(bar)
        }
    }

    private fun renderDonut(
        donutView: SpendingDonutView,
        categoryTotals: List<Pair<String, Double>>,
        totalView: TextView,
        currency: NumberFormat,
        monthTotal: Double
    ) {
        val segments = categoryTotals.take(4).map {
            val color = resolveCategoryColor(it.first)
            it.second.toFloat() to color
        }
        donutView.setSegments(segments)
        totalView.text = currency.format(monthTotal)
    }

    private fun createCategoryRow(name: String, amount: String): View {
        val row = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val dot = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(10, 10).apply { marginEnd = 8 }
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_category_dot)
            background.setTint(resolveCategoryColor(name))
        }

        val left = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "$name"
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 11f
        }

        val right = TextView(requireContext()).apply {
            text = amount
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 11f
        }

        row.addView(dot)
        row.addView(left)
        row.addView(right)
        return row
    }

    private fun createSimpleRow(left: String, right: String): View {
        val row = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 8, 10, 8)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_wallet_card)
        }

        val leftView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = left
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 11f
        }

        val rightView = TextView(requireContext()).apply {
            text = right
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 11f
        }

        row.addView(leftView)
        row.addView(rightView)
        return row
    }

    private fun normalizeMerchant(raw: String): String {
        if (raw.isBlank()) return "Unknown merchant"
        val cleaned = raw
            .replace(Regex("\\d+"), " ")
            .replace(Regex("[^A-Za-z& ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .uppercase(Locale.getDefault())

        return when {
            cleaned.contains("REWE") -> "REWE"
            cleaned.contains("EDEKA") -> "EDEKA"
            cleaned.contains("LIDL") -> "LIDL"
            cleaned.contains("ALDI") -> "ALDI"
            cleaned.contains("PAYPAL") -> "PAYPAL"
            cleaned.contains("AMAZON") -> "AMAZON"
            cleaned.contains("DEUTSCHE BAHN") || cleaned.contains(" DB ") -> "DEUTSCHE BAHN"
            cleaned.length < 3 -> "Unknown merchant"
            else -> cleaned
        }
    }

    private fun isEatOut(txn: WalletDataLoader.WalletTransaction): Boolean {
        return txn.category.equals("Eat out", ignoreCase = true)
    }

    private fun isGrocery(txn: WalletDataLoader.WalletTransaction): Boolean {
        return txn.category.equals("Grocery", ignoreCase = true)
    }

    private fun isTravel(txn: WalletDataLoader.WalletTransaction): Boolean {
        return txn.category.equals("Travel", ignoreCase = true)
    }

    private fun isShopping(txn: WalletDataLoader.WalletTransaction): Boolean {
        return txn.category.equals("Shopping", ignoreCase = true)
    }

    private fun isTransferCategory(category: String): Boolean {
        val c = category.lowercase(Locale.getDefault())
        return c.contains("transfer")
    }

    private fun nextSalaryDate(today: LocalDate): LocalDate {
        val currentMonth = YearMonth.from(today)
        val thisMonthDay = salaryDay.coerceAtMost(currentMonth.lengthOfMonth())
        val thisMonthSalaryDate = currentMonth.atDay(thisMonthDay)
        if (thisMonthSalaryDate.isAfter(today)) {
            return thisMonthSalaryDate
        }
        val nextMonth = currentMonth.plusMonths(1)
        val nextMonthDay = salaryDay.coerceAtMost(nextMonth.lengthOfMonth())
        return nextMonth.atDay(nextMonthDay)
    }

    private fun resolveCategoryColor(category: String): Int {
        val c = category.lowercase(Locale.getDefault())
        return when {
            c.contains("food") || c.contains("dining") -> ContextCompat.getColor(requireContext(), R.color.orange)
            c.contains("shop") -> ContextCompat.getColor(requireContext(), R.color.purple)
            c.contains("transport") || c.contains("travel") -> ContextCompat.getColor(requireContext(), R.color.primary_blue)
            c.contains("utility") -> ContextCompat.getColor(requireContext(), R.color.green)
            c.contains("health") -> ContextCompat.getColor(requireContext(), R.color.health_start)
            c.contains("salary") || c.contains("income") -> ContextCompat.getColor(requireContext(), R.color.green_dark)
            else -> ContextCompat.getColor(requireContext(), R.color.text_secondary)
        }
    }
}
