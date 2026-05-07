package com.household.app.ui.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.household.app.R

private val RobotoFlexFamily = FontFamily(
    Font(R.font.roboto_flex, FontWeight.Normal),
    Font(R.font.roboto_flex, FontWeight.Medium),
    Font(R.font.roboto_flex, FontWeight.SemiBold),
    Font(R.font.roboto_flex, FontWeight.Bold)
)

/**
 * Typography scale.
 * Font: system Roboto (matches Roboto Flex visually; swap once roboto_flex.ttf is added to res/font).
 * Tabular nums MUST be applied per-call-site on currency amounts via:
 *   fontFeatureSettings = "tnum"
 */
val JugaadTypography = Typography(
    // Hero balance — 32sp SemiBold
    headlineLarge = TextStyle(
        fontFamily = RobotoFlexFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    // Screen titles — 20sp SemiBold
    headlineMedium = TextStyle(
        fontFamily = RobotoFlexFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    // Card section titles — 16sp Medium
    titleMedium = TextStyle(
        fontFamily = RobotoFlexFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    // Body text — 14sp Regular
    bodyLarge = TextStyle(
        fontFamily = RobotoFlexFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // Secondary body — 14sp Regular (same size, different color at call-site)
    bodyMedium = TextStyle(
        fontFamily = RobotoFlexFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // Meta / label — 12sp Regular
    bodySmall = TextStyle(
        fontFamily = RobotoFlexFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // Chip text — 12sp Medium
    labelMedium = TextStyle(
        fontFamily = RobotoFlexFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // Section headers — 11sp Medium (Title Case + letterSpacing applied per-call-site)
    labelSmall = TextStyle(
        fontFamily = RobotoFlexFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.08.sp
    )
)

val PremiumTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
