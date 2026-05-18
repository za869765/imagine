package com.za869765.imagine.ui.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.theme.LocalBudgetColors
import java.time.LocalDate

@Composable
fun ApiKeyEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val budgetColors = LocalBudgetColors.current

    var showFull by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    val canSave = newKey.startsWith("xai-") && newKey.length > 8

    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "API Key",
                showBack = true,
                onBackClick = onBack,
                trailing = { Box(modifier = Modifier.size(48.dp)) },
            )
        },
        showBalanceBar = false,
        bottomNav = null,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 目前 Key Card
            ImagineCard {
                Column {
                    Text(
                        "目前 KEY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        letterSpacing = 0.08.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (showFull) prefs.apiKey ?: "未設定" else maskKey(prefs.apiKey),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.02.sp,
                    )
                    if (!prefs.apiKey.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            ImagineIcon(name = "check", size = 14.dp, fill = 1, tint = budgetColors.ok)
                            Text(
                                "已驗證 · ${prefs.apiKeyVerifiedAt ?: "—"}",
                                fontSize = 12.sp,
                                color = budgetColors.ok,
                            )
                        }
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextActionButton(label = "複製", icon = "content_copy", onClick = {
                                copyToClipboard(ctx, prefs.apiKey ?: "")
                            })
                            TextActionButton(
                                label = if (showFull) "遮蔽 Key" else "顯示完整 Key",
                                icon = "visibility",
                                onClick = { showFull = !showFull },
                            )
                        }
                    }
                }
            }

            // 變更為新 Key
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("變更為新 Key")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = newKey,
                        onValueChange = { newKey = it.trim() },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (newKey.isEmpty()) {
                                Text(
                                    "xai-...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                )
                            }
                            inner()
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextActionButton(
                        label = "從剪貼簿貼上",
                        icon = "content_copy",
                        onClick = { readClipboard(ctx)?.let { newKey = it } },
                    )
                }
            }

            PrimaryButton(
                label = "驗證並儲存",
                icon = "check",
                enabled = canSave,
                onClick = {
                    prefs.apiKey = newKey
                    prefs.apiKeyVerifiedAt = LocalDate.now().toString()
                    onSaved()
                },
            )

            // 危險區 - 移除 Key
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ImagineIcon(name = "key", size = 22.dp, fill = 1, tint = MaterialTheme.colorScheme.error)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "移除 API Key",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "移除後將無法使用本 APP，需重新設定",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(modifier = Modifier.clickable(onClick = onRemove)) {
                        ImagineIcon(name = "expand_more", size = 22.dp, tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Text(
                text = "Key 由 Android Keystore 加密儲存於本機，\n不會傳送到任何第三方伺服器。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private fun maskKey(key: String?): String {
    if (key.isNullOrBlank()) return "未設定"
    if (key.length <= 8) return key
    return key.take(4) + "•".repeat(13) + key.takeLast(3)
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cb.setPrimaryClip(android.content.ClipData.newPlainText("API Key", text))
}

private fun readClipboard(ctx: Context): String? {
    val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    return cb.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
}
