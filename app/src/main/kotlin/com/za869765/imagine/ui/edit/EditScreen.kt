package com.za869765.imagine.ui.edit

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab

enum class EditMode { ImageEdit, VideoEdit, VideoExtend }

@Composable
fun EditScreen(
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onExecute: (mode: EditMode, prompt: String) -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }

    var mode by remember { mutableStateOf(EditMode.ImageEdit) }
    var prompt by remember { mutableStateOf("") }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.EDIT, onTabSelected = onNavSelected) },
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
                    SegmentedOption("img", "圖片編輯"),
                    SegmentedOption("vid", "影片編輯"),
                    SegmentedOption("ext", "影片延長"),
                ),
                activeId = when (mode) {
                    EditMode.ImageEdit -> "img"
                    EditMode.VideoEdit -> "vid"
                    EditMode.VideoExtend -> "ext"
                },
                onSelected = {
                    mode = when (it) {
                        "img" -> EditMode.ImageEdit
                        "vid" -> EditMode.VideoEdit
                        else -> EditMode.VideoExtend
                    }
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("來源")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceDim)
                        .clickable { /* TODO: pick source */ },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            ImagineIcon(
                                name = "auto_awesome",
                                size = 24.dp,
                                fill = 1,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            text = when (mode) {
                                EditMode.ImageEdit -> "選擇圖片"
                                else -> "選擇影片"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "或從歷史挑選",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("編輯說明")
                PromptInput(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = "",
                    placeholder = when (mode) {
                        EditMode.ImageEdit -> "把背景換成夕陽，加上暖色調濾鏡..."
                        EditMode.VideoEdit -> "把背景音樂換成爵士樂，畫面更柔和..."
                        EditMode.VideoExtend -> "讓主角繼續往街道走..."
                    },
                    minHeight = 104,
                )
            }

            ImagineCard(pad = 14) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "預估費用",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W500,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "$0.05",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            PrimaryButton(
                label = "執 行",
                icon = "edit",
                enabled = prompt.isNotBlank(),
                onClick = { onExecute(mode, prompt) },
            )
        }
    }
}
