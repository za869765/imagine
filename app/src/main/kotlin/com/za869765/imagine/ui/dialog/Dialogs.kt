package com.za869765.imagine.ui.dialog

import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.theme.LocalBudgetColors

// ─────────────────────────────────────────────────────────────
// Reusable dialog shell — matches imagine-screens.jsx Dialog
// ─────────────────────────────────────────────────────────────
@Composable
private fun ImagineDialogShell(
    icon: String,
    iconBg: Color,
    iconFg: Color,
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    ImagineIcon(name = icon, size = 26.dp, fill = 1, tint = iconFg)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = (-0.01).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
                body()
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun BudgetExceededDialog(
    spent: Double,
    cap: Double,
    estimated: Double,
    onCancel: () -> Unit,
    onGoToSettings: () -> Unit,
) {
    ImagineDialogShell(
        icon = "history",
        iconBg = Color(0xFFFFF3E0),
        iconFg = Color(0xFFE65100),
        title = "本期預算已達上限",
        onDismiss = onCancel,
        actions = {
            TextActionButton(label = "取消", onClick = onCancel)
            Spacer(modifier = Modifier.size(8.dp))
            PrimaryButton(
                label = "前往設定",
                onClick = onGoToSettings,
                modifier = Modifier.height(44.dp).fillMaxWidth(0f).padding(horizontal = 24.dp),
            )
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$" + "%.2f".format(spent) + " / $" + "%.2f".format(cap),
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "本次預估：\$" + "%.2f".format(estimated),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "無法繼續生成。\n請至設定頁提高上限，\n或關閉「達上限時鎖定」。",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ModerationFailedDialog(
    onConfirm: () -> Unit,
) {
    val budgetColors = LocalBudgetColors.current
    ImagineDialogShell(
        icon = "lock",
        iconBg = MaterialTheme.colorScheme.secondaryContainer,
        iconFg = MaterialTheme.colorScheme.onSecondaryContainer,
        title = "內容未通過審核",
        onDismiss = onConfirm,
        actions = {
            PrimaryButton(
                label = "確定",
                onClick = onConfirm,
                modifier = Modifier.height(44.dp).fillMaxWidth(0f).padding(horizontal = 32.dp),
            )
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "xAI 端內容政策擋下了這次生成請求。",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ImagineIcon(name = "check", size = 16.dp, fill = 1, tint = budgetColors.ok)
                    Text(
                        "本次未扣費",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                        color = budgetColors.ok,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "建議調整：",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "• 避免露骨、暴力內容",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
                Text(
                    "• 避免名人、品牌名稱",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
                Text(
                    "• 嘗試更中性的描述",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
fun ClearDataDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    ImagineIcon(
                        name = "history",
                        size = 26.dp,
                        fill = 1,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "清除所有資料？",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "以下資料將永久刪除：\n• API Key\n• PIN 密碼\n• 本期用量紀錄\n• 預算設定\n• 偏好設定\n\n已下載到相簿的圖/影不會被刪除。\n此操作無法復原。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextActionButton(label = "取消", onClick = onCancel)
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedActionButton(
                        label = "清除",
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}
