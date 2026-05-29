package com.household.app.ui.v2.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.domain.models.BudgetRunway
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted

private const val FULL_DAILY_BUDGET = 100f
private const val CYCLE_DAYS = 30f

@Composable
fun BudgetGauge(runway: BudgetRunway, modifier: Modifier = Modifier) {
    // Fraction of the daily budget relative to a healthy baseline (capped at 1.0)
    val budgetFraction = (runway.dailyBudget.toFloat() / FULL_DAILY_BUDGET).coerceIn(0f, 1f)

    // Time fraction: how far through the cycle we are (0 = just started, 1 = end)
    val timeFraction = ((CYCLE_DAYS - runway.daysRemaining.toFloat()) / CYCLE_DAYS).coerceIn(0f, 1f)

    // Spending is healthy when daily budget (remaining/days) is still high relative to time elapsed.
    // If you're early in the cycle and still have a high daily budget: GREEN
    // If you're spending ahead of the time pacing: AMBER / RED
    val spendFraction = 1f - budgetFraction  // 0 = nothing spent, 1 = all gone
    val gaugeColor = when {
        spendFraction > timeFraction + 0.10f -> CriticalRed   // over-pacing
        spendFraction > timeFraction         -> LumeAmber      // slight over-spend
        else                                 -> LumeEmerald    // on track or under-spending
    }

    val animatedProgress by animateFloatAsState(
        targetValue = budgetFraction,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "budget_gauge_progress"
    )
    val animatedColor by animateColorAsState(
        targetValue = gaugeColor,
        animationSpec = tween(durationMillis = 800),
        label = "budget_gauge_color"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(240.dp)) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val startAngle = 140f
            val maxSweep = 260f
            val stroke = 12.dp.toPx()

            // Track (background arc)
            drawArc(
                color = TextMain.copy(alpha = 0.10f),
                startAngle = startAngle,
                sweepAngle = maxSweep,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Fill arc — solid pacing-aware color, fills from healthy end
            if (animatedProgress > 0f) {
                drawArc(
                    color = animatedColor,
                    startAngle = startAngle,
                    sweepAngle = maxSweep * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "€${runway.dailyBudget.toInt()}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextMain
            )
            Text(
                text = "PER DAY",
                style = MaterialTheme.typography.labelMedium,
                color = gaugeColor.copy(alpha = 0.85f),
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${runway.daysRemaining} days until salary",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
