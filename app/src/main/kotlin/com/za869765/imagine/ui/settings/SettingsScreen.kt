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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.za869765.imagine.BuildConfig
import com.za869765.imagine.applyScreenshotFlag
import android.content.Intent
import android.widget.Toast
import com.za869765.imagine.data.backup.KeyBackupCodec
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.CrashLogger
import com.za869765.imagine.data.storage.MediaImporter
import kotlinx.coroutines.launch
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.theme.LocalBudgetColors

@Composable
fun SettingsScreen(
    onApiKeyClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onClearedAndReset: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val budgetColors = LocalBudgetColors.current
    val scope = rememberCoroutineScope()

    var screenshots by remember { mutableStateOf(prefs.preventScreenshots) }

    var showClearDataConfirm by remember { mutableStateOf(false) }

    // ── Settings 狀態 ─────────────────────
    var showImportEditor by remember { mutableStateOf(false) }

    // v1.0.46: 從相簿批次匯入歷史 (PhotoPicker，不需 READ_MEDIA_* permission)
    // v1.0.48: importAll 回傳 List<String>，用 .size 拿 count
    val pickMultipleMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val count = MediaImporter.importAll(ctx, uris).size
                Toast.makeText(ctx, "已匯入 $count 個檔到 History", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun doExport() {
        val payload = KeyBackupCodec.export(prefs)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "imagine_keys.csv")
            putExtra(Intent.EXTRA_TITLE, "imagine_keys.csv")
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        ctx.startActivity(Intent.createChooser(intent, "匯出 Keys (CSV)"))
    }

    fun doImport(jsonStr: String): Boolean {
        return try {
            KeyBackupCodec.importInto(prefs, jsonStr)
            Toast.makeText(ctx, "已匯入", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Throwable) {
            Toast.makeText(ctx, "匯入失敗:${e.message?.take(120)}", Toast.LENGTH_LONG).show()
            false
        }
    }

    // v1.0.29 砍 GitHub PAT 整段 — repo 改 public 後 in-app updater 不需 token

    if (showImportEditor) {
        SimpleStringEditDialog(
            title = "匯入 Keys (CSV)",
            hint = "貼上 CSV：每行 key,value (支援 api_key / management_key / team_id / api_key_verified_at)。也接受舊版 JSON",
            current = "",
            mask = false,
            multiLine = true,  // v1.0.54 B4: CSV 多行必開
            onDismiss = { showImportEditor = false },
            onSave = { input ->
                if (input.isBlank()) { showImportEditor = false; return@SimpleStringEditDialog }
                if (doImport(input)) showImportEditor = false
            },
        )
    }

    if (showClearDataConfirm) {
        com.za869765.imagine.ui.dialog.ClearDataDialog(
            onCancel = { showClearDataConfirm = false },
            onConfirm = {
                showClearDataConfirm = false
                onClearedAndReset()
            },
        )
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "設定", showBack = true, onBackClick = onBack, trailing = { Box(modifier = Modifier.size(48.dp)) }) },
        showBalanceBar = false,
        bottomNav = null,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── 素材庫 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("素材")
                ImagineCard(pad = 0, onClick = onLibraryClick) {
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
                                name = "history",
                                size = 22.dp,
                                fill = 1,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "素材庫",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "瀏覽生成過的圖片與影片(歷史)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

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

            // ── xAI 後台（用量 / 帳單 / Grok）──
            // v1.0.21: 砍 Management API 拉真實餘額 (xAI 回傳單位不準怎樣都對不上),
            // 改成直接開 console.x.ai 用量頁。Token / 計算都用 xAI 後台原生。
            // v1.0.46: 加 grok.com 入口 — 看歷史/分享回 Imagine 走系統瀏覽器
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("xAI 後台")
                ImagineCard(pad = 0) {
                    Column {
                        SettingRow(divider = true, onClick = {
                            val url = "https://console.x.ai/team/02192454-54ee-4835-9680-212eda8ba708/usage?category=image"
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "在 console.x.ai 看用量 / 帳單",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.W500,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "瀏覽器開啟 — token / 圖片 / 影片數據以官方為準",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            ImagineIcon(name = "open_in_new", size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SettingRow(divider = false, onClick = {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://grok.com"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "開啟 grok.com",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.W500,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "瀏覽器開啟 — 看 Grok 歷史，按分享回傳 Imagine",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            ImagineIcon(name = "open_in_new", size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── 安全 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("安全")
                ImagineCard(pad = 0, onClick = null) {
                    Column {
                        SettingRow(divider = false) {
                            Text("防止截圖與錄影", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Switch(checked = screenshots, onCheckedChange = {
                                screenshots = it
                                prefs.preventScreenshots = it
                                (ctx as? android.app.Activity)?.let { act ->
                                    applyScreenshotFlag(act, it)
                                }
                            })
                        }
                    }
                }
            }

            // ── 歷史匯入 ──
            // v1.0.46: 從相簿 (PhotoPicker，不需 permission) 或 Grok/瀏覽器 ACTION_SEND 分享進來
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("歷史匯入")
                ImagineCard(pad = 16) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "從相簿多選圖/影匯入 History (無 prompt)。Grok / 瀏覽器內按「分享」也能直接送進 Imagine。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                        OutlinedActionButton(
                            label = "從相簿匯入",
                            onClick = {
                                pickMultipleMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ── 背景任務 ──
            // v1.0.54 (b): 跳系統 app 詳細頁，讓 user 把 Imagine 排除 Samsung/Android Doze
            // 最佳化，避免影片背景生成 worker 被殺
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("背景任務")
                ImagineCard(pad = 16) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "影片生成走背景 worker，會被系統電池優化殺掉。建議到 Android 設定 → 應用程式 → Imagine → 電池 → 改「不限制」(Samsung 機在「最佳化」選項)。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                        OutlinedActionButton(
                            label = "開啟系統設定",
                            onClick = {
                                runCatching {
                                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", ctx.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    ctx.startActivity(intent)
                                }.onFailure {
                                    Toast.makeText(ctx, "開啟設定失敗", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ── 除錯 ──
            // v1.0.50: 把 CrashLogger 記到 filesDir/crash.log 的 stack trace 分享給開發者
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("除錯")
                ImagineCard(pad = 16) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "閃退或操作異常時，按「分享錯誤記錄」把內部 log 傳給開發者 diagnose (純文字 stack trace，不含 API key)。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedActionButton(
                                label = "分享錯誤記錄",
                                onClick = {
                                    val text = CrashLogger.readAll(ctx)
                                    if (text.isBlank()) {
                                        Toast.makeText(ctx, "目前無錯誤記錄", Toast.LENGTH_SHORT).show()
                                    } else {
                                        ctx.startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "imagine crash log")
                                                    putExtra(Intent.EXTRA_TEXT, text)
                                                },
                                                "分享錯誤記錄",
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedActionButton(
                                label = "清空",
                                onClick = {
                                    CrashLogger.clear(ctx)
                                    Toast.makeText(ctx, "已清空錯誤記錄", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── Keys 備份 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Keys 備份")
                ImagineCard(pad = 16) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "CSV 格式：key,value 兩欄。匯出走系統分享面板分享到 Drive / Keep / Email (避免剪貼簿)；匯入貼上 CSV 內容 (也兼容舊版 JSON 匯出)。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedActionButton(
                                label = "匯出",
                                onClick = { doExport() },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedActionButton(
                                label = "匯入",
                                onClick = { showImportEditor = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── 危險區 (collapsed by default 避免誤按) ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var dangerExpanded by remember { mutableStateOf(false) }

                // 收合 header — 低調灰色 outline 一行；點才展開
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { dangerExpanded = !dangerExpanded }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "⚠️ 危險區",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W500,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        ImagineIcon(
                            name = if (dangerExpanded) "expand_less" else "expand_more",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 展開後才出現紅色「清除所有資料」按鈕
                if (dangerExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .clickable { showClearDataConfirm = true },
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
                                name = "chevron_right",
                                size = 22.dp,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
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
                    "Imagine v${BuildConfig.VERSION_NAME}",
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

private fun maskKey(key: String?): String {
    if (key.isNullOrBlank()) return "未設定"
    if (key.length <= 8) return key
    return key.take(4) + "•".repeat(13) + key.takeLast(3)
}

@Composable
private fun SimpleStringEditDialog(
    title: String,
    current: String,
    hint: String,
    mask: Boolean,
    multiLine: Boolean = false,  // v1.0.54 B4: CSV 等多行內容需 multi-line
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var input by remember { mutableStateOf(current) }
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
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    hint,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
                        singleLine = !multiLine,  // v1.0.54 B4
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextActionButton(label = "清除", onClick = { onSave("") })
                    Spacer(modifier = Modifier.width(8.dp))
                    TextActionButton(label = "取消", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedActionButton(label = "儲存", onClick = { onSave(input.trim()) })
                }
            }
        }
    }
}
