package com.household.app.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.ui.compose.theme.Border
import com.household.app.ui.compose.theme.Orange
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

@Immutable
data class TodayItem(val name: String, val time: String)

@Immutable
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
    var expanded by remember { mutableStateOf(false) }
    val visibleItems = if (expanded) items else items.take(2)

    SectionCard(modifier = modifier) {
        SectionHeader("Today")
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                text  = "Nothing scheduled",
                style = TextStyle(fontSize = 13.sp, color = TextMuted)
            )
        } else {
            visibleItems.forEach { item ->
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
                    text  = if (expanded) "Show less" else "+ ${items.size - 2} more",
                    style = TextStyle(fontSize = 12.sp, color = TextSecondary),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { expanded = !expanded }
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
    val hasUrgent = alerts.any { it.isUrgent }
    val pulse = rememberInfiniteTransition(label = "alerts_badge_pulse")
    val badgeAlpha by pulse.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alerts_badge_alpha"
    )

    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Alerts")
            if (alerts.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                // Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .alpha(if (hasUrgent) badgeAlpha else 1f)
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
    val icon = if (alert.isUrgent) Icons.Rounded.WarningAmber else Icons.Rounded.Info
    val iconTint = if (alert.isUrgent) Orange else TextSecondary

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
            }
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
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
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        border    = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text  = text,
        style = TextStyle(
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Medium,
            color         = TextMuted,
            letterSpacing = 0.88.sp
        )
    )
}
