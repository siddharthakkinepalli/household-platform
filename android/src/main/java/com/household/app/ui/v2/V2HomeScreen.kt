package com.household.app.ui.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.data.AppDatabase
import com.household.app.data.entities.DocumentEntity
import com.household.app.data.repository.DocumentRepositoryImpl
import com.household.app.domain.usecases.GetBudgetRunwayUseCase
import com.household.app.ui.compose.navigation.Screen
import com.household.app.ui.compose.state.ActivityItem
import com.household.app.ui.compose.state.CategoryBudget
import com.household.app.ui.compose.state.HomeViewModel
import com.household.app.ui.compose.state.InsightType
import com.household.app.ui.compose.state.Module
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.Red
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.BudgetGauge
import com.household.app.ui.v2.components.EliteGlassCard
import androidx.compose.ui.geometry.Offset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun V2HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToVault: () -> Unit = {},
    onNavigateToModule: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val budgetRunway = remember(state.balanceValue, state.salaryAnchorDay) {
        GetBudgetRunwayUseCase().execute(currentBalance = state.balanceValue, anchorDay = state.salaryAnchorDay)
    }
    val todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM"))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090D))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF14B8A6).copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = size.width * 1.25f
                ),
                center = Offset(0f, 0f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width, size.height),
                    radius = size.width * 1.5f
                ),
                center = Offset(size.width, size.height)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height),
                    radius = size.width * 1.0f
                ),
                center = Offset(size.width * 0.5f, size.height)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Household OS",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Premium for luminescent Glass", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                    Text(todayDate, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }

            state.insights.firstOrNull()?.let { insight ->
                EliteGlassCard(glowColor = if (insight.type == InsightType.INFO) LumePurple else LumeAmber) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (insight.type == InsightType.SUCCESS) LumeEmerald else LumeAmber
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(insight.title, color = TextMain, fontWeight = FontWeight.Bold)
                            Text(insight.message, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            EliteGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = Color(0xFF67F6E8),
                borderAlpha = 0.30f
            ) {
                Text("SMART BUDGET", color = Color(0xFF67F6E8), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, fontSize = 10.sp)
                Text("RUNWAY TO SALARY DAY", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(10.dp))
                
                if (state.loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LumeEmerald)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BudgetGauge(runway = budgetRunway)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Remaining pool: ${state.balanceFormatted}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Per-category budget card — between gauge and modules
            if (state.categoryBudgets.isNotEmpty()) {
                CategoryBudgetsCard(budgets = state.categoryBudgets)
            }

            // Zone 2 — Active Insights
            if (state.unlinkedVaultCount > 0) {
                InsightCard(
                    title    = "Unlinked Receipts",
                    subtitle = "${state.unlinkedVaultCount} scans waiting to be matched to expenses",
                    icon     = Icons.Rounded.ReceiptLong,
                    color    = LumeAmber,
                    onClick  = onNavigateToVault
                )
            }

            // Zone 3 — Recent Activity
            if (state.recentActivity.isNotEmpty()) {
                EliteGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = LumePurple) {
                    Text(
                        "RECENT ACTIVITY",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.5.sp,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    state.recentActivity.forEach { item ->
                        ActivityRow(item)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Upcoming Dates widget
            UpcomingDatesCard(
                onViewAll = { onNavigateToModule(Screen.Documents.route) }
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("MODULES", color = TextMuted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, fontSize = 10.sp)
                viewModel.modules.chunked(2).forEach { rowModules ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowModules.forEach { module ->
                            val subtitle = when (module.id) {
                                "family" -> when (state.familyMemberCount) {
                                    0 -> "Add household members"
                                    1 -> "1 member"
                                    else -> "${state.familyMemberCount} members"
                                }
                                else -> module.subtitle
                            }
                            ModuleCard(
                                module = module.copy(subtitle = subtitle),
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateToModule(module.route) }
                            )
                        }
                        if (rowModules.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(108.dp))
        }
    }
}

@Composable
private fun CategoryBudgetsCard(budgets: List<CategoryBudget>) {
    EliteGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = Color(0xFF60A5FA),
        borderAlpha = 0.18f
    ) {
        Text(
            "BUDGET OVERVIEW",
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            fontSize = 10.sp
        )
        Text(
            "This Month",
            color = TextMain,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        budgets.forEach { budget ->
            val progress = (budget.spent / budget.limit).toFloat().coerceIn(0f, 1f)
            val remaining = budget.limit - budget.spent
            val isOver = remaining < 0
            val statusColor = if (isOver) Red else budget.color
            val statusText = if (isOver) {
                "Over by €${"%.2f".format(abs(remaining))}"
            } else {
                "€${"%.2f".format(remaining)} left"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    budget.name,
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = statusColor,
                trackColor = budget.color.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun InsightCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.05f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextMain)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = color.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem) {
    val (icon, iconTint, label, sublabel, amountText, amountColor) = when (item) {
        is ActivityItem.Scan -> ActivityRowData(
            icon       = Icons.Rounded.ReceiptLong,
            iconTint   = LumeCyan,
            label      = item.merchant,
            sublabel   = "Vault receipt",
            amountText = item.amount?.let { "€${"%.2f".format(it)}" } ?: "–",
            amountColor = TextSecondary
        )
        is ActivityItem.Spend -> ActivityRowData(
            icon        = Icons.Rounded.Payments,
            iconTint    = LumePurple,
            label       = item.description,
            sublabel    = item.category,
            amountText  = "- €${"%.2f".format(abs(item.amount))}",
            amountColor = Red
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconTint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextMain, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(sublabel, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text(amountText, color = amountColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private data class ActivityRowData(
    val icon: ImageVector,
    val iconTint: Color,
    val label: String,
    val sublabel: String,
    val amountText: String,
    val amountColor: Color
)

@Composable
private fun ModuleCard(
    module: Module,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        shape = RoundedCornerShape(32.dp)
    ) {
        EliteGlassCard(glowColor = module.color) {
            Icon(module.icon, contentDescription = module.title, tint = module.color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(12.dp))
            Text(module.title, color = TextMain, fontWeight = FontWeight.Bold)
            Text(module.subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Upcoming Dates Card ───────────────────────────────────────────────────────

@Composable
private fun UpcomingDatesCard(
    onViewAll: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val repo = remember { DocumentRepositoryImpl(db.documentDao(), db.documentAlertDao()) }
    val expiring by repo.getDocumentsExpiringSoon(withinDays = 30)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    if (expiring.isEmpty()) return

    val shown = expiring.take(3)

    EliteGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = LumeAmber.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = LumeAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "UPCOMING DATES",
                    color = LumeAmber,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    fontSize = 10.sp
                )
            }
            Row(
                modifier = Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "View all",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        shown.forEachIndexed { index, doc ->
            UpcomingDateRow(doc = doc)
            if (index < shown.lastIndex) {
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 1.dp
                )
            }
        }
    }
}

@Composable
private fun UpcomingDateRow(doc: DocumentEntity) {
    val daysLeft = doc.expiryDate?.let { epochMs ->
        ChronoUnit.DAYS.between(Instant.now(), Instant.ofEpochMilli(epochMs)).toInt()
    }

    val (label, color) = when {
        daysLeft == null -> "" to TextMuted
        daysLeft < 0 -> "Expired ${abs(daysLeft)}d ago" to CriticalRed
        daysLeft < 7 -> "${daysLeft}d left" to CriticalRed
        daysLeft < 30 -> "${daysLeft}d left" to LumeAmber
        else -> "${daysLeft}d left" to LumeEmerald
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = doc.title,
            color = TextMain,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

