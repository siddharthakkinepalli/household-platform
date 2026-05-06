package com.household.app.ui.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.05f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite
                ),
                shape = RoundedCornerShape(30.dp)
            ),
        color = Color.White.copy(alpha = 0.03f),
        tonalElevation = 0.dp
    ) {
        Box {
            if (glowColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(glowColor.copy(alpha = 0.15f), Color.Transparent),
                                center = Offset(0f, 0f),
                                radius = 800f
                            )
                        )
                )
            }

            Column(
                modifier = Modifier.padding(22.dp),
                content = content
            )
        }
    }
}