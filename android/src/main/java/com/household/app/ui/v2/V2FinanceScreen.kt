package com.household.app.ui.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.data.AppDatabase
import com.household.app.data.entities.TaxTagEntity
import com.household.app.data.entities.WalletTripEntity
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.viewmodels.CategorySparkline
import com.household.app.ui.viewmodels.CategorySummary
import com.household.app.ui.viewmodels.ExpensesViewModel
import com.household.app.ui.viewmodels.FinancialAlert
import com.household.app.ui.viewmodels.PulseData
import com.household.app.ui.viewmodels.PulseStatus
import com.household.app.ui.viewmodels.SalaryAllocationData
import com.household.app.ui.viewmodels.MonthlySpend
import com.household.app.ui.viewmodels.Transaction
import kotlin.math.abs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle as JTimeTextStyle
import java.util.Locale

private data class CategoryBlockUi(
    val name: String,
    val spent: Double,
    val limit: Float,
    val color: Color,
    val icon: ImageVector
)

@Composable
fun V2FinanceScreen(
    viewModel: ExpensesViewModel = viewModel(),
    onNavigateToTrips: () -> Unit = {}
) {
    // Refresh whenever this screen comes to the foreground (e.g. after a CSV import)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTransactions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val transactions by viewModel.recentTransactions.observeAsState(emptyList())
    val categorySummary by viewModel.categorySummary.observeAsState(emptyList())
    val selectedCategory by viewModel.selectedCategory.observeAsState("All")
    val selectedTimeFilter by viewModel.selectedTimeFilter.observeAsState("Current Cycle")
    val customYearMonth by viewModel.customYearMonth.observeAsState(null)
    val activePeriodLabel by viewModel.activePeriodLabel.observeAsState("Current cycle")
    val householdPulse by viewModel.householdPulse.observeAsState()
    val categoryLimits by viewModel.categoryLimits.observeAsState(emptyMap())
    val salaryAllocation by viewModel.salaryAllocation.observeAsState()
    val incomeTransactions by viewModel.incomeTransactions.observeAsState(emptyList())
    val categorySparklines by viewModel.categorySparklines.observeAsState(emptyList())
    var editingTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val ftsResults by viewModel.ftsResults.collectAsState()
    val spendingTrends by viewModel.spendingTrends.collectAsState()
    val smartAlerts by viewModel.smartAlerts.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(searchQuery) {
        viewModel.searchFts(searchQuery)
    }

    val editingTransaction = remember(editingTransactionId, transactions) {
        transactions.firstOrNull { it.id == editingTransactionId }
    }

    val categoryGrid = remember(categorySummary, categoryLimits) {
        listOf(
            CategoryBlockUi("Groceries", categoryAmountFor(categorySummary, "Groceries"), categoryLimits["Groceries"] ?: 600f, Color(0xFF14B8A6), Icons.Rounded.ShoppingCart),
            CategoryBlockUi("Dining",    categoryAmountFor(categorySummary, "Dining"),    categoryLimits["Dining"]    ?: 100f, Color(0xFFF59E0B), Icons.Rounded.Restaurant),
            CategoryBlockUi("Travel",    categoryAmountFor(categorySummary, "Travel"),    categoryLimits["Travel"]    ?: 195f, Color(0xFF3B82F6), Icons.Rounded.Flight),
            CategoryBlockUi("Shopping",  categoryAmountFor(categorySummary, "Shopping"),  categoryLimits["Shopping"]  ?: 100f, Color(0xFFEC4899), Icons.Rounded.CardGiftcard)
        )
    }

    val filteredTransactions = remember(transactions, selectedCategory) {
        val visible = transactions.filterNot { it.category.equals("Excluded", ignoreCase = true) }
        if (selectedCategory == "All") {
            visible
        } else {
            visible.filter { canonicalCategory(it.category) == selectedCategory }
        }
    }

    val searchFilteredTransactions: List<Transaction> = remember(filteredTransactions, ftsResults, searchQuery) {
        when {
            searchQuery.isBlank() -> filteredTransactions
            ftsResults != null -> ftsResults!!
            else -> filteredTransactions.filter { it.description.contains(searchQuery, ignoreCase = true) }
        }
    }

    val groupedTransactions = remember(searchFilteredTransactions) {
        searchFilteredTransactions.groupBy { it.date.ifBlank { "Unknown Date" } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(LumePurple.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(size.width * 0.78f, size.height * 0.16f),
                        radius = size.width * 0.58f
                    ),
                    center = Offset(size.width * 0.78f, size.height * 0.16f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(LumePurple.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.18f),
                        radius = size.width * 0.34f
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.18f)
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wallet",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextMain
                        )
                        Text(
                            text = activePeriodLabel,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(LumeCyan.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                            .clickable(onClick = onNavigateToTrips)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Flight,
                                contentDescription = null,
                                tint = LumeCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Trips",
                                color = LumeCyan,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Transactions", "Trends").forEachIndexed { idx, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTab == idx) LumePurple.copy(alpha = 0.25f)
                                    else Color.Transparent,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedTab = idx }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == idx) LumePurple else TextSecondary
                            )
                        }
                    }
                }
            }

            if (selectedTab == 1) {
                item { SpendingTrendsContent(spendingTrends) }
            }

            if (selectedTab == 0) {
            item {
                salaryAllocation?.let { SalaryAllocationCard(it) }
            }

            item {
                SmartAlertFeed(smartAlerts)
            }

            item {
                householdPulse?.let { HouseholdPulseCard(it) }
            }

            item {
                CategoryGrid(
                    categories = categoryGrid,
                    selectedCategory = selectedCategory,
                    sparklines = categorySparklines,
                    onCategoryClick = { name ->
                        viewModel.filterByCategory(if (selectedCategory == name) "All" else name)
                    }
                )
            }

            item {
                if (!incomeTransactions.isNullOrEmpty()) {
                    IncomeSection(transactions = incomeTransactions ?: emptyList())
                }
            }

            item {
                LumeFilterPill(
                    selectedFilter = selectedTimeFilter,
                    customYearMonth = customYearMonth,
                    onFilterSelect = viewModel::selectTimeFilter,
                    onCustomMonthSelect = viewModel::selectCustomMonth
                )
            }

            item {
                WalletSearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
            }

            item {
                TransactionsPanel(
                    groupedTransactions = groupedTransactions,
                    onTransactionClick = { editingTransactionId = it.id }
                )
            }

            } // end selectedTab == 0 block
        }

        if (editingTransaction != null) {
            TransactionEditSheet(
                transaction = editingTransaction,
                onDismiss = { editingTransactionId = null },
                onUpdateCategory = { category, applyToFuture ->
                    viewModel.reclassifyTransaction(
                        transactionId = editingTransaction.id,
                        merchantName = editingTransaction.description,
                        newCategory = category,
                        applyToHistory = applyToFuture
                    )
                    editingTransactionId = null
                },
                onMarkAsSalary = { applyToHistory ->
                    viewModel.reclassifyTransaction(
                        transactionId = editingTransaction.id,
                        merchantName = editingTransaction.description,
                        newCategory = "Salary",
                        applyToHistory = applyToHistory
                    )
                    editingTransactionId = null
                }
            )
        }
    }
}

