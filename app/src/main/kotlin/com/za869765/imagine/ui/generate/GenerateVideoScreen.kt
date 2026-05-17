package com.za869765.imagine.ui.generate

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagePlaceholder
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.theme.LocalBudgetColors

enum class VideoMode { T2V, Img2Vid, Ref }

@Composable
fun GenerateVideoScreen(
    onSwitchToImage: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onGenerate: (prompt: String, mode: VideoMode, duration: Int, aspect: String, resolution: String) -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val budgetColors = LocalBudgetColors.current

    var prompt by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(VideoMode.Img2Vid) }
    var duration by remember { mutableStateOf(8) }
    var aspect by remember { mutableStateOf("16:9") }
    var resolution by remember { mutableStateOf("480p") }
    var sourceImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val maxImages = if (mode == VideoMode.Img2Vid) 1 else 3
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null && sourceImages.size < maxImages) {
            sourceImages = sourceImages + uri
        }
    }
    val launchPick: () -> Unit = {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val estimated = duration * 0.05
    val remaining = (prefs.budgetCap - prefs.spent).coerceAtLeast(0.0)
    val affordable = remaining >= estimated

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.GENERATE, onTabSelected = onNavSelected) },
        spent = prefs.spent,
        budgetCap = prefs.budgetCap,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedTab(
                options = listOf(
                    SegmentedOption("image", "圖片"),
                    SegmentedOption("video", "影片"),
                ),
                activeId = "video",
                onSelected = { if (it == "image") onSwitchToImage() },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("模式")
                SegmentedTab(
                    options = listOf(
                        SegmentedOption("t2v", "文生影"),
                        SegmentedOption("img2vid", "圖生影"),
                        SegmentedOption("ref", "參考圖"),
                    ),
                    activeId = when (mode) {
                        VideoMode.T2V -> "t2v"
                        VideoMode.Img2Vid -> "img2vid"
                        VideoMode.Ref -> "ref"
                    },
                    onSelected = {
                        mode = when (it) {
                            "t2v" -> VideoMode.T2V
                            "img2vid" -> VideoMode.Img2Vid
                            else -> VideoMode.Ref
                        }
                    },
                )
            }

            if (mode != VideoMode.T2V) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        if (mode == VideoMode.Img2Vid) "起始圖" else "參考圖（最多 3 張）",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        sourceImages.forEachIndexed { index, uri ->
                            SelectedImageSlot(
                                uri = uri,
                                onRemove = {
                                    sourceImages = sourceImages
                                        .toMutableList()
                                        .also { it.removeAt(index) }
                                },
                            )
                        }
                        if (sourceImages.size < maxImages) {
                            AddImageSlot(onClick = launchPick)
                        }
                    }
                }
            }

            PromptInput(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = "描述要怎麼動...",
                minHeight = 88,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamPicker(
                    label = "秒數",
                    value = duration.toString(),
                    options = (1..15).map { it.toString() },
                    onSelect = { duration = it.toIntOrNull() ?: 8 },
                    displayName = { "$it 秒" },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "長寬比",
                    value = aspect,
                    options = listOf("16:9", "1:1", "9:16", "4:3", "3:4", "3:2", "2:3"),
                    onSelect = { aspect = it },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "解析度",
                    value = resolution,
                    options = listOf("480p", "720p"),
                    onSelect = { resolution = it },
                    modifier = Modifier.weight(1f),
                )
            }

            ImagineCard(pad = 14) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            "預估費用",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W500,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$" + "%.2f".format(estimated),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.W600,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                " (${duration}s × \$0.05)",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "剩餘預算",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W500,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$" + "%.2f".format(remaining),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = FontFamily.Monospace,
                            color = if (affordable) budgetColors.ok else budgetColors.high,
                        )
                    }
                }
            }

            PrimaryButton(
                label = "生 成",
                icon = "movie",
                enabled = prompt.isNotBlank() && affordable,
                onClick = { onGenerate(prompt, mode, duration, aspect, resolution) },
            )
        }
    }
}

@Composable
private fun AddImageSlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ImagineIcon(
            name = "add_photo_alternate",
            size = 28.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectedImageSlot(uri: Uri, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(80.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            ImagineIcon(name = "close", size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
