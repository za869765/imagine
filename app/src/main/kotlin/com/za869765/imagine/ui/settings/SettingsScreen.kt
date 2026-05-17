package com.za869765.imagine.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.theme.LocalBudgetColors
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onApiKeyClick: () -> Unit,
    onChangePinClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val budgetColors = LocalBudgetColors.current

    var biometric by remember { mutableStateOf(prefs.biometricEnabled) }
    var lockOnBg by remember { mutableStateOf(prefs.lockOnBackground) }
    var screenshots by remember { mutableStateOf(prefs.preventScreenshots) }
    var lockOnLimit by remember { mutableStateOf(prefs.lockOnLimit) }
    var autoReset by remember { mutableStateOf(prefs.autoResetMonthly) }

    // 數字類欄位包成 mutableStateOf 才能即時刷新 UI
    var budgetCap by remember { mutableStateOf(prefs.budgetCap) }
    var spent by remember { mutableStateOf(prefs.spent) }
    var imageCount by remember { mutableStateOf(prefs.imageCount) }
    var videoSeconds by remember { mutableStateOf(prefs.videoSeconds) }

    var showBudgetEditor by remember { mutableStateOf(false) }

    if (showBudgetEditor) {
        BudgetEditDialog(
            current = budgetCap,
            onDismiss = { showBudgetEditor = false },
            onSave = { newCap ->
                budgetCap = newCap
                prefs.budgetCap = newCap
                showBudgetEditor = false
            },
        )
    }

    val cap = budgetCap
    val ratio = (spent / cap).toFloat().coerceAtMost(1f)
    val pctText = (ratio * 100).roundToInt().toString() + "%"

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "設定", trailing = { Box(modifier = Modifier.size(48.dp)) }) },
        showBudgetBar = false,
        bottomNav = { ImagineBottomNav(active = NavTab.SETTINGS, onTabSelected = onNavSelected) },
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── API ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("API")
                ImagineCard(pad = 0, onClick = onApiKeyClick) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            ImagineIcon(
                                name = "key",
                                size = 22.dp,
                                fill = 1,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "API Key",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                maskKey(prefs.apiKey),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.02.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            if (!prefs.apiKey.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    ImagineIcon(name = "check", size = 14.dp, fill = 1, tint = budgetColors.ok)
                                    Text(
                                        "已驗證 · ${prefs.apiKeyVerifiedAt ?: "—"}",
                                        fontSize = 12.sp,
                                        color = budgetColors.ok,
                                    )
                                }
                            }
                        }
                        ImagineIcon(name = "expand_more", size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── 預算控制 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("預算控制")
                ImagineCard(pad = 0) {
                    Column {
                        SettingRow(divider = true, onClick = { showBudgetEditor = true }) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("預算上限", fontSize = 15.sp, fontWeight = FontWeight.W500, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "$" + "%.2f".format(budgetCap),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.W600,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            TextActionButton(label = "編輯", onClick = { showBudgetEditor = true })
                        }
                        SettingRow(divider = true) {
                            Text("達上限時鎖定生成", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Switch(checked = lockOnLimit, onCheckedChange = {
                                lockOnLimit = it; prefs.lockOnLimit = it
                            })
                        }
                        SettingRow(divider = false) {
                            Text("每月 1 號自動重設", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Switch(checked = autoReset, onCheckedChange = {
                                autoReset = it; prefs.autoResetMonthly = it
                            })
                        }
                    }
                }
            }

            // ── 本期用量 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("本期用量（${prefs.periodStart} 起）")
                ImagineCard(pad = 16) {
                    Column {
                        UsageRow("圖片", "$imageCount 張 × \$0.05", "$" + "%.2f".format(imageCount * 0.05))
                        Spacer(modifier = Modifier.height(10.dp))
                        UsageRow("影片", "$videoSeconds 秒 × \$0.05", "$" + "%.2f".format(videoSeconds * 0.05))
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text("合計", fontSize = 15.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "$" + "%.2f".format(spent),
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.W700,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(min(ratio, 1f))
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            ratio > 0.9f -> budgetColors.high
                                            ratio > 0.7f -> budgetColors.warn
                                            else -> budgetColors.ok
                                        }
                                    ),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "$" + "%.2f".format(spent) + " / $" + "%.2f".format(cap),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                pctText,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TextActionButton(
                                label = "立即重設用量",
                                icon = "history",
                                onClick = {
                                    prefs.resetUsage()
                                    spent = 0.0
                                    imageCount = 0
                                    videoSeconds = 0
                                },
                            )
                        }
                    }
                }
            }

            // ── 安全 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("安全")
                ImagineCard(pad = 0, onClick = null) {
                    Column {
                        SettingRow(divider = true, onClick = onChangePinClick) {
                            Text("變更 PIN", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            ImagineIcon(name = "expand_more", size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SettingRow(divider = true) {
                            Text("啟用生物辨識", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Switch(checked = biometric, onCheckedChange = {
                                biometric = it; prefs.biometricEnabled = it
                            })
                        }
                        SettingRow(divider = true) {
                            Text("APP 切背景立即鎖", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Switch(checked = lockOnBg, onCheckedChange = {
                                lockOnBg = it; prefs.lockOnBackground = it
                            })
                        }
                        SettingRow(divider = false) {
                            Text("防止截圖與錄影", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Switch(checked = screenshots, onCheckedChange = {
                                screenshots = it; prefs.preventScreenshots = it
                            })
                        }
                    }
                }
            }

            // ── 危險區 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("危險區")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable(onClick = onClearDataClick),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ImagineIcon(
                            name = "history",
                            size = 22.dp,
                            fill = 1,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "清除所有資料",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "API Key、用量、歷史全清",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        ImagineIcon(
                            name = "expand_more",
                            size = 22.dp,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // ── 關於 ──
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Imagine v1.0.0",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.W500,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "xAI Imagine API 客戶端",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "官方定價 ↗",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BudgetEditDialog(
    current: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var input by remember { mutableStateOf("%.0f".format(current)) }
    val parsed = input.toDoubleOrNull()
    val valid = parsed != null && parsed > 0 && parsed <= 9999

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "預算上限",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "輸入新的月預算（美元）",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$",
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = input,
                            onValueChange = { s ->
                                if (s.length <= 6 && s.all { it.isDigit() || it == '.' }) input = s
                            },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.W700,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextActionButton(label = "取消", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedActionButton(
                        label = "儲存",
                        enabled = valid,
                        onClick = { if (valid) onSave(parsed!!) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    divider: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun UsageRow(label: String, subText: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.W500, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            amount,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.W600,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun maskKey(key: String?): String {
    if (key.isNullOrBlank()) return "未設定"
    if (key.length <= 8) return key
    return key.take(4) + "•".repeat(13) + key.takeLast(3)
}
