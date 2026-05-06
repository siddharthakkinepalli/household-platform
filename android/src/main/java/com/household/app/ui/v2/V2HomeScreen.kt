package com.household.app.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.ui.compose.state.HomeViewModel
import com.household.app.ui.compose.state.InsightType
import com.household.app.ui.compose.state.Module
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.Red
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.DeepBackground
import com.household.app.ui.v2.components.EliteGlassCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun V2HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    val todayItems = listOf(
        TimelineItem("Review family budget", "09:30"),
        TimelineItem("Plan weekly groceries", "18:00"),
        TimelineItem("Archive a receipt", "20:15")
    )
    val alerts = listOf(
        AlertEntry("Drive backup not connected yet", true),
        AlertEntry("2 contracts need metadata", true),
        AlertEntry("Meals plan still using placeholders", false)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        DeepBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Household OS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextMain
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Premium home control layer", color = TextSecondary)
                    Text(todayDate, color = TextMuted, style = MaterialTheme.typography.bodySmall)
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

            EliteGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = LumePurple) {
                Text("Total this month", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(10.dp))
                if (state.loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LumeEmerald)
                    }
                } else {
                    Text(
                        text = state.balanceFormatted,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMain
                    )
                    Spacer(Modifier.height(12.dp))
                    DeltaChip(deltaPercent = state.deltaPercent)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val wide = maxWidth > 600.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TodayCard(todayItems = todayItems, modifier = Modifier.weight(1f))
                        AlertsCard(alerts = alerts, modifier = Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TodayCard(todayItems = todayItems, modifier = Modifier.fillMaxWidth())
                        AlertsCard(alerts = alerts, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Modules", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                viewModel.modules.chunked(2).forEach { rowModules ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowModules.forEach { module ->
                            ModuleCard(module = module, modifier = Modifier.weight(1f))
                        }
                        if (rowModules.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            EliteGlassCard(modifier = Modifier.fillMaxWidth(), glowColor = LumeAmber) {
                Text("Premium Preview", color = TextMain, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This screen is the new visual baseline: navy shell, glass cards, luminescent accents, and bottom navigation. Once you approve this direction, the other tabs can be rebuilt to match.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(92.dp))
        }
    }
}

@Composable
private fun DeltaChip(deltaPercent: Float) {
    val positive = deltaPercent >= 0f
    val chipColor = if (positive) LumeEmerald else Red
    val label = if (positive) {
        "↑ ${"%.0f".format(abs(deltaPercent))}% vs last month"
    } else {
        "↓ ${"%.0f".format(abs(deltaPercent))}% vs last month"
    }

    Box(
        modifier = Modifier
            .background(chipColor.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = chipColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TodayCard(todayItems: List<TimelineItem>, modifier: Modifier = Modifier) {
    EliteGlassCard(modifier = modifier, glowColor = LumeEmerald) {
        Text("TODAY", color = TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        todayItems.take(2).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(LumeEmerald, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.label, color = TextMain, fontWeight = FontWeight.Medium)
                    Text(item.time, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text("+ ${todayItems.size - 2} more", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AlertsCard(alerts: List<AlertEntry>, modifier: Modifier = Modifier) {
    EliteGlassCard(modifier = modifier, glowColor = LumeAmber) {
        Text("Alerts", color = TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        alerts.forEach { alert ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (alert.urgent) LumeAmber.copy(alpha = 0.08f) else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (alert.urgent) Icons.Rounded.WarningAmber else Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = if (alert.urgent) LumeAmber else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(alert.label, color = TextMain, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModuleCard(module: Module, modifier: Modifier = Modifier) {
    EliteGlassCard(modifier = modifier, glowColor = module.color) {
        Icon(module.icon, contentDescription = module.title, tint = module.color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(12.dp))
        Text(module.title, color = TextMain, fontWeight = FontWeight.Bold)
        Text(module.subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

private data class TimelineItem(
    val label: String,
    val time: String
)

private data class AlertEntry(
    val label: String,
    val urgent: Boolean
)