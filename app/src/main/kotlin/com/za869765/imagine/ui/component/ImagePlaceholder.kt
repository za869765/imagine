package com.za869765.imagine.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

// Diagonal-striped placeholder block for image/video previews
// Mirrors imagine-components.jsx ImagePlaceholder
@Composable
fun ImagePlaceholder(
    modifier: Modifier = Modifier,
    aspect: Float? = 16f / 9f,
    height: Dp? = null,
    label: String? = null,
    video: Boolean = false,
    cornerRadius: Dp = 12.dp,
) {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val stripeColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val outline = MaterialTheme.colorScheme.outlineVariant

    val sizingModifier = when {
        height != null -> Modifier.height(height).fillMaxWidth()
        aspect != null -> Modifier.fillMaxWidth().aspectRatio(aspect)
        else -> Modifier.fillMaxSize()
    }

    Box(
        modifier = modifier
            .then(sizingModifier)
            .clip(RoundedCornerShape(cornerRadius))
            .background(baseColor)
            .border(1.dp, outline, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Diagonal stripes at 135°
            rotate(degrees = 135f) {
                val stripeWidth = 12.dp.toPx()
                val gap = 12.dp.toPx()
                val diag = sqrt(size.width * size.width + size.height * size.height)
                var x = -diag
                while (x < diag) {
                    drawRect(
                        color = stripeColor,
                        topLeft = Offset(x + stripeWidth, -diag / 2),
                        size = androidx.compose.ui.geometry.Size(stripeWidth, diag * 2),
                    )
                    x += stripeWidth + gap
                }
            }
        }

        if (video) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                ImagineIcon(
                    name = "play_arrow",
                    size = 32.dp,
                    fill = 1,
                    tint = Color.White,
                )
            }
        }

        if (label != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W500,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
