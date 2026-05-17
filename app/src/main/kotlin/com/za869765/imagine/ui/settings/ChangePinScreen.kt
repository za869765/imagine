package com.za869765.imagine.ui.settings

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
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.onboarding.PinAuxKey
import com.za869765.imagine.ui.onboarding.PinDots
import com.za869765.imagine.ui.onboarding.PinPad
import kotlinx.coroutines.delay

private enum class ChangePinStep { Old, NewFirst, NewConfirm }

@Composable
fun ChangePinScreen(onBack: () -> Unit, onComplete: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }

    var step by remember { mutableStateOf(ChangePinStep.Old) }
    var pin by remember { mutableStateOf("") }
    var firstNew by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    if (error) {
        LaunchedEffect(Unit) {
            delay(500)
            error = false
        }
    }

    // Auto-verify old PIN when length matches stored
    LaunchedEffect(pin, step) {
        if (step != ChangePinStep.Old) return@LaunchedEffect
        val storedLen = prefs.pinLength
        if (storedLen > 0 && pin.length == storedLen) {
            delay(100)
            val ok = PinCrypto.verify(pin, prefs.pinSalt ?: "", prefs.pinHash ?: "")
            if (ok) {
                pin = ""
                step = ChangePinStep.NewFirst
            } else {
                error = true
                pin = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        ImagineIconButton(name = "arrow_back", onClick = onBack)

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            ImagineIcon(
                name = "lock", size = 28.dp, fill = 1,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (step) {
                ChangePinStep.Old -> "請輸入目前 PIN"
                ChangePinStep.NewFirst -> "請輸入新 PIN"
                ChangePinStep.NewConfirm -> "再次輸入新 PIN 確認"
            },
            color = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.W700,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error && step == ChangePinStep.Old) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "PIN 錯誤",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error && step == ChangePinStep.NewConfirm) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "兩次輸入不一致",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        PinDots(filled = pin.length, error = error)
        Spacer(modifier = Modifier.height(32.dp))

        PinPad(
            onDigit = { d -> if (pin.length < 12) pin += d.toString() },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            onConfirm = {
                if (pin.length < 3) {
                    error = true
                    return@PinPad
                }
                when (step) {
                    ChangePinStep.Old -> {
                        // confirm via PinCrypto
                        val ok = PinCrypto.verify(pin, prefs.pinSalt ?: "", prefs.pinHash ?: "")
                        if (ok) {
                            pin = ""
                            step = ChangePinStep.NewFirst
                        } else {
                            error = true
                            pin = ""
                        }
                    }
                    ChangePinStep.NewFirst -> {
                        firstNew = pin
                        pin = ""
                        step = ChangePinStep.NewConfirm
                    }
                    ChangePinStep.NewConfirm -> {
                        if (pin == firstNew) {
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
                }
            },
            leadingKey = PinAuxKey.Backspace,
            trailingKey = PinAuxKey.Confirm,
            filled = pin.length,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
