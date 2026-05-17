package com.za869765.imagine.ui.generate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.OutlinedActionButton
import kotlinx.coroutines.delay

@Composable
fun VideoGeneratingScreen(
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    var elapsed by remember { mutableStateOf(0) }
    val mins = elapsed / 60
    val secs = elapsed % 60
    val timeText = "%d:%02d".format(mins, secs)

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed++
            if (elapsed > 90) {
                onDone()
                break
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Spinning indeterminate ring around the icon
            CircularProgressIndicator(
                modifier = Modifier.size(120.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            )
            // Static icon center
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                ImagineIcon(
                    name = "movie",
                    size = 48.dp,
                    fill = 1,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "影片生成中...",
            fontSize = 22.sp,
            fontWeight = FontWeight.W700,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.01).sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "預計需要 30–90 秒",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            timeText,
            fontSize = 36.sp,
            fontWeight = FontWeight.W600,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.8.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "已等待",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(56.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "可以切到其他畫面，\n完成會通知你",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedActionButton(
            label = "取消生成",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
