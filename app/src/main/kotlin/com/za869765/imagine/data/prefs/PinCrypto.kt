package com.za869765.imagine.data.prefs

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// PIN never stored in clear; PBKDF2-HMAC-SHA256 with 100k iterations.
object PinCrypto {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGO = "PBKDF2WithHmacSHA256"

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, saltB64: String): String {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGO)
        val out = factory.generateSecret(spec).encoded
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun verify(pin: String, saltB64: String, expectedHash: String): Boolean {
        return constantTimeEquals(hash(pin, saltB64), expectedHash)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
