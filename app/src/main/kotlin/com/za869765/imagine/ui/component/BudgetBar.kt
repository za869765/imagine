package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.billing.BillingState
import com.za869765.imagine.data.prefs.SecurePrefs

// Top-of-screen strip showing the xAI prepaid balance + current month spend.
// Replaces the old local-budget tracker — xAI prepaid is the source of truth.
// Tap row to trigger a re-sync.
@Composable
fun XaiBalanceBar(
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()

    val balance by BillingState.balance
    val spent by BillingState.spent
    val syncing by BillingState.syncing
    val syncedAt by BillingState.syncedAt
    val error by BillingState.error
    val configured = prefs.isManagementSet

    LaunchedEffect(configured) {
        if (configured && balance == null && error == null) {
            BillingState.sync(prefs, scope)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = configured) { BillingState.sync(prefs, scope) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!configured) {
            Text(
                "未連接 xAI 後台 — 到設定 → xAI 後台填入 Management Key",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        append("餘額 ")
                        withStyle(SpanStyle(fontWeight = FontWeight.W700)) {
                            append(balance ?: "—")
                        }
                        append("   本期 ")
                        withStyle(SpanStyle(fontWeight = FontWeight.W700)) {
                            append(spent ?: "—")
                        }
                    },
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val sub = error ?: syncedAt?.let { "最後同步 $it" } ?: "尚未同步"
                Text(
                    sub.take(80),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ImagineIcon(
                name = "refresh",
                size = 20.dp,
                tint = if (syncing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
