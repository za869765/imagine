package com.za869765.imagine.ui.component

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.storage.CharacterAssets

/**
 * 角色資產選擇 sheet — 列出所有角色(名字+定妝圖縮圖列),點一個角色把整組定妝圖
 * 交給 onPick(呼叫端拿去當參考圖)。長按角色可刪除(不刪圖檔)。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CharacterPickerSheet(
    onDismiss: () -> Unit,
    onPick: (name: String, uris: List<Uri>) -> Unit,
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // reloadKey:刪角色後重讀清單
    var reloadKey by remember { mutableStateOf(0) }
    val names = remember(reloadKey) { CharacterAssets.names(ctx) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader("選擇角色（帶入整組定妝圖）")
            if (names.isEmpty()) {
                Text(
                    text = "還沒有角色資產 — 生圖成功後在結果卡點「🎭 存成角色資產」,幫這組定妝圖取個名字,之後就能在這裡一鍵帶入。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "點角色帶入定妝圖當參考;長按可刪除角色(圖檔不會刪)。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                names.forEach { name ->
                    val images = remember(name, reloadKey) { CharacterAssets.imagesOf(ctx, name) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .combinedClickable(
                                onClick = {
                                    onPick(name, images.map { CharacterAssets.uriOf(ctx, it) })
                                },
                                onLongClick = { deleteTarget = name },
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "🎭 $name",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.W700,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${images.size} 張",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                images.take(4).forEach { n ->
                                    AsyncImage(
                                        model = CharacterAssets.uriOf(ctx, n),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    )
                                }
                                if (images.size > 4) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "+${images.size - 4}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.W600,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        ImagineIcon(
                            name = "chevron_right", size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("刪除角色「$name」？") },
            text = { Text("只會刪掉這個角色的名字與圖片組合,定妝圖本身仍在歷史/素材庫。") },
            confirmButton = {
                TextButton(onClick = {
                    CharacterAssets.delete(ctx, name)
                    deleteTarget = null
                    reloadKey++
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

/**
 * 「存成角色資產」命名對話框 — 輸入新名字或點既有角色名(加進該角色)。
 * onConfirm 拿到名字;圖片由呼叫端自己 addImages。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveToCharacterDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
) {
    val ctx = LocalContext.current
    val existing = remember { CharacterAssets.names(ctx) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("存成角色資產") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "幫這組定妝圖取個角色名,之後生成時可一鍵帶入整組當參考圖(鎖臉/鎖造型)。" +
                        "同一角色要分多套定妝(居家/外出/正式),用「名字·定妝」分開存,例:小美·外出。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("角色名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing.isNotEmpty()) {
                    Text(
                        "或加進既有角色:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        existing.forEach { n ->
                            ImagineChip(
                                label = n,
                                variant = if (name == n) ChipVariant.Tonal else ChipVariant.Outlined,
                                onClick = { name = n },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
