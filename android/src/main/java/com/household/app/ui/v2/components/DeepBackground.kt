package com.household.app.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun DeepBackground(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07090D))
            .drawBehind {
                // Top-left green glow (Teal)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF14B8A6).copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.width * 1.5f
                    ),
                    center = Offset(0f, 0f)
                )
                // Middle-right purple glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.4f),
                        radius = size.width * 1.2f
                    ),
                    center = Offset(size.width, size.height * 0.4f)
                )
                // Bottom-center amber glow (subtle)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height),
                        radius = size.width * 0.8f
                    ),
                    center = Offset(size.width * 0.5f, size.height)
                )
            }
    )
}