@Composable
private fun SmartAlertFeed(alerts: List<FinancialAlert>) {
    if (alerts.isEmpty()) return
    Column {
        Text(
            "SMART ALERTS",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = TextMain.copy(alpha = 0.40f),
            modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(alerts) { alert ->
                SmartAlertChip(alert)
            }
        }
    }
}

@Composable
private fun SmartAlertChip(alert: FinancialAlert) {
    val tint = when (alert.tintKey) {
        "amber"   -> LumeAmber
        "emerald" -> LumeEmerald
        "purple"  -> LumePurple
        "red"     -> CriticalRed
        "cyan"    -> LumeCyan
        else      -> TextMain
    }
    Surface(
        modifier = Modifier.width(172.dp),
        color = tint.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.24f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(alert.emoji, style = MaterialTheme.typography.bodyMedium)
                Text(
                    alert.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.6.sp,
                    color = tint,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                alert.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextMain.copy(alpha = 0.78f),
                maxLines = 2
            )
            alert.amount?.let { amt ->
                Spacer(Modifier.height(5.dp))
                Text(
                    "€${"%.0f".format(amt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HouseholdPulseCard(pulse: PulseData) {
    val statusColor = when (pulse.status) {
        PulseStatus.SAFE -> LumeEmerald
        PulseStatus.WARNING -> LumeAmber
        PulseStatus.CRITICAL -> CriticalRed
    }
    val statusLabel = when (pulse.status) {
        PulseStatus.SAFE -> "● Safe"
        PulseStatus.WARNING -> "◐ Watch"
        PulseStatus.CRITICAL -> "⚠ Alert"
    }
    EliteGlassCard(glowColor = statusColor.copy(alpha = 0.28f), modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "HOUSEHOLD PULSE",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = TextMain.copy(alpha = 0.55f)
                )
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.38f))
                ) {
                    Text(
                        statusLabel,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Primary metric
            Text(
                pulse.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = TextMain.copy(alpha = 0.65f), fontSize = 16.sp, fontWeight = FontWeight.Bold)) {
                        append("€")
                    }
                    withStyle(SpanStyle(color = TextMain, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)) {
                        append("%.0f".format(pulse.projectedAmount))
                    }
                    withStyle(SpanStyle(color = TextMain.copy(alpha = 0.42f), fontSize = 13.sp)) {
                        append("  ${pulse.projectedLabel}")
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            Spacer(Modifier.height(14.dp))

            // Insight rows
            val hasAnyInsight = pulse.topRisk != null || pulse.upcoming != null || pulse.suggestion != null
            if (!hasAnyInsight) {
                Text(
                    "No concerns this cycle. Keep it up.",
                    color = TextMain.copy(alpha = 0.40f),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                pulse.topRisk?.let {
                    PulseInsightRow(Icons.Rounded.TrendingUp, "Top risk", it, LumeAmber)
                    Spacer(Modifier.height(10.dp))
                }
                pulse.upcoming?.let {
                    PulseInsightRow(Icons.Rounded.CalendarToday, "Upcoming", it, TextMain.copy(alpha = 0.60f))
                    Spacer(Modifier.height(10.dp))
                }
                pulse.suggestion?.let {
                    PulseSuggestionRow(it)
                }
            }
        }
    }
}

@Composable
private fun PulseInsightRow(icon: ImageVector, label: String, value: String, iconTint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Column {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = TextMain.copy(alpha = 0.38f)
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextMain, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PulseSuggestionRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LumePurple.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, LumePurple.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = LumePurple, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextMain.copy(alpha = 0.85f))
    }
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryBlockUi>,
    selectedCategory: String,
    sparklines: List<CategorySparkline>,
    onCategoryClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { category ->
                    CategoryGridItem(
                        data = category,
                        modifier = Modifier.weight(1f),
                        isSelected = selectedCategory == category.name,
                        isFeatured = false,
                        sparkline = sparklines.firstOrNull { it.category == category.name },
                        onClick = { onCategoryClick(category.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGridItem(
    data: CategoryBlockUi,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isFeatured: Boolean,
    sparkline: CategorySparkline?,
    onClick: () -> Unit
) {
    val active = isSelected || isFeatured
    val utilization = if (data.limit > 0) (data.spent / data.limit).coerceIn(0.0, 1.0).toFloat() else 0f
    val barColor = when {
        utilization >= 1f   -> CriticalRed
        utilization >= 0.75f -> LumeAmber
        else                -> data.color
    }
    Column(
        modifier = modifier
            .background(
                color = if (active) data.color.copy(alpha = 0.14f) else Color(0xFFB7C6E6).copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                brush = if (active) Brush.linearGradient(listOf(data.color, data.color.copy(alpha = 0.20f)))
                        else SolidColor(Color.White.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = data.icon,
            contentDescription = data.name,
            tint = if (active) data.color else TextMain.copy(alpha = 0.65f),
            modifier = Modifier.size(18.dp)
        )
        Text(data.name, style = MaterialTheme.typography.labelSmall, color = TextMain.copy(alpha = 0.65f))
        Text(
            "€${"%.0f".format(data.spent)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (utilization >= 1f) CriticalRed else TextMain
        )
        Text(
            "of €${data.limit.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMain.copy(alpha = 0.40f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(utilization)
                    .height(3.dp)
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
        sparkline?.let { s ->
            val maxVal = maxOf(s.prevPrevAmount, s.prevAmount, s.currentAmount, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                listOf(s.prevPrevAmount, s.prevAmount, s.currentAmount).forEach { v ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((16.dp * (v / maxVal)).coerceAtLeast(2.dp))
                            .background(data.color.copy(alpha = 0.35f), RoundedCornerShape(1.dp))
                    )
                }
            }
            val deltaLabel = when {
                s.delta > 0 -> "↑ +€${"%.0f".format(s.delta)} vs last"
                s.delta < 0 -> "↓ €${"%.0f".format(-s.delta)} vs last"
                else -> "= same as last"
            }
            Text(
                deltaLabel,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    s.delta > 0 -> LumeAmber.copy(alpha = 0.8f)
                    s.delta < 0 -> LumeEmerald.copy(alpha = 0.8f)
                    else -> TextMain.copy(alpha = 0.4f)
                },
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LumeFilterPill(
    selectedFilter: String,
    customYearMonth: YearMonth?,
    onFilterSelect: (String) -> Unit,
    onCustomMonthSelect: (YearMonth) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("All Time", "Current Cycle", "Previous Cycle").forEach { filter ->
            val isSelected = filter == selectedFilter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) LumePurple.copy(alpha = 0.20f) else Color.Transparent,
                        CircleShape
                    )
                    .clickable { onFilterSelect(filter) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter,
                    color = if (isSelected) TextMain else TextMain.copy(alpha = 0.50f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        // Calendar browse button
        val isCustomActive = selectedFilter == "Custom"
        Box(
            modifier = Modifier
                .weight(0.7f)
                .background(
                    if (isCustomActive) LumeEmerald.copy(alpha = 0.20f) else Color.Transparent,
                    CircleShape
                )
                .clickable { showPicker = true }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCustomActive && customYearMonth != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.CalendarToday, null,
                        tint = LumeEmerald, modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = customYearMonth.month.getDisplayName(JTimeTextStyle.SHORT, Locale.getDefault()),
                        fontSize = 9.sp, color = LumeEmerald, fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Icon(
                    Icons.Rounded.CalendarToday, contentDescription = "Browse month",
                    tint = TextMain.copy(alpha = 0.50f), modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (showPicker) {
        MonthYearWheelSheet(
            initialYearMonth = customYearMonth ?: YearMonth.now(),
            onConfirm = { ym ->
                onCustomMonthSelect(ym)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthYearWheelSheet(
    initialYearMonth: YearMonth,
    onConfirm: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    val currentYear = YearMonth.now().year
    val months = (1..12).map { Month.of(it).getDisplayName(JTimeTextStyle.FULL, Locale.getDefault()) }
    val years = (0..4).map { (currentYear - it).toString() }

    var selectedMonthIdx by rememberSaveable {
        mutableStateOf((initialYearMonth.monthValue - 1).coerceIn(0, 11))
    }
    var selectedYearIdx by rememberSaveable {
        mutableStateOf(years.indexOf(initialYearMonth.year.toString()).coerceAtLeast(0))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color(0xFF0F1B2D)
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp)
        ) {
            Text(
                "Browse by Month", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextMain,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WheelColumnPicker(
                    items = months,
                    selectedIndex = selectedMonthIdx,
                    onSelectionChanged = { selectedMonthIdx = it },
                    modifier = Modifier.weight(2f)
                )
                WheelColumnPicker(
                    items = years,
                    selectedIndex = selectedYearIdx,
                    onSelectionChanged = { selectedYearIdx = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = {
                    val month = Month.of(selectedMonthIdx + 1)
                    val year = years[selectedYearIdx.coerceIn(0, years.lastIndex)].toInt()
                    onConfirm(YearMonth.of(year, month))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = LumeEmerald
                )
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumnPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 44.dp
    val visibleCount = 5
    val padding = visibleCount / 2
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, maxOf(0, items.lastIndex))
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val derivedSelected by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, items.lastIndex) }
    }

    LaunchedEffect(derivedSelected) { onSelectionChanged(derivedSelected) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val itemHeightPx = with(density) { itemHeight.roundToPx() }
            val offset = listState.firstVisibleItemScrollOffset
            val target = if (offset > itemHeightPx / 2)
                listState.firstVisibleItemIndex + 1
            else
                listState.firstVisibleItemIndex
            scope.launch {
                listState.animateScrollToItem(target.coerceIn(0, items.lastIndex))
            }
        }
    }

    Box(modifier = modifier.height(itemHeight * visibleCount)) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxSize()
        ) {
            repeat(padding) { item { Box(Modifier.height(itemHeight).fillMaxWidth()) } }
            items(items.size) { idx ->
                val isSelected = idx == derivedSelected
                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[idx],
                        color = if (isSelected) LumeEmerald else TextMain.copy(alpha = 0.30f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (isSelected) 17.sp else 13.sp
                    )
                }
            }
            repeat(padding) { item { Box(Modifier.height(itemHeight).fillMaxWidth()) } }
        }
        // Center selection highlight band
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(LumeEmerald.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
        )
    }
}

@Composable
private fun WalletSearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = TextMain.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search transactions…",
                    color = TextMain.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextMain),
                cursorBrush = SolidColor(LumePurple),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.Close,
                null,
                tint = TextMain.copy(alpha = 0.45f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onQueryChange("") }
            )
        }
    }
}

@Composable
private fun TransactionsPanel(
    groupedTransactions: Map<String, List<Transaction>>,
    onTransactionClick: (Transaction) -> Unit
) {
    var taxTaggingTx by remember { mutableStateOf<Transaction?>(null) }

    EliteGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "TRANSACTIONS",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = TextMain.copy(alpha = 0.58f)
        )
        Spacer(Modifier.height(8.dp))

        groupedTransactions.forEach { (date, transactions) ->
            Text(
                text = date,
                style = MaterialTheme.typography.labelMedium,
                color = TextMain.copy(alpha = 0.42f),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            transactions.forEachIndexed { index, tx ->
                TransactionStrip(
                    tx = tx,
                    onClick = { onTransactionClick(tx) },
                    onLongClick = { taxTaggingTx = tx }
                )
                if (index != transactions.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.05f))
                    )
                }
            }
        }
    }

    taxTaggingTx?.let { tx ->
        TaxTagDialog(
            tx = tx,
            onDismiss = { taxTaggingTx = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionStrip(
    tx: Transaction,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val tint = categoryTint(tx.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = categoryIcon(tx.category),
            contentDescription = tx.category,
            tint = TextMain.copy(alpha = 0.72f),
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(tx.description, color = TextMain, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = canonicalCategory(tx.category),
                color = tint.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(tx.date, color = TextMain.copy(alpha = 0.40f), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "-EUR ${"%.2f".format(abs(tx.amount))}",
                color = TextMain,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = TextMain.copy(alpha = 0.24f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TransactionEditSheet(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onUpdateCategory: (String, Boolean) -> Unit,
    onMarkAsSalary: (applyToHistory: Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var applyToFuture by rememberSaveable(transaction.id) { mutableStateOf(false) }
    val categories = listOf(
        "Groceries", "Dining", "Travel", "Shopping",
        "Entertainment", "School", "Utilities", "Exclude"
    )
    val selectedCategory = canonicalCategory(transaction.category)

    var trips by remember { mutableStateOf<List<WalletTripEntity>>(emptyList()) }
    var selectedTrip by remember(transaction.id) { mutableStateOf(transaction.trip) }

    LaunchedEffect(Unit) {
        trips = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AppDatabase.getInstance(context).walletTripDao().getAllTrips()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A1848),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Edit Transaction", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextMain)
            Text(transaction.description, color = TextMain.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(24.dp))
            Text("RE-CATEGORIZE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = TextMain.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdateCategory(cat, applyToFuture) },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LumePurple.copy(alpha = 0.2f),
                            selectedLabelColor = LumePurple,
                            selectedLeadingIconColor = LumePurple
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = applyToFuture,
                    onCheckedChange = { applyToFuture = it },
                    colors = CheckboxDefaults.colors(checkedColor = LumePurple)
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("Create Merchant Rule", fontWeight = FontWeight.Medium, color = TextMain)
                    Text(
                        "Always categorize this merchant as selected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMain.copy(alpha = 0.45f)
                    )
                }
            }

            if (trips.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                Spacer(Modifier.height(16.dp))
                Text("TAG TO TRIP", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = TextMain.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTrip == null,
                        onClick = {
                            selectedTrip = null
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    AppDatabase.getInstance(context).walletTransactionDao()
                                        .updateTransactionTrip(transaction.localId, null)
                                }
                            }
                        },
                        label = { Text("None") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LumeCyan.copy(alpha = 0.15f),
                            selectedLabelColor = LumeCyan
                        )
                    )
                    trips.forEach { trip ->
                        FilterChip(
                            selected = selectedTrip == trip.name,
                            onClick = {
                                selectedTrip = trip.name
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        AppDatabase.getInstance(context).walletTransactionDao()
                                            .updateTransactionTrip(transaction.localId, trip.name)
                                    }
                                }
                            },
                            label = { Text(trip.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LumeCyan.copy(alpha = 0.15f),
                                selectedLabelColor = LumeCyan
                            )
                        )
                    }
                }
            }

            // Mark as Salary — shown for positive-amount (income) transactions
            if (transaction.amount > 0) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.FilledTonalButton(
                    onClick = { onMarkAsSalary(applyToFuture) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = LumeEmerald.copy(alpha = 0.15f),
                        contentColor = LumeEmerald
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Rounded.Payments,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Mark as Salary",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    if (applyToFuture) "Will auto-classify future transactions from this payer as Salary"
                    else "Only marks this transaction",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMain.copy(alpha = 0.40f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}


@Composable
private fun TaxTagDialog(tx: Transaction, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val taxCategories = listOf("Work Equipment", "Home Office", "Medical Expenses", "Donations", "Work Commute", "Other")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag for Tax", color = TextMain) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    tx.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                taxCategories.forEach { cat ->
                    TextButton(
                        onClick = {
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    AppDatabase.getInstance(context).taxTagDao().upsert(
                                        TaxTagEntity(
                                            entityType = "transaction",
                                            entityId = tx.localId.toLong(),
                                            taxCategory = cat,
                                            year = tx.bookedOn.year
                                        )
                                    )
                                }
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(cat, color = LumeEmerald)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = Color(0xFF0E1117)
    )
}

private fun categoryAmountFor(summary: List<CategorySummary>, target: String): Double {
    return summary
        .filter { canonicalCategory(it.category) == target }
        .sumOf { abs(it.totalAmount) }
}

@Composable
private fun SpendingTrendsContent(trends: List<MonthlySpend>) {
    val trendColors = listOf(
        Color(0xFF14B8A6), // Groceries — teal
        Color(0xFFF59E0B), // Dining — amber
        Color(0xFF3B82F6), // Travel — blue
        Color(0xFFEC4899), // Shopping — pink
        Color(0xFF8B5CF6)  // Other — purple
    )
    val categoryKeys = listOf("Groceries", "Dining", "Travel", "Shopping", "Other")

    if (trends.isEmpty()) {
        Text(
            text = "No spending data yet — import transactions via CSV",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        return
    }

    val maxTotal = trends.maxOfOrNull { it.total } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "6-MONTH SPEND BREAKDOWN",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.8.sp,
            color = TextMain.copy(alpha = 0.45f)
        )

        // Bar chart — one solid bar per month, height proportional to spend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            trends.forEach { month ->
                val barFraction = if (maxTotal > 0) (month.total / maxTotal).toFloat() else 0f
                val dominantCategory = month.categories.maxByOrNull { it.value }?.key ?: "Other"
                val barColor = trendColors[categoryKeys.indexOf(dominantCategory).coerceAtLeast(0)]
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((barFraction * 140).dp)
                            .background(barColor.copy(alpha = 0.80f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = month.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categoryKeys.take(5).forEachIndexed { idx, key ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(trendColors[idx], RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = key,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Monthly totals
        trends.forEach { month ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = month.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "€${"%.0f".format(month.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain
                )
            }
        }
    }
}

private fun canonicalCategory(category: String): String {
    val key = category.lowercase()
    return when {
        "exclude" in key -> "Excluded"
        "income" in key -> "Income"
        "grocer" in key -> "Groceries"
        "dining" in key || "takeaway" in key || "mensa" in key || "school lunch" in key -> "Dining"
        "transport" in key || "transit" in key || "travel" in key || "gas & fuel" in key || "carsharing" in key -> "Travel"
        "utilit" in key || "housing" in key || "rent" in key || "broadcasting" in key
                || "internet" in key || "mobile" in key || "municipal" in key -> "Utilities"
        "transfer" in key || "sepa" in key || "interbank" in key -> "Transfers"
        "shop" in key || "clothing" in key || "home & furniture" in key
                || "diy" in key || "electronics" in key || "discount" in key -> "Shopping"
        "insurance" in key || "pharmacy" in key || "health" in key -> "Insurance"
        "school fees" in key || "education" in key || "activities" in key -> "Education"
        "drugstore" in key || "personal care" in key -> "Drugstore"
        "bank fees" in key -> "Bank Fees"
        else -> "Other"
    }
}

private fun categoryTint(category: String): Color {
    return when (canonicalCategory(category)) {
        "Groceries" -> Color(0xFF14B8A6)
        "Dining" -> Color(0xFFF59E0B)
        "Travel" -> Color(0xFF3B82F6)
        "Utilities" -> Color(0xFF8B5CF6)
        "Transfers" -> Color(0xFF06B6D4)
        "Shopping" -> Color(0xFFEC4899)
        "Excluded" -> Color(0xFF94A3B8)
        "Income" -> Color(0xFF22C55E)
        "Insurance" -> Color(0xFFEF4444)
        "Education" -> Color(0xFFA855F7)
        "Drugstore" -> Color(0xFF06B6D4)
        "Bank Fees" -> Color(0xFF64748B)
        else -> TextMain.copy(alpha = 0.7f)
    }
}

private fun categoryIcon(category: String): ImageVector {
    return when (canonicalCategory(category)) {
        "Groceries" -> Icons.Rounded.LocalGroceryStore
        "Dining" -> Icons.Rounded.Restaurant
        "Travel" -> Icons.Rounded.Flight
        "Utilities" -> Icons.Rounded.Payments
        "Transfers" -> Icons.Rounded.Payments
        "Shopping" -> Icons.Rounded.CardGiftcard
        else -> Icons.Rounded.Payments
    }
}

@Composable
private fun SalaryAllocationCard(allocation: SalaryAllocationData) {
    var fixedExpanded by remember { mutableStateOf(false) }
    EliteGlassCard(glowColor = LumeEmerald.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header row: Salary amount + date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SALARY ALLOCATION",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = TextMain.copy(alpha = 0.55f)
                )
                Text(
                    "€${"%.0f".format(allocation.salaryAmount)}  ·  ${allocation.salaryDateLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain.copy(alpha = 0.80f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Segmented bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(4.dp)),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (allocation.fixedPercent == 0f && allocation.discretionaryPercent == 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                    )
                } else {
                    val fixedPct = allocation.fixedPercent
                    val discPct = allocation.discretionaryPercent
                    val remPct = allocation.remainingPercent
                    // Fixed segment (left-rounded)
                    if (fixedPct > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(fixedPct)
                                .height(8.dp)
                                .background(
                                    CriticalRed,
                                    RoundedCornerShape(
                                        topStart = 4.dp, bottomStart = 4.dp,
                                        topEnd = if (discPct == 0f && remPct == 0f) 4.dp else 0.dp,
                                        bottomEnd = if (discPct == 0f && remPct == 0f) 4.dp else 0.dp
                                    )
                                )
                        )
                    }
                    // Discretionary segment
                    if (discPct > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(discPct)
                                .height(8.dp)
                                .background(
                                    LumeAmber,
                                    RoundedCornerShape(
                                        topStart = if (fixedPct == 0f) 4.dp else 0.dp,
                                        bottomStart = if (fixedPct == 0f) 4.dp else 0.dp,
                                        topEnd = if (remPct == 0f) 4.dp else 0.dp,
                                        bottomEnd = if (remPct == 0f) 4.dp else 0.dp
                                    )
                                )
                        )
                    }
                    // Remaining segment (right-rounded)
                    if (remPct > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(remPct)
                                .height(8.dp)
                                .background(
                                    LumeEmerald,
                                    RoundedCornerShape(
                                        topStart = if (fixedPct == 0f && discPct == 0f) 4.dp else 0.dp,
                                        bottomStart = if (fixedPct == 0f && discPct == 0f) 4.dp else 0.dp,
                                        topEnd = 4.dp, bottomEnd = 4.dp
                                    )
                                )
                        )
                    }
                }
            }

            if (allocation.fixedPercent == 0f && allocation.discretionaryPercent == 0f) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Fixed costs will appear after recurring detection",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.70f)
                )
            } else {
                Spacer(Modifier.height(12.dp))

                // Three-column legend
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Fixed
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Fixed",
                            style = MaterialTheme.typography.labelSmall,
                            color = CriticalRed.copy(alpha = 0.85f)
                        )
                        Text(
                            "€${"%.0f".format(allocation.fixedTotal)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMain
                        )
                        Text(
                            "${(allocation.fixedPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMain.copy(alpha = 0.42f)
                        )
                    }
                    // Discretionary
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Discretionary",
                            style = MaterialTheme.typography.labelSmall,
                            color = LumeAmber.copy(alpha = 0.85f)
                        )
                        Text(
                            "€${"%.0f".format(allocation.discretionaryTotal)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMain
                        )
                        Text(
                            "${(allocation.discretionaryPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMain.copy(alpha = 0.42f)
                        )
                    }
                    // Remaining
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = LumeEmerald.copy(alpha = 0.85f)
                        )
                        Text(
                            "€${"%.0f".format(allocation.remaining)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMain
                        )
                        Text(
                            "${(allocation.remainingPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMain.copy(alpha = 0.42f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                Spacer(Modifier.height(12.dp))

                // Fixed costs collapsible row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { fixedExpanded = !fixedExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Fixed costs",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextMain.copy(alpha = 0.80f)
                    )
                    Icon(
                        imageVector = if (fixedExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMain.copy(alpha = 0.50f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = fixedExpanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        if (allocation.fixedBills.isEmpty()) {
                            Text(
                                "No recurring bills detected yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        } else {
                            allocation.fixedBills.forEachIndexed { index, (merchant, amount) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        merchant,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMain.copy(alpha = 0.80f)
                                    )
                                    Text(
                                        "€${"%.0f".format(amount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (index != allocation.fixedBills.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color.White.copy(alpha = 0.05f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeSection(transactions: List<Transaction>) {
    var expanded by remember { mutableStateOf(false) }
    val total = transactions.sumOf { it.amount }
    EliteGlassCard(glowColor = LumeEmerald.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Payments,
                        contentDescription = null,
                        tint = LumeEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Income this cycle",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextMain.copy(alpha = 0.85f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "+€${"%.0f".format(total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LumeEmerald
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMain.copy(alpha = 0.50f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    transactions.forEachIndexed { index, tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tx.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMain.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    tx.date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMain.copy(alpha = 0.38f)
                                )
                            }
                            Text(
                                "+€${"%.2f".format(tx.amount)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = LumeEmerald
                            )
                        }
                        if (index != transactions.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.05f))
                            )
                        }
                    }
                }
            }
        }
    }
}
