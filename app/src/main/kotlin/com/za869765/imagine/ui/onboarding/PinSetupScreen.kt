package com.za869765.imagine.ui.onboarding

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.prefs.PinCrypto
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineIcon
import kotlinx.coroutines.delay

private enum class PinSetupStep { Entering, Confirming }

@Composable
fun PinSetupScreen(onComplete: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }

    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(PinSetupStep.Entering) }
    var error by remember { mutableStateOf(false) }

    if (error) {
        LaunchedEffect(Unit) {
            delay(500)
            error = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
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
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (step == PinSetupStep.Entering) "設定解鎖密碼" else "再次輸入確認",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 26.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = (-0.01).sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (step == PinSetupStep.Entering) "輸入數字 PIN，按 ✓ 完成\n建議至少 4 位以上"
            else "請再輸入一次相同的 PIN",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(36.dp))
        PinDots(filled = pin.length, error = error)
        Spacer(modifier = Modifier.height(36.dp))
        PinPad(
            onDigit = { d ->
                if (pin.length < 12) pin = pin + d.toString()
            },
            onBackspace = {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
            },
            onConfirm = {
                if (pin.length < 3) {
                    error = true
                    return@PinPad
                }
                if (step == PinSetupStep.Entering) {
                    firstPin = pin
                    pin = ""
                    step = PinSetupStep.Confirming
                } else {
                    if (pin == firstPin) {
                        val salt = PinCrypto.newSalt()
                        prefs.pinSalt = salt
                        prefs.pinHash = PinCrypto.hash(pin, salt)
                        prefs.pinLength = pin.length
                        onComplete()
                    } else {
                        error = true
                        pin = ""
                    }
                }
            },
            leadingKey = PinAuxKey.Backspace,
            trailingKey = PinAuxKey.Confirm,
            filled = pin.length,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
