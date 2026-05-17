package com.za869765.imagine.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Mirrors imagine-tokens.js §r — radii defaults
val ImagineShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),  // TextField, ParamPicker
    large = RoundedCornerShape(16.dp),   // Card
    extraLarge = RoundedCornerShape(28.dp), // Dialog, BottomSheet
)

// Custom shapes outside the M3 Shapes scale
object ImagineCustomShapes {
    val PillButton = RoundedCornerShape(24.dp)  // imagine-tokens.js r.btn
    val Chip = RoundedCornerShape(18.dp)        // r.chip
    val Media = RoundedCornerShape(12.dp)       // r.media (image/video preview)
}
