package com.household.app.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.ui.compose.theme.Border
import com.household.app.ui.compose.theme.Green
import com.household.app.ui.compose.theme.GreenSoft
import com.household.app.ui.compose.theme.HeroMintBottom
import com.household.app.ui.compose.theme.HeroMintTop
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
    val deltaPrefix = if (isPositive) "+" else "-"
    val deltaText = "$deltaPrefix${abs(deltaPercent).toInt()}% vs last month"
    val statusText = if (isPositive) "Healthy spend" else "Watch spending"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(HeroMintTop, HeroMintBottom)
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 112.dp, y = (-30).dp)
                    .size(132.dp)
                    .background(
                        color = Green.copy(alpha = 0.11f),
                        shape = RoundedCornerShape(36.dp)
                    )
            )

            Box(
                modifier = Modifier
                    .offset(x = 192.dp, y = 52.dp)
                    .size(92.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(28.dp)
                    )
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Total This Month",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextMuted,
                        letterSpacing = 0.08.sp
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(chipBackground, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
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

                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary.copy(alpha = 0.74f)
                            )
                        )
                    }
                }
            }
        }
    }
}
