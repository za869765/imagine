package com.za869765.imagine.ui.component

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Prompt",
    placeholder: String = "描述你想生成的內容...",
    maxChars: Int = 1000,
    modifier: Modifier = Modifier,
    // S22U (6.8") 觸控區建議 ≥48dp，default 改 156dp 給三行可見高度 + 足夠拇指區
    minHeight: Int = 156,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline
    val borderWidth = if (focused) 2.dp else 1.dp
    val labelColor = if (focused) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    // 焦點時用 BringIntoViewRequester 推 input 到 IME 上方避免被擋
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    fun doPaste() {
        val pasted = Clipboard.paste(ctx)
        if (pasted.isNullOrBlank()) {
            Toast.makeText(ctx, "剪貼簿沒有文字", Toast.LENGTH_SHORT).show()
            return
        }
        // 貼上覆蓋既有內容 — 對「重做生成」場景最常用,要附加文字可手動 select+貼
        onValueChange(if (pasted.length > maxChars) pasted.take(maxChars) else pasted)
    }

    Box(modifier = modifier) {
        // Floating label
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.W500,
            color = labelColor,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp)
                .align(Alignment.TopStart),
        )

        // 貼上按鈕(TopEnd,跟 floating label 對稱)— 蓋住 border line,視覺浮起
        Row(
            modifier = Modifier
                .padding(end = 12.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { doPaste() }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ImagineIcon(
                name = "content_paste",
                size = 14.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "貼上",
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(min = minHeight.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .bringIntoViewRequester(bringIntoView)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxChars) onValueChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) scope.launch { bringIntoView.bringIntoView() }
                    },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,    // 從 15→16 對 S22U 高密度螢幕視覺更舒服
                    lineHeight = 24.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    // prompt 允許多行；不要強制 Done / 不要 auto capitalize
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    imeAction = ImeAction.Default,
                ),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                        )
                    }
                    inner()
                },
            )

            // Character counter
            Text(
                text = "${value.length} / $maxChars",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(top = 4.dp),
            )
        }
    }
}
