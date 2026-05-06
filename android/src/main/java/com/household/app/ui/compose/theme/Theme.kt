package com.household.app.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val JugaadColorScheme = lightColorScheme(
    primary          = Green,
    onPrimary        = TextOnColor,
    secondary        = Purple,
    onSecondary      = TextOnColor,
    tertiary         = Orange,
    onTertiary       = TextOnColor,
    background       = BgBase,
    onBackground     = TextPrimary,
    surface          = Surface,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline          = Border,
    error            = Red,
    onError          = TextOnColor
)

@Composable
fun JugaadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JugaadColorScheme,
        typography  = JugaadTypography,
        content     = content
    )
}
