package com.za869765.imagine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// Custom CompositionLocals for tokens outside the Material 3 spec.
val LocalBudgetColors = staticCompositionLocalOf { LightBudgetColors }
val LocalSpacing = staticCompositionLocalOf { ImagineSpacing }
val LocalIsDark = staticCompositionLocalOf { false }

// 8dp grid — mirrors imagine-tokens.js §s
object ImagineSpacing {
    val xxs = 4
    val xs = 8
    val sm = 12
    val md = 16
    val lg = 24
    val xl = 32
    val xxl = 48
}

@Composable
fun ImagineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val budgetColors = if (darkTheme) DarkBudgetColors else LightBudgetColors

    CompositionLocalProvider(
        LocalBudgetColors provides budgetColors,
        LocalIsDark provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ImagineTypography,
            shapes = ImagineShapes,
            content = content,
        )
    }
}
