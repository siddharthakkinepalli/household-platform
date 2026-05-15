package com.household.app.ui.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.data.entities.AlertType
import com.household.app.data.entities.DocumentAlertEntity
import com.household.app.ui.compose.theme.DeepCharcoal
import com.household.app.ui.compose.theme.GoldAccent
import com.household.app.ui.compose.theme.GoldBorder
import com.household.app.ui.compose.theme.GoldAccentLight
import com.household.app.ui.compose.theme.MatteCharcoal
import com.household.app.ui.compose.theme.TungstenYellow

@Composable
fun DocumentAlertCard(
    alert: DocumentAlertEntity,
    documentTitle: String,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )

    val isUrgent = alert.daysUntil <= 7
    val alertIcon: ImageVector = when (alert.alertType) {
        AlertType.EXPIRY_WARNING -> Icons.Default.Warning
        AlertType.PRICE_INCREASE -> Icons.Default.Description
        AlertType.AUTO_RENEWAL -> Icons.Default.Description
        AlertType.ACTION_REQUIRED -> Icons.Default.Warning
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = if (isUrgent) 12.dp else 6.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = GoldAccent.copy(alpha = 0.2f),
                spotColor = GoldAccent.copy(alpha = if (isUrgent) 0.4f else 0.2f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MatteCharcoal,
                        DeepCharcoal
                    )
                )
            )
            .drawWithGoldBorder(isUrgent = isUrgent)
            .clickable(
                onClick = {
                    isPressed = true
                    onAcknowledge()
                }
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Alert Icon with 3D Gold effect
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GoldAccentLight,
                                GoldBorder
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                GoldAccentLight,
                                GoldBorder.copy(alpha = 0.6f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = alertIcon,
                    contentDescription = null,
                    tint = MatteCharcoal,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = documentTitle,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = alert.message,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Deadline with Tungsten Yellow
                DeadlineBadge(
                    daysUntil = alert.daysUntil,
                    isUrgent = isUrgent
                )
            }
        }
    }
}

@Composable
private fun DeadlineBadge(
    daysUntil: Int,
    isUrgent: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isUrgent) {
        TungstenYellow.copy(alpha = 0.15f)
    } else {
        GoldAccent.copy(alpha = 0.1f)
    }

    val borderColor = if (isUrgent) {
        TungstenYellow.copy(alpha = 0.5f)
    } else {
        GoldBorder.copy(alpha = 0.5f)
    }

    val textColor = if (isUrgent) {
        TungstenYellow
    } else {
        GoldAccent
    }

    val daysText = when {
        daysUntil <= 0 -> "OVERDUE"
        daysUntil == 1 -> "Tomorrow"
        daysUntil <= 7 -> "$daysUntil days"
        daysUntil <= 30 -> "$daysUntil days"
        else -> "Upcoming"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = daysText,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun Modifier.drawWithGoldBorder(isUrgent: Boolean): Modifier = this.drawBehind {
    val borderColor = if (isUrgent) GoldAccent else GoldBorder.copy(alpha = 0.7f)

    // Top border with gold gradient
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(
                borderColor.copy(alpha = 0.1f),
                borderColor,
                borderColor.copy(alpha = 0.1f)
            )
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.5.dp.toPx()
    )

    // Left border
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(
                borderColor.copy(alpha = 0.1f),
                borderColor.copy(alpha = 0.5f)
            )
        ),
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = 1.dp.toPx()
    )

    // Inner glow effect for urgent alerts
    if (isUrgent) {
        val innerGlow = Brush.radialGradient(
            colors = listOf(
                GoldAccent.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.1f, size.height * 0.1f),
            radius = size.maxDimension
        )
        drawCircle(
            brush = innerGlow,
            radius = size.maxDimension * 0.8f
        )
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)