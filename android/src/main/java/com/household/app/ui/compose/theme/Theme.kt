package com.household.app.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class JugaadThemeSelection {
    LUMINESCENT_GLASS,
    JUGAAD_CHILLI,
    NORDIC_EINKAUF,
    MATRIX_PIPELINE,
    MONSOON_FOREST,
    TWILIGHT_CASHMERE
}

// ── Colour schemes ────────────────────────────────────────────────────────────

private val LuminescentGlassColors = darkColorScheme(
    background       = Color(0xFF0B0E14),
    surface          = Color(0xFF161B22),
    primary          = LumeCyan,
    secondary        = LumeEmerald,
    tertiary         = LumeAmber,
    onPrimary        = TextOnColor,
    onSecondary      = TextOnColor,
    onTertiary       = Color(0xFF0B0E14),
    onBackground     = TextMain,
    onSurface        = TextMain,
    onSurfaceVariant = TextSecondaryDark,
    outline          = EliteGlassBorder,
    error            = CriticalRed,
    onError          = TextOnColor
)

private val JugaadChilliColors = darkColorScheme(
    background       = Color(0xFF120D0D),
    surface          = Color(0xFF1C1414),
    primary          = Color(0xFFD32F2F),
    secondary        = Color(0xFFFFB300),
    tertiary         = Color(0xFFFF7043),
    onPrimary        = TextOnColor,
    onSecondary      = Color(0xFF120D0D),
    onTertiary       = TextOnColor,
    onBackground     = TextMain,
    onSurface        = TextMain,
    onSurfaceVariant = TextSecondaryDark,
    outline          = Color(0x33FF7043),
    error            = CriticalRed,
    onError          = TextOnColor
)

private val NordicEinkaufColors = lightColorScheme(
    background       = Color(0xFFF8F9FA),
    surface          = Color(0xFFECEFF1),
    primary          = Color(0xFF1A237E),
    secondary        = Color(0xFF00796B),
    tertiary         = Color(0xFFF57F17),
    onPrimary        = TextOnColor,
    onSecondary      = TextOnColor,
    onTertiary       = TextOnColor,
    onBackground     = Color(0xFF121212),
    onSurface        = Color(0xFF121212),
    onSurfaceVariant = Color(0xFF4A4A6A),
    outline          = Color(0xFFCFD8DC),
    error            = Color(0xFFD32F2F),
    onError          = TextOnColor
)

// Terminal green — matrix-style dark mode
private val MatrixPipelineColors = darkColorScheme(
    background       = Color(0xFF000D00),
    surface          = Color(0xFF001A00),
    primary          = Color(0xFF00FF41),
    secondary        = Color(0xFF00CC33),
    tertiary         = Color(0xFF4ADE80),
    onPrimary        = Color(0xFF000D00),
    onSecondary      = Color(0xFF000D00),
    onTertiary       = Color(0xFF000D00),
    onBackground     = Color(0xFFCCFFCC),
    onSurface        = Color(0xFFCCFFCC),
    onSurfaceVariant = Color(0xFF99EE99),
    outline          = Color(0x3300FF41),
    error            = Color(0xFFFF453A),
    onError          = TextOnColor
)

// Deep teal — earthy monsoon palette
private val MonsoonForestColors = darkColorScheme(
    background       = Color(0xFF0D1B1E),
    surface          = Color(0xFF112428),
    primary          = Color(0xFF26A69A),
    secondary        = Color(0xFF66BB6A),
    tertiary         = Color(0xFF80DEEA),
    onPrimary        = TextOnColor,
    onSecondary      = Color(0xFF0D1B1E),
    onTertiary       = Color(0xFF0D1B1E),
    onBackground     = Color(0xFFE0F2F1),
    onSurface        = Color(0xFFE0F2F1),
    onSurfaceVariant = Color(0xFFB2DFDB),
    outline          = Color(0x3326A69A),
    error            = Color(0xFFFF6B6B),
    onError          = TextOnColor
)

// Warm lavender-rose — soft premium dark mode
private val TwilightCashmereColors = darkColorScheme(
    background       = Color(0xFF120B17),
    surface          = Color(0xFF1E1226),
    primary          = Color(0xFFB39DDB),
    secondary        = Color(0xFFF48FB1),
    tertiary         = Color(0xFFFFCC80),
    onPrimary        = Color(0xFF120B17),
    onSecondary      = Color(0xFF120B17),
    onTertiary       = Color(0xFF120B17),
    onBackground     = Color(0xFFF3E5F5),
    onSurface        = Color(0xFFF3E5F5),
    onSurfaceVariant = Color(0xFFCE93D8),
    outline          = Color(0x33B39DDB),
    error            = Color(0xFFCF6679),
    onError          = TextOnColor
)

// ── Theme composables ─────────────────────────────────────────────────────────

/**
 * Primary app theme. Accepts a [JugaadThemeSelection] so any composable tree can
 * be wrapped with the user's persisted choice. Default keeps the Luminescent Glass
 * look for preview/legacy call-sites that pass no argument.
 */
@Composable
fun JugaadTheme(
    themeSelection: JugaadThemeSelection = JugaadThemeSelection.LUMINESCENT_GLASS,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeSelection) {
        JugaadThemeSelection.LUMINESCENT_GLASS -> LuminescentGlassColors
        JugaadThemeSelection.JUGAAD_CHILLI     -> JugaadChilliColors
        JugaadThemeSelection.NORDIC_EINKAUF    -> NordicEinkaufColors
        JugaadThemeSelection.MATRIX_PIPELINE   -> MatrixPipelineColors
        JugaadThemeSelection.MONSOON_FOREST    -> MonsoonForestColors
        JugaadThemeSelection.TWILIGHT_CASHMERE -> TwilightCashmereColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = PremiumTypography,
        content     = content
    )
}

/** Backward-compat alias used by V2AppShell — delegates to JugaadTheme. */
@Composable
fun HouseholdPlatformTheme(
    themeSelection: JugaadThemeSelection = JugaadThemeSelection.LUMINESCENT_GLASS,
    content: @Composable () -> Unit
) = JugaadTheme(themeSelection, content)
