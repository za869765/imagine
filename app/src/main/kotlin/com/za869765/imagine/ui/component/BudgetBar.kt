package com.za869765.imagine.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.theme.LocalBudgetColors
import kotlin.math.min
import kotlin.math.roundToInt

// APP signature element — see imagine-components.jsx BudgetBar + UI.md §4.1
// Height 56dp, sticky beneath app bar.
@Composable
fun BudgetBar(
    spent: Double,
    cap: Double = 20.0,
    modifier: Modifier = Modifier,
) {
    val ratio = (spent / cap).toFloat()
    val pct = min(ratio, 1f)
    val over = spent > cap
    val budgetColors = LocalBudgetColors.current

    val stateColor = when {
        ratio > 1f -> budgetColors.over
        ratio > 0.9f -> budgetColors.high
        ratio > 0.7f -> budgetColors.warn
        else -> budgetColors.ok
    }

    val animatedColor by animateColorAsState(
        targetValue = stateColor,
        animationSpec = tween(durationMillis = 500),
        label = "budgetColor",
    )
    val animatedPct by animateFloatAsState(
        targetValue = pct,
        animationSpec = tween(durationMillis = 500),
        label = "budgetPct",
    )

    val pctText = (ratio * 100).roundToInt().toString() + "%"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Left: amount / cap (mono, mixed weight)
        Text(
            text = formatBudgetAmount(spent, cap),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.W600,
            letterSpacing = (-0.01).sp,
        )

        // Middle: progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedPct)
                    .clip(RoundedCornerShape(6.dp))
                    .background(animatedColor),
            )
        }

        // Right: percentage label, colored by state
        Text(
            text = if (over) "⚠ $pctText" else pctText,
            color = animatedColor,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.W600,
        )
    }
}

private fun formatBudgetAmount(spent: Double, cap: Double): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.W600)) {
        append("$" + "%.2f".format(spent))
    }
    append(" ")
    withStyle(SpanStyle(fontWeight = FontWeight.W400)) {
        append("/ $" + "%.2f".format(cap))
    }
}
