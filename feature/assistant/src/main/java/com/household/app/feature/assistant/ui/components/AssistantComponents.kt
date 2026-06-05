package com.household.app.feature.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Redefining EliteGlassCard and Colors to break circular dependency on :android
val LumePurple = Color(0xFF8B5CF6)
val LumeCyan = Color(0xFF14B8A6)
val LumeEmerald = Color(0xFF10B981)
val LumeAmber = Color(0xFFF59E0B)
val TextMain = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF9CA3AF)
val EliteGlassBorder = Color(0x33FFFFFF)

@Composable
fun EliteGlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .drawWithCache {
                val intenseGlow = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.1f),
                    radius = size.maxDimension
                )
                onDrawBehind {
                    drawRect(intenseGlow)
                }
            }
            .padding(1.dp) // Stroke simulation
    ) {
        Column(content = content)
    }
}
