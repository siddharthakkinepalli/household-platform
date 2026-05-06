package com.household.app.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.ui.compose.theme.Border
import com.household.app.ui.compose.theme.Orange
import com.household.app.ui.compose.theme.OrangeSoft
import com.household.app.ui.compose.theme.SurfaceVariant
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextPrimary
import com.household.app.ui.compose.theme.TextSecondary

/**
 * TodayAlertsRow — adaptive layout.
 * Phone (≤600dp): Today card + Alerts card stacked vertically.
 * Tablet (>600dp): side by side with equal weight.
 */
@Composable
fun TodayAlertsRow(
    todayItems: List<TodayItem>,
    alerts: List<AlertItem>,
    modifier: Modifier = Modifier
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    if (screenWidthDp > 600) {
        Row(
            modifier              = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TodayCard(items = todayItems, modifier = Modifier.weight(1f))
            AlertsCard(alerts = alerts, modifier = Modifier.weight(1f))
        }
    } else {
        Column(
            modifier            = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TodayCard(items = todayItems)
            AlertsCard(alerts = alerts)
        }
    }
}

// ── Data models ───────────────────────────────────────────────────────────

data class TodayItem(val name: String, val time: String)

data class AlertItem(
    val text: String,
    val isUrgent: Boolean = false
)

// ── Today card ────────────────────────────────────────────────────────────

@Composable
private fun TodayCard(
    items: List<TodayItem>,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        SectionHeader("Today")
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                text  = "Nothing scheduled",
                style = TextStyle(fontSize = 13.sp, color = TextMuted)
            )
        } else {
            items.take(2).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text  = "• ${item.name}",
                        style = TextStyle(fontSize = 13.sp, color = TextPrimary),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text  = item.time,
                        style = TextStyle(fontSize = 12.sp, color = TextSecondary)
                    )
                }
            }
            if (items.size > 2) {
                Text(
                    text  = "+ ${items.size - 2} more",
                    style = TextStyle(fontSize = 12.sp, color = TextSecondary),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ── Alerts card ───────────────────────────────────────────────────────────

@Composable
private fun AlertsCard(
    alerts: List<AlertItem>,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Alerts")
            if (alerts.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                // Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Orange)
                ) {
                    Text(
                        text  = alerts.size.toString(),
                        style = TextStyle(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (alerts.isEmpty()) {
            Text(
                text  = "No alerts",
                style = TextStyle(fontSize = 13.sp, color = TextMuted)
            )
        } else {
            alerts.forEach { alert ->
                AlertRow(alert)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertItem) {
    val bgColor = if (alert.isUrgent) Orange.copy(alpha = 0.06f) else Color.Transparent
    val dotColor = if (alert.isUrgent) Orange else TextMuted.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (alert.isUrgent) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .background(Orange, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text  = alert.text,
                style = TextStyle(
                    fontSize = 13.sp,
                    color    = if (alert.isUrgent) Orange else TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Shared card shell ─────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        border    = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White, SurfaceVariant.copy(alpha = 0.55f))
                    )
                )
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text  = text.uppercase(),
        style = TextStyle(
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Medium,
            color         = TextMuted,
            letterSpacing = 0.08.sp
        )
    )
}
