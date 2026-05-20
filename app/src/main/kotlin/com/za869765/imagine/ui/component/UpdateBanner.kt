package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.update.Installer
import com.za869765.imagine.data.update.UpdateInfo

/**
 * 顯示在主畫面頂端的更新橫條。
 *
 * - state=Idle 且有 UpdateInfo → 顯示「v… 可用」+ 「立即更新」可點
 * - state=Downloading → 顯示「下載中 65%」進度文字 (簡單版，沒進度條)
 * - state=Verifying/Launching → 顯示「準備安裝…」
 * - state=Error → 顯示錯誤訊息 + 「重試」
 */
@Composable
fun UpdateBanner(
    info: UpdateInfo?,
    progress: Installer.Progress,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (info == null && progress.stage == Installer.Stage.Idle) return

    val bg = when (progress.stage) {
        Installer.Stage.Error -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val fg = when (progress.stage) {
        Installer.Stage.Error -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                when (progress.stage) {
                    Installer.Stage.Idle -> {
                        Text(
                            "有新版本：${info?.latestVersionName.orEmpty()}",
                            fontSize = 13.sp, fontWeight = FontWeight.W600, color = fg,
                        )
                        Text(
                            "v${info?.currentVersionName} → ${info?.latestVersionName}  ${formatSize(info?.apkSize ?: 0)}",
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = fg.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Installer.Stage.Downloading -> {
                        val pct = if (progress.total > 0)
                            (progress.downloaded * 100 / progress.total)
                        else 0
                        Text(
                            "下載中 $pct%",
                            fontSize = 13.sp, fontWeight = FontWeight.W600, color = fg,
                        )
                        Text(
                            "${formatSize(progress.downloaded)} / ${formatSize(progress.total)}",
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = fg.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Installer.Stage.Verifying, Installer.Stage.Launching -> {
                        Text(
                            if (progress.stage == Installer.Stage.Verifying) "驗證下載中…" else "請點「安裝」確認…",
                            fontSize = 13.sp, fontWeight = FontWeight.W600, color = fg,
                        )
                    }
                    Installer.Stage.Error -> {
                        Text("更新失敗", fontSize = 13.sp, fontWeight = FontWeight.W600, color = fg)
                        Text(
                            progress.message.orEmpty(),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = fg.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            // 右側按鈕
            when (progress.stage) {
                Installer.Stage.Idle -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "稍後",
                            fontSize = 12.sp,
                            color = fg.copy(alpha = 0.7f),
                            modifier = Modifier.clickable { onDismiss() }.padding(6.dp),
                        )
                        Text(
                            "立即更新",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W700,
                            color = fg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(fg.copy(alpha = 0.15f))
                                .clickable { onUpdateClick() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Installer.Stage.Error -> {
                    Text(
                        "重試",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W700,
                        color = fg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(fg.copy(alpha = 0.15f))
                            .clickable { onUpdateClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                else -> {}
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / 1024.0 / 1024.0
    return "%.1f MB".format(mb)
}
