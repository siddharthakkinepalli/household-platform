package com.household.app.ui.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Standardized glass card — strict 40% alpha on the Material surface color.
 * Use this instead of ad-hoc `background(color.copy(alpha = X))` patterns.
 * For cards that need a glow effect, use EliteGlassCard instead.
 */
@Composable
fun JugaadGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.40f)
    )
    val border = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    )
    if (onClick != null) {
        Card(modifier = modifier, colors = colors, border = border, onClick = onClick) { content() }
    } else {
        Card(modifier = modifier, colors = colors, border = border) { content() }
    }
}
