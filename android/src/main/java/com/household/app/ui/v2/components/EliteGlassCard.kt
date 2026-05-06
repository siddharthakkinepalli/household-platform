package com.household.app.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.household.app.ui.compose.theme.GlassStroke
import com.household.app.ui.compose.theme.SurfaceNavy

@Composable
fun EliteGlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .blur(radius = 8.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.08f), Color.Transparent)
                )
            )
            .background(SurfaceNavy.copy(alpha = 0.6f))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                    start = Offset(0f, 0f),
                    end = Offset(200f, 200f)
                ),
                shape = RoundedCornerShape(32.dp)
            )
    ) {
        if (glowColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(0f, 0f)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}