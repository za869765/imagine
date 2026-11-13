package com.za869765.imagine.ui.download

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.download.Downloader
import com.za869765.imagine.data.download.Downloader.Status
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar

/** 網址裡抓第一個 http(s) 連結(分享文字常是「標題 + 網址」) */
fun firstUrl(text: String?): String? =
    text?.let { Regex("""https?://\S+""").find(it)?.value?.trimEnd(')', '」', '。', ',') }

/** v3.2.0 下載分頁:貼網址 → 筆電代下載 → 影片存進所有作品、聲音存到手機「下載/Imagine」 */
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val tasks = Downloader.tasks.collectAsState().value.filterNot { it.url.startsWith(Downloader.LAPTOP_PREFIX) }  // 筆電測試片的在它自己的分頁顯示
    val shared by Downloader.sharedUrl.collectAsState()

    var url by rememberSaveable { mutableStateOf("") }
    // 0 = 影片 1080p, 1 = 影片 720p, 2 = 只要聲音
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(shared) {
        val s = shared ?: return@LaunchedEffect
        url = s
        Downloader.sharedUrl.value = null
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "下載", showBack = true, onBackClick = onBack, onSettingsClick = onSettingsClick) },
        bottomNav = null,  // v3.3.3 從設定頁進來,不在底部分頁裡
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("影片網址（YouTube、IG、TikTok、X、B站…）") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { firstUrl(clipboard.getText()?.text)?.let { url = it } ?: run { note = "剪貼簿裡沒有網址" } }) {
                        Text("貼上")
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("影片 1080p", "影片 720p", "只要聲音").forEachIndexed { i, label ->
                    FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label) })
                }
            }
            val link = firstUrl(url)
            Button(
                onClick = {
                    Downloader.start(ctx, link!!, audio = mode == 2, height = if (mode == 1) 720 else 1080)
                    url = ""
                    note = null
                },
                enabled = link != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("開始下載") }
            Text(
                "由筆電代下載（筆電要開著、工人要在跑），檔案經 Cloudflare 暫存傳回手機後就刪掉，不經雲端硬碟。" +
                    "影片存進「所有作品」，聲音（MP3）存到手機「下載／Imagine」資料夾。" +
                    "也可以在 YouTube、IG 等 APP 按「分享」→ Imagine 直接帶網址過來。要登入才能看的內容目前下載不到。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            note?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }

            tasks.forEach { t -> TaskCard(t, onOpenVideo) }
        }
    }
}

@Composable
private fun TaskCard(t: Downloader.Task, onOpenVideo: (String) -> Unit) {
    val ctx = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (t.audio) "🎵 " else "🎬 ") + t.title,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when (t.status) {
                Status.RUNNING -> buildString {
                    append(t.stage)
                    if (t.progress > 0f) append(" ${t.progress.toInt()}%")
                    if (t.etaSec > 0) append("・剩 ${if (t.etaSec >= 60) "${t.etaSec / 60} 分 ${t.etaSec % 60} 秒" else "${t.etaSec} 秒"}")
                }
                Status.DONE -> if (t.audio) "完成，已存到手機「下載／Imagine」" else "完成，已存進所有作品"
                Status.FAILED -> "失敗：${t.error}"
                Status.CANCELLED -> "已取消"
            }
            Text(
                status,
                fontSize = 12.sp,
                color = if (t.status == Status.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (t.status == Status.RUNNING) {
                if (t.progress > 0f) {
                    LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (t.status) {
                    Status.RUNNING -> TextButton(onClick = { Downloader.cancel(ctx, t.id) }) { Text("取消") }
                    Status.DONE -> {
                        TextButton(onClick = {
                            val u = t.savedUri ?: return@TextButton
                            if (!t.audio) {
                                onOpenVideo(u)
                            } else {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(u), "audio/mpeg")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    )
                                }
                            }
                        }) { Text(if (t.audio) "播放" else "看影片") }
                        TextButton(onClick = { Downloader.remove(t.id) }) { Text("從清單移除") }
                    }
                    else -> {
                        TextButton(onClick = { Downloader.remove(t.id) }) { Text("從清單移除") }
                    }
                }
            }
        }
    }
}
