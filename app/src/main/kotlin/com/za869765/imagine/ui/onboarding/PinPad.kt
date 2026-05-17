package com.za869765.imagine.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PinAuxKey { Backspace, Confirm, Biometric, None }

@Composable
fun PinPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onBiometric: () -> Unit = {},
    leadingKey: PinAuxKey = PinAuxKey.Backspace,    // bottom-left
    trailingKey: PinAuxKey = PinAuxKey.Confirm,     // bottom-right
    filled: Int = 0,
    modifier: Modifier = Modifier,
) {
    // Layout: 1 2 3 / 4 5 6 / 7 8 9 / [leading] 0 [trailing]
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (row in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    for (col in 0..2) {
                        val n = row * 3 + col + 1
                        NumberKey(n) { onDigit(n) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                AuxKey(
                    key = leadingKey,
                    filled = filled,
                    onBackspace = onBackspace,
                    onConfirm = onConfirm,
                    onBiometric = onBiometric,
                )
                NumberKey(0) { onDigit(0) }
                AuxKey(
                    key = trailingKey,
                    filled = filled,
                    onBackspace = onBackspace,
                    onConfirm = onConfirm,
                    onBiometric = onBiometric,
                )
            }
        }
    }
}

@Composable
private fun NumberKey(n: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = n.toString(),
            fontSize = 30.sp,
            fontWeight = FontWeight.W400,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AuxKey(
    key: PinAuxKey,
    filled: Int,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    onBiometric: () -> Unit,
) {
    when (key) {
        PinAuxKey.Backspace -> Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .clickable(onClick = onBackspace),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "刪除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        PinAuxKey.Confirm -> {
            val active = filled > 0
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable(enabled = active, onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "確認",
                    tint = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        PinAuxKey.Biometric -> Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .clickable(onClick = onBiometric),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Fingerprint,
                contentDescription = "指紋解鎖",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        PinAuxKey.None -> Box(modifier = Modifier.size(76.dp))
    }
}
