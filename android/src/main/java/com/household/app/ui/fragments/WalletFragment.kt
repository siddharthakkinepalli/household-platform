package com.household.app.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.household.app.R
import com.household.app.data.DashboardPrefs
import com.household.app.data.TransactionCategorizer
import com.household.app.data.WalletDataLoader
import com.household.app.data.WalletUserDataStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

class WalletFragment : Fragment() {

    private data class Trip(val name: String, val budget: Double)

    private data class WalletTxn(
        val id: Int,
        val title: String,
        var category: String,
        val amount: Double,
        val date: LocalDate,
        val paymentType: String,
        var trip: String? = null,
        val note: String = "",
        val bankName: String = "",
        var excluded: Boolean = false
    )

    private lateinit var textSalaryCycle: TextView
    private lateinit var textSpent: TextView
    private lateinit var textRemaining: TextView
    private lateinit var spinnerCycleMonth: Spinner
    private lateinit var spinnerCycleYear: Spinner
    private lateinit var editTransactionSearch: EditText
    private lateinit var textActiveFilters: TextView
    private lateinit var buttonOpenExpenseForm: Button
    private lateinit var tripsContainer: LinearLayout
    private lateinit var transactionsContainer: LinearLayout

    private var selectedCategoryFilter: String = "All"
    private var selectedCycleYearMonth: YearMonth = YearMonth.now()
    private var isCycleSelectorReady: Boolean = false
    private var searchQuery: String = ""

    private val salaryDay = 1
    private val cycleBudget = 2400.0
    private lateinit var dataLoader: WalletDataLoader
    private lateinit var categorizer: TransactionCategorizer
    private val webFilterCategories by lazy {
        categorizer.getAllCategories().filter { it != "All" && it != "Excluded" }
    }
    private val webCategorySet by lazy {
        categorizer.getAllCategories().filter { it != "All" && it != "Excluded" }.toSet()
    }

    private val trips = mutableListOf<Trip>()
    private val transactions = mutableListOf<WalletTxn>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_wallet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dataLoader = WalletDataLoader(requireContext())
        categorizer = TransactionCategorizer()

        textSalaryCycle = view.findViewById(R.id.text_salary_cycle)
        textSpent = view.findViewById(R.id.text_wallet_spent)
        textRemaining = view.findViewById(R.id.text_wallet_remaining)
        spinnerCycleMonth = view.findViewById(R.id.spinner_cycle_month)
        spinnerCycleYear = view.findViewById(R.id.spinner_cycle_year)
        editTransactionSearch = view.findViewById(R.id.edit_transaction_search)
        textActiveFilters = view.findViewById(R.id.text_active_filters)
        buttonOpenExpenseForm = view.findViewById(R.id.button_open_expense_form)
        tripsContainer = view.findViewById(R.id.trips_container)
        transactionsContainer = view.findViewById(R.id.transactions_container)

