package com.household.app.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun EliteGlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    borderAlpha: Float = 0.12f,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .drawWithCache {
                val explicitGlowAlpha = glowColor.alpha.takeIf { it < 1f }
                val normalizedGlowColor = if (glowColor == Color.Transparent) glowColor else glowColor.copy(alpha = 1f)
                val glowHotspot = Offset(size.width * 0.08f, size.height * 0.08f)
                val intenseGlow = Brush.radialGradient(
                    colors = listOf(
                        normalizedGlowColor.copy(alpha = explicitGlowAlpha ?: 0.22f),
                        Color.Transparent
                    ),
                    center = glowHotspot,
                    radius = size.maxDimension
                )
                val ambientGlow = Brush.radialGradient(
                    colors = listOf(
                        normalizedGlowColor.copy(alpha = explicitGlowAlpha?.times(0.45f) ?: 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.maxDimension * 1.2f
                )
                onDrawBehind {
                    if (glowColor != Color.Transparent) {
                        drawCircle(
                            brush = ambientGlow,
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.maxDimension * 1.2f
                        )
                        drawCircle(
                            brush = intenseGlow,
                            center = glowHotspot,
                            radius = size.maxDimension
                        )
                    }
                }
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha.coerceAtLeast(0.22f)),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.06f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite
                ),
                shape = RoundedCornerShape(32.dp)
            ),
        contentAlignment = androidx.compose.ui.Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}