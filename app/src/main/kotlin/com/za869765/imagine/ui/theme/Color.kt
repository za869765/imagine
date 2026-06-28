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

// ── 重設計強制深色配色 (Claude Design handoff「Imagine 重設計」) ──
// 把 M3 colorScheme 角色映射到設計稿的深色 token,讓全 App 既有畫面(用 colorScheme 取色者)
// 立刻翻成新深色基底;各畫面/元件再逐批精修版面。
// 設計 token：canvas #0D0E12 / nav #101117 / card #131419 / tile #17181D / elevated #1A1B20
//            主色紫 #B7A0FF(on #1E1140) / 文字 #ECECF0·#9A9AA6·#62636E
val RedesignDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB7A0FF),
    onPrimary = Color(0xFF1E1140),
    primaryContainer = Color(0xFF231F38),
    onPrimaryContainer = Color(0xFFC9B8FF),
    inversePrimary = Color(0xFF5B4A9E),
    secondary = Color(0xFFB7A0FF),
    onSecondary = Color(0xFF1E1140),
    secondaryContainer = Color(0xFF2A2545),
    onSecondaryContainer = Color(0xFFC9B8FF),
    tertiary = Color(0xFF56E0D2),
    onTertiary = Color(0xFF06302B),
    tertiaryContainer = Color(0xFF14463F),
    onTertiaryContainer = Color(0xFF7FE9DD),
    error = Color(0xFFF0A0A0),
    onError = Color(0xFF3A1414),
    errorContainer = Color(0xFF3A1E22),
    onErrorContainer = Color(0xFFF3B6B6),
    background = Color(0xFF0D0E12),
    onBackground = Color(0xFFECECF0),
    surface = Color(0xFF0D0E12),
    onSurface = Color(0xFFECECF0),
    surfaceVariant = Color(0xFF17181D),
    onSurfaceVariant = Color(0xFF9A9AA6),
    surfaceTint = Color(0xFFB7A0FF),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF101117),
    surfaceContainer = Color(0xFF101117),
    surfaceContainerHigh = Color(0xFF131419),
    surfaceContainerHighest = Color(0xFF1A1B20),
    surfaceDim = Color(0xFF0A0A0E),
    surfaceBright = Color(0xFF1F2026),
    outline = Color(0x1FFFFFFF),       // 12% 白 — picker/pill/chip 邊框
    outlineVariant = Color(0x12FFFFFF), // 7% 白 — 分隔線/淡邊框
    scrim = Color(0xCC000000),
    inverseSurface = Color(0xFFECECF0),
    inverseOnSurface = Color(0xFF1A1B20),
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
