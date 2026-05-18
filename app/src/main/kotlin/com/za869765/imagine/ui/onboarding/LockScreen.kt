package com.za869765.imagine.ui.onboarding

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.za869765.imagine.data.prefs.PinCrypto
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.TextActionButton
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    onForgotPin: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val biometricEnabled = prefs.biometricEnabled && canAuthenticateWithBiometric(ctx)

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

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
            onBiometric = {
                runBiometric(ctx, prefs, onUnlock)
            },
            leadingKey = if (biometricEnabled) PinAuxKey.Biometric else PinAuxKey.None,
            trailingKey = PinAuxKey.Backspace,
            filled = pin.length,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(28.dp))
        TextActionButton(label = "忘記 PIN？", onClick = onForgotPin)
    }
}

// S22 Ultra 的指紋是 BIOMETRIC_STRONG，Android 13+ 對 WEAK-only 的 BiometricPrompt 有限制，
// 改用 STRONG or WEAK，三星指紋/臉部/低階感應器都吃得到。
private const val BIOMETRIC_LEVELS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK

private fun canAuthenticateWithBiometric(ctx: android.content.Context): Boolean {
    val bm = BiometricManager.from(ctx)
    return bm.canAuthenticate(BIOMETRIC_LEVELS) == BiometricManager.BIOMETRIC_SUCCESS
}

private fun runBiometric(
    ctx: android.content.Context,
    prefs: SecurePrefs,
    onUnlock: () -> Unit,
) {
    val activity = (ctx as? FragmentActivity) ?: return
    if (!prefs.biometricEnabled) return

    val executor = ContextCompat.getMainExecutor(ctx)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onUnlock()
        }
    }
    val prompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("解鎖 Imagine")
        .setSubtitle("使用生物辨識解鎖")
        .setNegativeButtonText("使用 PIN")
        .setAllowedAuthenticators(BIOMETRIC_LEVELS)
        .build()
    prompt.authenticate(info)
}
