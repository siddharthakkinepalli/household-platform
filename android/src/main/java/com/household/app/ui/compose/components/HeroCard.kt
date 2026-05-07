package com.household.app.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.ui.compose.theme.Border
import com.household.app.ui.compose.theme.Green
import com.household.app.ui.compose.theme.GreenSoft
import com.household.app.ui.compose.theme.Red
import com.household.app.ui.compose.theme.RedSoft
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextPrimary
import kotlin.math.abs

@Composable
fun HeroCard(
    balanceFormatted: String,
    deltaPercent: Float,
    modifier: Modifier = Modifier
) {
    val isPositive = deltaPercent >= 0f
    val chipColor = if (isPositive) Green else Red
    val chipBackground = if (isPositive) GreenSoft else RedSoft
    val deltaArrow = if (isPositive) "↑" else "↓"
    val deltaText = "$deltaArrow ${abs(deltaPercent).toInt()}% vs last month"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Total this month",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextMuted,
                    letterSpacing = 0.96.sp
                )
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = balanceFormatted,
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontFeatureSettings = "tnum"
                )
            )

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .height(28.dp)
                    .padding(horizontal = 0.dp)
                    .background(chipBackground, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = deltaText,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = chipColor
                    )
                )
            }
        }
    }
}
