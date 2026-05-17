package com.za869765.imagine.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Material 3 Light palette — mirrors imagine-tokens.js §light
val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    surfaceDim = Color(0xFFF2EFF4),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1D1B20),
    scrim = Color(0x52000000),
)

// Material 3 Dark palette — mirrors imagine-tokens.js §dark
val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    surfaceDim = Color(0xFF141218),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    scrim = Color(0x80000000),
)

// Budget state colors — distinct from Material 3 semantic colors.
// Mirrors imagine-tokens.js §budgetState. Exposed via LocalBudgetColors.
data class BudgetColors(
    val ok: Color,
    val warn: Color,
    val high: Color,
    val over: Color,
)

val LightBudgetColors = BudgetColors(
    ok = Color(0xFF10B981),
    warn = Color(0xFFF59E0B),
    high = Color(0xFFEF4444),
    over = Color(0xFF991B1B),
)

val DarkBudgetColors = BudgetColors(
    ok = Color(0xFF34D399),
    warn = Color(0xFFFBBF24),
    high = Color(0xFFF87171),
    over = Color(0xFFDC2626),
)
