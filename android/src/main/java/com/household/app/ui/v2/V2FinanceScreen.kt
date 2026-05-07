package com.household.app.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.viewmodels.CategorySummary
import com.household.app.ui.viewmodels.ExpensesViewModel
import com.household.app.ui.viewmodels.Transaction
import kotlin.math.abs

private data class CategoryBlockUi(
    val name: String,
    val amountLeft: Double,
    val color: Color,
    val icon: ImageVector
)

@Composable
fun V2FinanceScreen(
    viewModel: ExpensesViewModel = viewModel()
) {
    val transactions by viewModel.recentTransactions.observeAsState(emptyList())
    val categorySummary by viewModel.categorySummary.observeAsState(emptyList())
    val selectedCategory by viewModel.selectedCategory.observeAsState("All")
    val selectedTimeFilter by viewModel.selectedTimeFilter.observeAsState("This Month")
    var editingTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingTransaction = remember(editingTransactionId, transactions) {
        transactions.firstOrNull { it.id == editingTransactionId }
    }

    val totalSpend = transactions.sumOf { abs(it.amount) }
    val totalBudget = (totalSpend * 1.35).coerceAtLeast(1.0)
    val totalBudgetLeft = (totalBudget - totalSpend).coerceAtLeast(0.0)

    val categoryGrid = remember(categorySummary) {
        listOf(
            CategoryBlockUi("Groceries", categoryAmountFor(categorySummary, "Groceries"), Color(0xFF14B8A6), Icons.Rounded.ShoppingCart),
            CategoryBlockUi("Eat Out", categoryAmountFor(categorySummary, "Eat Out"), Color(0xFFF59E0B), Icons.Rounded.Restaurant),
            CategoryBlockUi("Travel", categoryAmountFor(categorySummary, "Travel"), Color(0xFF3B82F6), Icons.Rounded.Flight),
            CategoryBlockUi("Shopping", categoryAmountFor(categorySummary, "Shopping"), Color(0xFFEC4899), Icons.Rounded.CardGiftcard)
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

    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { it.date.ifBlank { "Unknown Date" } }
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
                Column {
                    Text(
                        text = "Wallet",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMain
                    )
                    Text(
                        text = "Monthly Overview",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                WalletHeroCard(totalBudgetLeft, filteredTransactions.take(14).map { abs(it.amount).toFloat() })
            }

            item {
                CategoryGrid(
                    categories = categoryGrid,
                    selectedCategory = selectedCategory,
                    onCategoryClick = { name ->
                        viewModel.filterByCategory(if (selectedCategory == name) "All" else name)
                    }
                )
            }

            item {
                LumeFilterPill(
                    selectedFilter = selectedTimeFilter,
                    onFilterSelect = viewModel::selectTimeFilter
                )
            }

            item {
                TransactionsPanel(
                    groupedTransactions = groupedTransactions,
                    onTransactionClick = { editingTransactionId = it.id }
                )
            }
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
                }
            )
        }
    }
}

