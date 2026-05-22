package com.za869765.imagine.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.za869765.imagine.BuildConfig
import com.za869765.imagine.data.prefs.PinCrypto
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.TextActionButton
import kotlinx.coroutines.delay

// 忘記 PIN 重設碼 — 寫死(repo 已 public 沒保密效果，純擋手滑)。
// APP 唯一持有人 za869765 自己一定記得，不必另開 onboarding 教學。
private const val RESET_SECRET = "za869765"

@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    onForgotPin: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }

    fun tryAuth() {
        val salt = prefs.pinSalt ?: return
        val hash = prefs.pinHash ?: return
        if (PinCrypto.verify(pin, salt, hash)) {
            onUnlock()
        } else {
            error = true
            pin = ""
        }
    }

    // Auto-verify when PIN reaches stored length
    LaunchedEffect(pin) {
        val storedLen = prefs.pinLength
        if (storedLen > 0 && pin.length == storedLen) {
            // Tiny delay so the dot animation completes before the screen swaps.
            delay(100)
            tryAuth()
        }
    }

    if (error) {
        LaunchedEffect(Unit) {
            delay(500)
            error = false
        }
    }

    if (showForgotDialog) {
        ForgotPinDialog(
            onDismiss = { showForgotDialog = false },
            onResetConfirmed = {
                showForgotDialog = false
                onForgotPin()
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            ImagineIcon(
                name = "lock",
                size = 28.dp,
                fill = 1,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Imagine",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.W700,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (error) "PIN 錯誤，請再試一次" else "請輸入 PIN 解鎖",
            color = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        PinDots(filled = pin.length, error = error)
        Spacer(modifier = Modifier.height(32.dp))
        PinPad(
            onDigit = { d ->
                if (pin.length < 12) pin = pin + d.toString()
            },
            onBackspace = {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
            },
            leadingKey = PinAuxKey.None,
            trailingKey = PinAuxKey.Backspace,
            filled = pin.length,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(28.dp))
        TextActionButton(label = "忘記 PIN？", onClick = { showForgotDialog = true })
    }
}

@Composable
private fun ForgotPinDialog(
    onDismiss: () -> Unit,
    onResetConfirmed: () -> Unit,
) {
    val ctx = LocalContext.current
    var resetCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

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
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "忘記 PIN — 重設流程",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "請寄信到 za869765@gmail.com 申請重設碼。\n" +
                        "⚠️ 重設 PIN 會清空 APP 內所有資料(API Key、生成歷史)，請先確認。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                OutlinedActionButton(
                    label = "📧 開啟郵件 app 寄出申請",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:za869765@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Imagine APP 重設碼申請")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "請申請重設碼以解鎖 Imagine APP。\n\n" +
                                    "Imagine 版本: ${BuildConfig.VERSION_NAME}\n" +
                                    "裝置: ${Build.MODEL}\n" +
                                    "Android: ${Build.VERSION.RELEASE}\n",
                            )
                        }
                        runCatching {
                            ctx.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(ctx, "找不到郵件 app", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = resetCode,
                    onValueChange = {
                        resetCode = it
                        if (codeError) codeError = false
                    },
                    label = { Text("重設碼") },
                    placeholder = { Text("收信後輸入此處") },
                    isError = codeError,
                    supportingText = if (codeError) {
                        { Text("重設碼不對", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextActionButton(label = "取消", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedActionButton(
                        label = "確認重設",
                        onClick = {
                            if (resetCode.trim() == RESET_SECRET) {
                                onResetConfirmed()
                            } else {
                                codeError = true
                            }
                        },
                    )
                }
            }
        }
    }
}
