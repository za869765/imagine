package com.za869765.imagine.ui.generate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.h3.H3Jobs
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.TextActionButton

/** v2.5.9 種子：隨機／固定。選擇和數字都會記住；v2.7.0 固定時從「種子庫」看預覽圖挑選，也可直接輸入數字。 */
@Composable
fun H3SeedPicker(prefs: SecurePrefs) {
    val ctx = LocalContext.current
    var fixed by remember { mutableStateOf(prefs.h3SeedFixed) }
    var value by remember { mutableStateOf(prefs.h3SeedValue) }
    fun pickSeed(v: String) { value = v; prefs.h3SeedValue = v }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("種子", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedTab(
            options = listOf(SegmentedOption("random", "隨機"), SegmentedOption("fixed", "固定")),
            activeId = if (fixed) "fixed" else "random",
            onSelected = { fixed = it == "fixed"; prefs.h3SeedFixed = fixed },
        )
        if (fixed) {
            OutlinedTextField(
                value = value,
                onValueChange = { v -> pickSeed(v.filter { it.isDigit() }.take(14)) },
                label = { Text("種子數字") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(if (value.isBlank()) "沒填數字會用隨機種子" else "同提示詞、同條件、同機器才會重現相同結果")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // v2.7.0 不再列數字：開種子庫看成品預覽再選
            var libraryOpen by remember { mutableStateOf(false) }
            OutlinedActionButton(label = "選取種子庫", icon = "photo_library", onClick = { libraryOpen = true }, modifier = Modifier.fillMaxWidth())
            if (libraryOpen) {
                val choices = remember { H3Jobs.seedChoices(ctx) }
                AlertDialog(
                    onDismissRequest = { libraryOpen = false },
                    title = { Text("種子庫") },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (choices.isEmpty()) {
                                item { Text("還沒有記憶種子，生成完成後會自動存進來", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            items(choices) { (seed, uri) ->
                                SeedRow(seed, uri) { pickSeed(seed.toString()); libraryOpen = false }
                            }
                        }
                    },
                    confirmButton = { TextActionButton(label = "關閉", onClick = { libraryOpen = false }) },
                )
            }
        }
    }
}

@Composable
private fun SeedRow(seed: Long, uri: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Text(seed.toString(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** v2.6.1「再生成一次」：選隨機或記憶的種子，旁邊附該種子的成品預覽，選了就直接送出。 */
@Composable
fun H3RegenerateSeedDialog(prompt: String, onRandom: () -> Unit, onSeed: (Long) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val choices = remember(prompt) { H3Jobs.seedChoices(ctx, prompt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("再生成一次") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text(
                        "隨機種子",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onRandom)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                if (choices.isEmpty()) {
                    item { Text("這個提示詞還沒有記憶種子", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp)) }
                }
                items(choices) { (seed, uri) -> SeedRow(seed, uri) { onSeed(seed) } }
            }
        },
        confirmButton = { TextActionButton(label = "關閉", onClick = onDismiss) },
    )
}

/** 成品頁的種子列：只顯示實際種子；v2.6.9 種子已自動記憶，不必手動按，也不會改動下次的隨機／固定設定。 */
@Composable
fun H3SeedRow(seed: Long?) {
    if (seed == null) return
    Text("種子 $seed（已自動記憶）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
}