        // Load data from database asynchronously
        lifecycleScope.launch {
            loadWalletData()
            setupCycleSelectors()
            setupCategoryFilterButtons(view)

            view.findViewById<Button>(R.id.button_add_trip).setOnClickListener { showAddTripDialog() }
            buttonOpenExpenseForm.setOnClickListener { showAddExpenseBottomSheet() }
            editTransactionSearch.doAfterTextChanged {
                searchQuery = it?.toString().orEmpty().trim().lowercase(Locale.getDefault())
                refreshWalletView()
            }

            applyQuickFilterFromToday()

            renderTrips()
            refreshWalletView()
        }
    }

    private suspend fun applyQuickFilterFromToday() {
        val quickFilter = DashboardPrefs.consumeWalletQuickFilter(requireContext()) ?: return
        selectedCategoryFilter = quickFilter.category
        searchQuery = quickFilter.query.lowercase(Locale.getDefault())
        editTransactionSearch.setText(quickFilter.query)
    }

    private suspend fun loadWalletData() {
        trips.clear()
        transactions.clear()

        // Merge embedded export + user imports/overrides.
        val loadedTransactions = WalletUserDataStore.loadMergedTransactions(
            requireContext(),
            dataLoader.loadTransactions()
        )
        transactions.addAll(loadedTransactions.map { dt ->
            val refined = categorizer.classifyCategory(dt.title, dt.category)
            val shouldExclude = categorizer.shouldExclude(dt.title)
            WalletTxn(
                id = dt.id,
                title = dt.title,
                category = refined,
                amount = dt.amount,
                date = dt.date,
                paymentType = dt.paymentType,
                trip = dt.trip,
                note = dt.note,
                bankName = dt.bankName,
                excluded = dt.excluded || shouldExclude
            )
        })
        
        // Load trips from export + user-created trips.
        val loadedTrips = WalletUserDataStore.loadMergedTrips(
            requireContext(),
            dataLoader.loadTrips()
        )
        trips.addAll(loadedTrips.map { Trip(it.name, it.budget) })
    }

    private fun setupCycleSelectors() {
        val locale = Locale.getDefault()
        val months = (1..12).map { monthNumber ->
            Month.of(monthNumber).getDisplayName(TextStyle.FULL, locale)
        }
        spinnerCycleMonth.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            months
        )

        val minYear = transactions.minOfOrNull { it.date.year } ?: LocalDate.now().year
        val maxYear = transactions.maxOfOrNull { it.date.year } ?: LocalDate.now().year
        val yearStart = minOf(minYear, LocalDate.now().year - 1)
        val yearEnd = maxOf(maxYear, LocalDate.now().year + 1)
        val years = (yearStart..yearEnd).toList()
        spinnerCycleYear.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            years
        )

        val defaultCycle = resolveDefaultCycleStartMonth()
        selectedCycleYearMonth = defaultCycle
        spinnerCycleMonth.setSelection(defaultCycle.monthValue - 1)
        val yearIndex = years.indexOf(defaultCycle.year).takeIf { it >= 0 } ?: 0
        spinnerCycleYear.setSelection(yearIndex)

        val listener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isCycleSelectorReady) return
                val selectedYear = spinnerCycleYear.selectedItem as? Int ?: return
                val selectedMonth = spinnerCycleMonth.selectedItemPosition + 1
                selectedCycleYearMonth = YearMonth.of(selectedYear, selectedMonth)
                refreshWalletView()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        spinnerCycleMonth.onItemSelectedListener = listener
        spinnerCycleYear.onItemSelectedListener = listener
        isCycleSelectorReady = true
    }



    private fun setupCategoryFilterButtons(root: View) {
        val categoryFilterRow = root.findViewById<LinearLayout>(R.id.category_filter_row)
        categoryFilterRow.removeAllViews()

        // Use canonical Expenses web categories for consistent filtering behavior.
        val allCategories = buildList {
            add("All")
            addAll(webFilterCategories)
            if (!contains("Other")) add("Other")
            add("Excluded")
        }

        allCategories.forEach { category ->
            val button = Button(requireContext()).apply {
                text = category
                setBackgroundResource(android.R.drawable.btn_default)
                setOnClickListener {
                    selectedCategoryFilter = category
                    refreshWalletView()
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = 8
                layoutParams = params
            }
            categoryFilterRow.addView(button)
        }
    }

    private fun showAddExpenseBottomSheet() {
        val categories = webFilterCategories.filter { it != "Excluded" }
        val tripOptions = listOf("No trip") + trips.map { it.name }

        val bottomSheet = AddExpenseBottomSheetFragment(
            categories = categories,
            tripOptions = tripOptions
        ) { entry ->
            lifecycleScope.launch {
                transactions.add(
                    WalletTxn(
                        id = WalletUserDataStore.nextUserTransactionId(
                            requireContext(),
                            transactions.maxOfOrNull { it.id } ?: 0
                        ),
                        title = if (entry.note.isBlank()) "Manual expense" else entry.note,
                        category = entry.category,
                        amount = -abs(entry.amount),
                        date = LocalDate.now(),
                        paymentType = entry.paymentType,
                        trip = entry.trip,
                        note = entry.note,
                        bankName = "Manual"
                    )
                )
                WalletUserDataStore.appendImportedTransactions(
                    requireContext(),
                    transactions.takeLast(1).map {
                        WalletDataLoader.WalletTransaction(
                            id = it.id,
                            title = it.title,
                            category = it.category,
                            amount = it.amount,
                            date = it.date,
                            paymentType = it.paymentType,
                            trip = it.trip,
                            note = it.note,
                            bankName = it.bankName,
                            excluded = it.excluded
                        )
                    }
                )
                refreshWalletView()
                setupCategoryFilterButtons(requireView())
                Toast.makeText(requireContext(), "Expense added", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheet.show(childFragmentManager, "AddExpenseBottomSheet")
    }

    private fun showAddTripDialog() {
        val inputLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val tripNameInput = EditText(requireContext()).apply {
            hint = "Trip name"
        }
        val tripBudgetInput = EditText(requireContext()).apply {
            hint = "Trip budget"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        inputLayout.addView(tripNameInput)
        inputLayout.addView(tripBudgetInput)

        AlertDialog.Builder(requireContext())
            .setTitle("Add trip")
            .setView(inputLayout)
            .setPositiveButton("Save") { _, _ ->
                val name = tripNameInput.text?.toString().orEmpty().trim()
                val budget = tripBudgetInput.text?.toString()?.toDoubleOrNull() ?: 0.0
                if (name.isNotBlank()) {
                    lifecycleScope.launch {
                        trips.add(Trip(name, budget))
                        WalletUserDataStore.appendUserTrips(
                            requireContext(),
                            listOf(WalletDataLoader.WalletTrip(name, budget))
                        )
                        renderTrips()
                        refreshWalletView()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderTrips() {
        tripsContainer.removeAllViews()
        if (trips.isEmpty()) {
            tripsContainer.addView(simpleLabel("No trips yet"))
            return
        }

        trips.forEach { trip ->
            val tripSpend = transactions
                .filter { it.trip == trip.name }
                .sumOf { kotlin.math.min(it.amount, 0.0) }

            val summary = "${trip.name}  •  Budget €${"%.2f".format(trip.budget)}  •  Spent €${"%.2f".format(abs(tripSpend))}"
            tripsContainer.addView(cardLabel(summary))
        }
    }

    private fun refreshWalletView() {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
        val cycleRange = resolveCycleRange(selectedCycleYearMonth)
        textSalaryCycle.text = "${cycleRange.first.format(formatter)} - ${cycleRange.second.format(formatter)}"

        val filtered = transactions.filter { txn ->
            val inCycle = !txn.date.isBefore(cycleRange.first) && !txn.date.isAfter(cycleRange.second)
            val filterCategory = toFilterCategory(txn)
            val inCategory = when (selectedCategoryFilter) {
                "All" -> !txn.excluded
                "Excluded" -> txn.excluded
                "Other" -> filterCategory == "Other" && !txn.excluded
                else -> filterCategory.equals(selectedCategoryFilter, ignoreCase = true) && !txn.excluded
            }
            val normalized = normalizeMerchant(txn.title).lowercase(Locale.getDefault())
            val searchTokens = searchQuery.split(" ").map { it.trim() }.filter { it.isNotBlank() }
            val inSearch = searchTokens.isEmpty() || searchTokens.any { token ->
                txn.title.lowercase(Locale.getDefault()).contains(token) ||
                    normalized.contains(token) ||
                    txn.category.lowercase(Locale.getDefault()).contains(token) ||
                    filterCategory.lowercase(Locale.getDefault()).contains(token)
            }
            inCycle && inCategory && inSearch
        }.sortedByDescending { it.date }

        val queryText = if (searchQuery.isBlank()) "none" else searchQuery
        val cycleLabel = selectedCycleYearMonth.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
        textActiveFilters.text = "Filter: $cycleLabel · $selectedCategoryFilter · Search: $queryText"

        val spend = filtered.filter { it.amount < 0 }.sumOf { abs(it.amount) }
        textSpent.text = "€ ${"%.2f".format(spend)}"
        textRemaining.text = "Remaining budget: € ${"%.2f".format(cycleBudget - spend)}"

        transactionsContainer.removeAllViews()
        if (filtered.isEmpty()) {
            transactionsContainer.addView(simpleLabel("No transactions in this filter"))
        } else {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val dateHeaderFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

            val grouped = filtered
                .groupBy { it.date }
                .entries
                .sortedByDescending { it.key }

            grouped.forEach { (date, dayTxns) ->
                val dayLabel = when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> date.format(dateHeaderFormatter)
                }
                val daySpend = dayTxns.filter { it.amount < 0 }.sumOf { abs(it.amount) }
                val dayIncome = dayTxns.filter { it.amount > 0 }.sumOf { it.amount }
                val headerSuffix = buildString {
                    if (daySpend > 0) append("-€${"%.2f".format(daySpend)}")
                    if (dayIncome > 0) {
                        if (daySpend > 0) append("  ")
                        append("+€${"%.2f".format(dayIncome)}")
                    }
                }
                transactionsContainer.addView(dateHeaderLabel("$dayLabel  ·  $headerSuffix"))

                dayTxns.forEach { txn ->
                    val amountPrefix = if (txn.amount < 0) "-" else "+"
                    val normalizedMerchant = normalizeMerchant(txn.title)
                    val confidence = confidenceLabel(txn.title, txn.category)
                    val recurring = recurringLabel(txn, transactions)
                    val line1 = "Merchant: $normalizedMerchant  $amountPrefix€${"%.2f".format(abs(txn.amount))}"
                    val correction = if (!txn.title.equals(normalizedMerchant, ignoreCase = true)) {
                        "(Corrected) ${txn.title} -> $normalizedMerchant"
                    } else {
                        "(Matched) ${txn.title}"
                    }
                    val tripPart = if (txn.trip.isNullOrBlank()) "" else " · trip: ${txn.trip}"
                    val bankPart = if (txn.bankName.isBlank()) "" else " · ${txn.bankName}"
                    val line2 = "$correction\n${txn.category} · ${txn.paymentType}$bankPart · ${txn.date}$tripPart"
                    val line3 = "Recurring: $recurring · Confidence: $confidence"
                    val excludeStatus = if (txn.excluded) " [EXCLUDED]" else ""
                    val cardView = cardLabel("$line1\n$line2\n$line3$excludeStatus", txn)
                    transactionsContainer.addView(cardView)
                }
            }
        }

        renderTrips()
    }

    private fun resolveCycleRange(cycleStartMonth: YearMonth): Pair<LocalDate, LocalDate> {
        val safeStartDay = salaryDay.coerceIn(1, cycleStartMonth.lengthOfMonth())
        val nextMonth = cycleStartMonth.plusMonths(1)
        val safeNextStartDay = salaryDay.coerceIn(1, nextMonth.lengthOfMonth())

        val start = cycleStartMonth.atDay(safeStartDay)
        val nextStart = nextMonth.atDay(safeNextStartDay)
        return start to nextStart.minusDays(1)
    }

    private fun resolveDefaultCycleStartMonth(): YearMonth {
        val today = LocalDate.now()
        return if (today.dayOfMonth >= salaryDay) {
            YearMonth.from(today)
        } else {
            YearMonth.from(today.minusMonths(1))
        }
    }

    private fun cardLabel(text: String, txn: WalletTxn? = null): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setBackgroundResource(R.drawable.bg_wallet_card)
            setTextColor(resources.getColor(android.R.color.white, null))
            setPadding(22, 18, 22, 18)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            layoutParams = params

            // Add long-click context menu for exclusion
            if (txn != null) {
                isLongClickable = true
                setOnLongClickListener {
                    showTransactionMenu(txn)
                    true
                }
            }
        }
    }

    private fun showTransactionMenu(txn: WalletTxn) {
        val options = mutableListOf<String>()
        if (txn.excluded) {
            options.add("Include transaction")
        } else {
            options.add("Exclude transaction")
        }
        options.add("Move to category")
        options.add("Move to trip")

        AlertDialog.Builder(requireContext())
            .setTitle(txn.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Include transaction", "Exclude transaction" -> {
                        txn.excluded = !txn.excluded
                        lifecycleScope.launch {
                            WalletUserDataStore.setTransactionExcluded(requireContext(), txn.id, txn.excluded)
                            refreshWalletView()
                        }
                    }
                    "Move to category" -> showMoveCategoryDialog(txn)
                    "Move to trip" -> showMoveTripDialog(txn)
                }
            }
            .show()
    }

    private fun showMoveCategoryDialog(txn: WalletTxn) {
        val categories = webFilterCategories.filter { it != "Excluded" }

        AlertDialog.Builder(requireContext())
            .setTitle("Move to category")
            .setItems(categories.toTypedArray()) { _, which ->
                val chosen = categories[which]
                txn.category = chosen
                lifecycleScope.launch {
                    WalletUserDataStore.setCategoryOverride(requireContext(), txn.id, chosen)
                    setupCategoryFilterButtons(requireView())
                    refreshWalletView()
                }
            }
            .show()
    }

    private fun toFilterCategory(txn: WalletTxn): String {
        val raw = txn.category.trim()
        if (raw.isBlank()) {
            return categorizer.classifyCategory(txn.title, "")
        }

        if (raw in webCategorySet) {
            return raw
        }

        val lowered = raw.lowercase(Locale.getDefault())
        return when {
            lowered.contains("food") || lowered.contains("dining") || lowered.contains("grocery") -> "Food & Dining"
            lowered.contains("travel") || lowered.contains("transport") -> "Transportation"
            lowered.contains("utility") || lowered.contains("bill") -> "Utilities"
            lowered.contains("entertain") || lowered.contains("movie") || lowered.contains("music") -> "Entertainment"
            lowered.contains("shop") || lowered.contains("fashion") -> "Shopping"
            lowered.contains("health") || lowered.contains("fit") || lowered.contains("medical") -> "Health & Fitness"
            lowered.contains("salary") || lowered.contains("income") -> "Salary/Income"
            lowered.contains("housing") || lowered.contains("property") || lowered.contains("rent") -> "Housing & Property"
            lowered.contains("transfer") || lowered.contains("wise") || lowered.contains("revolut") -> "Transfers"
            lowered.contains("cash") || lowered.contains("atm") -> "Cash"
            lowered.contains("government") || lowered.contains("benefit") || lowered.contains("tax") -> "Government & Benefits"
            lowered.contains("bank") || lowered.contains("fee") || lowered.contains("gebu") || lowered.contains("provision") -> "Banking & Fees"
            else -> {
                val byKeywords = categorizer.classifyCategory(txn.title, "")
                if (byKeywords == "Other" || byKeywords.isBlank()) "Other" else byKeywords
            }
        }
    }

    private fun showMoveTripDialog(txn: WalletTxn) {
        val tripOptions = listOf("No trip") + trips.map { it.name }
        AlertDialog.Builder(requireContext())
            .setTitle("Move to trip")
            .setItems(tripOptions.toTypedArray()) { _, which ->
                val chosen = if (which == 0) null else tripOptions[which]
                txn.trip = chosen
                lifecycleScope.launch {
                    WalletUserDataStore.setTripOverride(requireContext(), txn.id, chosen)
                    refreshWalletView()
                }
            }
            .show()
    }

    private fun normalizeMerchant(raw: String): String {
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
            cleaned.length < 3 -> "UNKNOWN"
            else -> cleaned
        }
    }

    private fun recurringLabel(txn: WalletTxn, allTxns: List<WalletTxn>): String {
        val titleLower = txn.title.lowercase(Locale.getDefault())
        val knownSubscriptionKeywords = listOf(
            "school fee", "school fees", "ess fee", "esses fee",
            "rent", "miete", "lindner", "immobilien",
            "conficar", "confitech",
            "deutschlandticket", "deutschland ticket", "db vertri", "deutsche bahn",
            "transferwise", "wise", "india transfer", "chithra", "siddharth",
            "rashmi bijegatte", "dance fee",
            "lycamobile", "lyca",
            "claude", "anthropic", "google subscription", "google one",
            "telekom", "lekker", "electricity", "strom"
        )
        if (knownSubscriptionKeywords.any { titleLower.contains(it) }) {
            return "Subscription"
        }
        return "No"
    }

    private fun confidenceLabel(title: String, category: String): String {
        val normalized = normalizeMerchant(title)
        val hasStrongMerchant = normalized != "UNKNOWN" && normalized.length > 3
        val knownCategory = category != "Other"
        return when {
            hasStrongMerchant && knownCategory -> "High"
            hasStrongMerchant || knownCategory -> "Medium"
            else -> "Low"
        }
    }

    private fun dateHeaderLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(0xFFB39DDB.toInt()) // soft lavender
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 20
            params.bottomMargin = 6
            layoutParams = params
            setPadding(4, 0, 4, 0)
        }
    }

    private fun simpleLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(android.R.color.white, null))
            gravity = Gravity.START
            setPadding(8, 8, 8, 8)
        }
    }
}
