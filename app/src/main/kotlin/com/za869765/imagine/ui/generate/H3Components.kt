package com.za869765.imagine.ui.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab

const val ENGINE_GROK = "grok"
const val ENGINE_H3 = "h3"

/** v2.0.7 生圖頁:「生成引擎：Grok Imagine ｜ H3 Studio（Qwen 生圖）」,同一組 id(grok / h3)。 */
@Composable
fun ImageEngineSelector(engine: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) =
    VideoEngineSelector(engine = engine, onSelect = onSelect, modifier = modifier, h3Label = "H3 Studio（Qwen 生圖）")

/** 生影頁 / 設定頁共用:「生成引擎：Grok Imagine ｜ H3 Studio」。 */
@Composable
fun VideoEngineSelector(engine: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, h3Label: String = "H3 Studio") {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "生成引擎",
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentedTab(
            options = listOf(
                SegmentedOption(ENGINE_GROK, "Grok Imagine"),
                SegmentedOption(ENGINE_H3, h3Label),
            ),
            activeId = engine,
            onSelected = onSelect,
            activeColor = if (engine == ENGINE_H3) Color(0xFF5B3FA8) else Color(0xFF14463F),
        )
    }
}

/** v2.1.9 機器看板:頁面看得到時每 20 秒向 H3 Studio 查一次(不花錢)。 */
@Composable
fun rememberMachineBoard(prefs: com.za869765.imagine.data.prefs.SecurePrefs): List<com.za869765.imagine.data.h3.H3Machine> {
    var board by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.za869765.imagine.data.h3.H3Machine>>(emptyList()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(owner) {
        if (!prefs.isH3TokenSet) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                (com.za869765.imagine.data.h3.H3Client.machines(prefs) as? com.za869765.imagine.data.h3.H3Client.Result.Ok)?.value?.let { board = it.machines }
                kotlinx.coroutines.delay(20_000)
            }
        }
    }
    return board
}

fun machineDotColor(state: String): Color = when (state) {
    "idle", "standby" -> Color(0xFF2E9E5B)
    "busy", "booting" -> Color(0xFFE0A100)
    else -> Color(0xFF9AA0A6)
}

/** 機器狀態卡:綠＝空閒待機／待機倒數(下單馬上做)、黃＝生成中／開機中(要排隊)、灰＝關機／離線。highlight＝目前選的那台。 */
@Composable
fun MachineBoardCard(board: List<com.za869765.imagine.data.h3.H3Machine>, routes: List<String>, highlight: String? = null) {
    val list = routes.mapNotNull { r -> board.firstOrNull { it.route == r } }
    if (list.isEmpty()) return
    com.za869765.imagine.ui.component.ImagineCard(pad = 12) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("機器狀態（每 20 秒更新）", fontSize = 12.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEach { m ->
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(10.dp).androidx_background(machineDotColor(m.state)),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${m.name}：${m.label}",
                            fontSize = 14.sp,
                            fontWeight = if (m.route == highlight) FontWeight.W700 else FontWeight.W500,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(m.detail, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun Modifier.androidx_background(c: Color): Modifier =
    this.then(Modifier.background(c, androidx.compose.foundation.shape.CircleShape))

/** 某台機器的一句狀態(給畫質／機器選項的說明用);沒有看板資料回 ""。 */
fun machineNote(board: List<com.za869765.imagine.data.h3.H3Machine>, route: String): String =
    board.firstOrNull { it.route == route }?.let { "・現在：${it.label}" } ?: ""

/** v2.1.9 素材小卡:收起時只顯示一行摘要,點開才展開(參考圖、角色、素材庫)。 */
@Composable
fun CollapsibleSection(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    com.za869765.imagine.ui.component.ImagineCard(pad = 12, onClick = onToggle) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                Text(summary, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (expanded) "收起 ▴" else "展開 ▾", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

/** v2.1.9 最近任務清單:沒做完的每 30 秒查一次狀態與預計完成,寫回 H3Jobs 後 onChanged 重讀清單。 */
@Composable
fun RecentJobsPoller(prefs: com.za869765.imagine.data.prefs.SecurePrefs, jobs: List<com.za869765.imagine.data.h3.H3Jobs.Job>, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val pending = jobs.filter { it.status !in listOf("done", "failed", "cancelled") }.map { it.runId to it.kind }
    androidx.compose.runtime.LaunchedEffect(owner, pending) {
        if (pending.isEmpty() || !prefs.isH3TokenSet) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                for ((id, kind) in pending) {
                    val (st, eta) = if (kind == "image") {
                        (com.za869765.imagine.data.h3.H3Client.imageRun(prefs, id) as? com.za869765.imagine.data.h3.H3Client.Result.Ok)?.value?.let { it.status to it.eta } ?: (null to null)
                    } else {
                        (com.za869765.imagine.data.h3.H3Client.job(prefs, id) as? com.za869765.imagine.data.h3.H3Client.Result.Ok)?.value?.let { it.status to it.eta } ?: (null to null)
                    }
                    if (st != null) com.za869765.imagine.data.h3.H3Jobs.update(ctx, id) { it.copy(status = st, etaAt = eta?.etaAt ?: it.etaAt) }
                }
                onChanged()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }
}

/**
 * 播放 H3 成品:遠端串流需帶 Authorization(伺服器支援 HTTP Range);本機 file:// 直接播。
 */
@UnstableApi
@Composable
fun H3VideoPlayer(url: String, headers: Map<String, String>, userAgent: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(url) {
        val builder = ExoPlayer.Builder(ctx)
        if (url.startsWith("http")) {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(http))
        }
        builder.build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(url) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}
