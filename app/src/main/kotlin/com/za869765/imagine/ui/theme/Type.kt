package com.za869765.imagine.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Sans-serif: defaults to system (Noto Sans CJK on most CN/TW Android devices,
// including One UI 8.0). We can swap to bundled Noto Sans TC later if needed.
val SansFamily: FontFamily = FontFamily.Default

// Monospace: used for amounts and API keys — Roboto Mono is on most devices,
// system mono is the fallback.
val MonoFamily: FontFamily = FontFamily.Monospace

val ImagineTypography: Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W600,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W600,
        fontSize = 45.sp, lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W600,
        fontSize = 36.sp, lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W600,
        fontSize = 32.sp, lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W600,
        fontSize = 28.sp, lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W600,
        fontSize = 24.sp, lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W600,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W500,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W500,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W400,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W400,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W400,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W500,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W500,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.W500,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)

// Mono text style helpers — for currency amounts and API key display.
val MonoAmount: TextStyle = TextStyle(
    fontFamily = MonoFamily, fontWeight = FontWeight.W600,
    fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.01).sp,
)

val MonoBody: TextStyle = TextStyle(
    fontFamily = MonoFamily, fontWeight = FontWeight.W400,
    fontSize = 14.sp, lineHeight = 20.sp,
)
