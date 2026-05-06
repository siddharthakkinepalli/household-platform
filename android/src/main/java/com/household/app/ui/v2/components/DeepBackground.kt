package com.household.app.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DeepBackground(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07111F),
                        Color(0xFF0F172A),
                        Color(0xFF111827)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-48).dp, y = (-24).dp)
                .size(220.dp)
                .blur(72.dp)
                .background(Color(0xFF22C55E).copy(alpha = 0.20f))
        )
        Box(
            modifier = Modifier
                .offset(x = 220.dp, y = 96.dp)
                .size(180.dp)
                .blur(72.dp)
                .background(Color(0xFF60A5FA).copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .offset(x = 80.dp, y = 420.dp)
                .size(240.dp)
                .blur(84.dp)
                .background(Color(0xFFF97316).copy(alpha = 0.14f))
        )
    }
}