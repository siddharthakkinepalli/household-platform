package com.household.app.ui.v2.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.domain.models.BudgetRunway
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.Red
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted

private const val FULL_DAILY_BUDGET = 100f

@Composable
fun BudgetGauge(runway: BudgetRunway, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = (runway.dailyBudget.toFloat() / FULL_DAILY_BUDGET).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "budget_gauge_progress"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(240.dp)) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val startAngle = 140f
            val sweepAngle = 260f
            val stroke = 12.dp.toPx()

            drawArc(
                color = TextMain.copy(alpha = 0.10f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            drawArc(
                brush = Brush.sweepGradient(
                    colorStops = arrayOf(
                        0.0f to Red,
                        0.5f to LumeAmber,
                        1.0f to Color(0xFF67F6E8)
                    ),
                    center = Offset(size.width / 2f, size.height / 2f)
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedProgress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
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
                color = Color(0xFF67F6E8).copy(alpha = 0.75f),
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