@Composable
private fun WalletHeroCard(totalBudgetLeft: Double, sparklineValues: List<Float>) {
    EliteGlassCard(glowColor = LumePurple.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(194.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(54.dp))
                Text(
                    "BUDGET OVERVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = TextMain.copy(alpha = 0.62f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = TextMain.copy(alpha = 0.72f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("EUR ")
                        }
                        withStyle(
                            SpanStyle(
                                color = TextMain,
                                fontSize = MaterialTheme.typography.displayLarge.fontSize,
                                fontWeight = FontWeight.ExtraBold
                            )
                        ) {
                            append("${"%.2f".format(totalBudgetLeft)}")
                        }
                    },
                    lineHeight = MaterialTheme.typography.displayLarge.lineHeight
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "TOTAL BUDGET LEFT",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = TextMain.copy(alpha = 0.40f)
                )
            }

            Surface(
                color = LumeAmber.copy(alpha = 0.15f),
                shape = CircleShape,
                border = BorderStroke(1.dp, LumeAmber.copy(alpha = 0.45f)),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Star, null, tint = LumeAmber, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Star Member", color = LumeAmber, style = MaterialTheme.typography.labelSmall)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp)
            ) {
                LumeSparkline(
                    values = sparklineValues,
                    glowColor = LumePurple.copy(alpha = 0.30f),
                    lineColor = Color.White,
                    modifier = Modifier
                        .width(120.dp)
                        .height(60.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryBlockUi>,
    selectedCategory: String,
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
                        isFeatured = selectedCategory == "All" && category.name == "Groceries",
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
    onClick: () -> Unit
) {
    val active = isSelected || isFeatured
    Box(
        modifier = modifier
            .height(112.dp)
            .background(
                color = if (active) data.color.copy(alpha = 0.14f) else Color(0xFFB7C6E6).copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                brush = if (active) {
                    Brush.linearGradient(
                        listOf(
                            data.color,
                            data.color.copy(alpha = 0.20f)
                        )
                    )
                } else {
                    SolidColor(Color.White.copy(alpha = 0.10f))
                },
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = data.icon,
                contentDescription = data.name,
                tint = if (active) data.color else TextMain.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(data.name, style = MaterialTheme.typography.labelMedium, color = TextMain.copy(alpha = 0.70f))
            Spacer(Modifier.height(4.dp))
            Text(
                "EUR ${"%.2f".format(data.amountLeft)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
        }
    }
}

@Composable
private fun LumeFilterPill(
    selectedFilter: String,
    onFilterSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("All Time", "This Month", "Week 3").forEach { filter ->
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
    }
}

@Composable
private fun TransactionsPanel(
    groupedTransactions: Map<String, List<Transaction>>,
    onTransactionClick: (Transaction) -> Unit
) {
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
                TransactionStrip(tx = tx, onClick = { onTransactionClick(tx) })
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

@Composable
private fun TransactionStrip(
    tx: Transaction,
    onClick: () -> Unit
) {
    val tint = categoryTint(tx.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEditSheet(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onUpdateCategory: (String, Boolean) -> Unit
) {
    var applyToFuture by rememberSaveable(transaction.id) { mutableStateOf(true) }
    val categories = listOf("Groceries", "Eat Out", "Travel", "Shopping", "Exclude")
    val selectedCategory = canonicalCategory(transaction.category)

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

            categories.chunked(3).forEach { rowCats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCats.forEach { cat ->
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
                Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LumeSparkline(
    values: List<Float>,
    glowColor: Color,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val points = if (values.isEmpty()) listOf(0.2f, 0.6f, 0.35f, 0.72f, 0.45f) else values
    Canvas(modifier = modifier) {
        val max = points.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val min = points.minOrNull() ?: 0f
        val span = (max - min).coerceAtLeast(1f)
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path()

        points.forEachIndexed { index, value ->
            val x = index * step
            val norm = (value - min) / span
            val y = size.height - (norm * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = glowColor,
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(12f))
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(12f))
        )
    }
}

private fun categoryAmountFor(summary: List<CategorySummary>, target: String): Double {
    return summary
        .filter { canonicalCategory(it.category) == target }
        .sumOf { abs(it.totalAmount) }
}

private fun canonicalCategory(category: String): String {
    val key = category.lowercase()
    return when {
        "exclude" in key -> "Excluded"
        "grocery" in key -> "Groceries"
        "eat" in key || "food" in key || "restaurant" in key -> "Eat Out"
        "travel" in key || "transport" in key -> "Travel"
        "shop" in key -> "Shopping"
        else -> "Other"
    }
}

private fun categoryTint(category: String): Color {
    return when (canonicalCategory(category)) {
        "Groceries" -> Color(0xFF14B8A6)
        "Eat Out" -> Color(0xFFF59E0B)
        "Travel" -> Color(0xFF3B82F6)
        "Shopping" -> Color(0xFFEC4899)
        "Excluded" -> Color(0xFF94A3B8)
        else -> TextMain.copy(alpha = 0.7f)
    }
}

private fun categoryIcon(category: String): ImageVector {
    return when (canonicalCategory(category)) {
        "Groceries" -> Icons.Rounded.LocalGroceryStore
        "Eat Out" -> Icons.Rounded.Restaurant
        "Travel" -> Icons.Rounded.Flight
        "Shopping" -> Icons.Rounded.CardGiftcard
        else -> Icons.Rounded.Payments
    }
}
