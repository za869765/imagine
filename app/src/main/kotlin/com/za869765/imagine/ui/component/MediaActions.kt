package com.za869765.imagine.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * MediaAction — 全 App 媒體動作文案與圖示的唯一來源(UI_REDESIGN_PLAN 2.2)。
 * 結果區、全螢幕看圖器、歷史詳情、素材庫、教學頁都從這裡取 label,
 * 廢除「生影 / 動起來 / 編輯這張 / 使用」等舊稱(導覽短標籤「對話｜生圖｜生影」不在此列)。
 */
enum class MediaAction(val label: String, val icon: String, val destructive: Boolean = false) {
    EDIT_IMAGE("修改圖片", "edit"),
    ANIMATE_IMAGE("圖片動起來", "movie"),
    EXTEND_VIDEO("延長影片", "movie"),
    EDIT_VIDEO("修改影片", "edit"),
    SAVE_TO_GALLERY("儲存到相簿", "download"),
    SHARE("分享", "share"),
    SHARE_WITH_PROMPT("分享（附提示詞）", "share"),
    COPY_PROMPT("複製提示詞", "content_copy"),
    USE_PROMPT("套用提示詞", "check"),
    ADD_TO_LIBRARY("加入素材庫", "star"),
    CHANGE_CATEGORY("改分類", "sell"),
    REMOVE_FROM_LIBRARY("移出素材庫", "visibility_off", destructive = true),
    SAVE_AS_CHARACTER("存為角色定妝圖", "star"),
}

data class MediaActionItem(val action: MediaAction, val onClick: () -> Unit)

// 看圖器動作:同一份 label/icon,onClick 帶目前顯示那張的 url
fun MediaAction.viewer(onClick: (String) -> Unit): ViewerAction =
    ViewerAction(icon = icon, label = label, destructive = destructive, onClick = onClick)

/**
 * MediaActionBar — 結果區動作列:前 primaryCount(預設 2)顆並排 OutlinedActionButton,
 * 其餘收進右側「更多」DropdownMenu(項目少且純文字,故不用 ModalBottomSheet)。
 */
@Composable
fun MediaActionBar(
    items: List<MediaActionItem>,
    modifier: Modifier = Modifier,
    primaryCount: Int = 2,
) {
    if (items.isEmpty()) return
    val primary = items.take(primaryCount)
    val more = items.drop(primaryCount)
    var showMore by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        primary.forEach { item ->
            OutlinedActionButton(
                label = item.action.label,
                icon = item.action.icon,
                onClick = item.onClick,
                modifier = Modifier.weight(1f),
            )
        }
        if (more.isNotEmpty()) {
            Box {
                OutlinedButton(
                    onClick = { showMore = true },
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    ImagineIcon(name = "more_horiz", size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    more.forEach { item ->
                        val fg = if (item.action.destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                        DropdownMenuItem(
                            text = { Text(item.action.label, color = fg) },
                            leadingIcon = { ImagineIcon(name = item.action.icon, size = 18.dp, tint = fg) },
                            onClick = { showMore = false; item.onClick() },
                        )
                    }
                }
            }
        }
    }
}
