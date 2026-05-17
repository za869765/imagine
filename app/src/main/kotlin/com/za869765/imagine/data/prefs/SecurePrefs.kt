package com.za869765.imagine.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// All persistent state, encrypted at rest via Android Keystore.
class SecurePrefs private constructor(ctx: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            "imagine_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ── API Key ─────────────────────────────────────────────────
    var apiKey: String?
        get() = prefs.getString(K_API_KEY, null)
        set(v) = prefs.edit().putString(K_API_KEY, v).apply()

    var apiKeyVerifiedAt: String?
        get() = prefs.getString(K_API_KEY_VERIFIED_AT, null)
        set(v) = prefs.edit().putString(K_API_KEY_VERIFIED_AT, v).apply()

    // ── PIN (hash + salt + length-hint) ──────────────────────────
    var pinHash: String?
        get() = prefs.getString(K_PIN_HASH, null)
        set(v) = prefs.edit().putString(K_PIN_HASH, v).apply()

    var pinSalt: String?
        get() = prefs.getString(K_PIN_SALT, null)
        set(v) = prefs.edit().putString(K_PIN_SALT, v).apply()

    // Length is not exposed in UI — only used internally to recognize "PIN complete".
    var pinLength: Int
        get() = prefs.getInt(K_PIN_LENGTH, 0)
        set(v) = prefs.edit().putInt(K_PIN_LENGTH, v).apply()

    val isPinSet: Boolean get() = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()
    val isApiKeySet: Boolean get() = !apiKey.isNullOrBlank()

    // ── Security toggles ────────────────────────────────────────
    var biometricEnabled: Boolean
        get() = prefs.getBoolean(K_BIOMETRIC, false)
        set(v) = prefs.edit().putBoolean(K_BIOMETRIC, v).apply()

    var lockOnBackground: Boolean
        get() = prefs.getBoolean(K_LOCK_BG, true)
        set(v) = prefs.edit().putBoolean(K_LOCK_BG, v).apply()

    var preventScreenshots: Boolean
        get() = prefs.getBoolean(K_FLAG_SECURE, true)
        set(v) = prefs.edit().putBoolean(K_FLAG_SECURE, v).apply()

    // ── Budget ──────────────────────────────────────────────────
    var budgetCap: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(K_BUDGET_CAP, java.lang.Double.doubleToRawLongBits(20.0)))
        set(v) = prefs.edit().putLong(K_BUDGET_CAP, java.lang.Double.doubleToRawLongBits(v)).apply()

    var lockOnLimit: Boolean
        get() = prefs.getBoolean(K_LOCK_ON_LIMIT, true)
        set(v) = prefs.edit().putBoolean(K_LOCK_ON_LIMIT, v).apply()

    var autoResetMonthly: Boolean
        get() = prefs.getBoolean(K_AUTO_RESET, true)
        set(v) = prefs.edit().putBoolean(K_AUTO_RESET, v).apply()

    // Period state
    var periodStart: String
        get() = prefs.getString(K_PERIOD_START, LocalDate.now().withDayOfMonth(1).format(DATE_FMT))!!
        set(v) = prefs.edit().putString(K_PERIOD_START, v).apply()

    var spent: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(K_SPENT, 0L))
        set(v) = prefs.edit().putLong(K_SPENT, java.lang.Double.doubleToRawLongBits(v)).apply()

    var imageCount: Int
        get() = prefs.getInt(K_IMG_COUNT, 0)
        set(v) = prefs.edit().putInt(K_IMG_COUNT, v).apply()

    var videoSeconds: Int
        get() = prefs.getInt(K_VID_SEC, 0)
        set(v) = prefs.edit().putInt(K_VID_SEC, v).apply()

    // ── Theme + locale (lightweight prefs that mustn't survive uninstall either) ──
    var themeMode: String
        get() = prefs.getString(K_THEME, "system")!!
        set(v) = prefs.edit().putString(K_THEME, v).apply()

    // ── Bulk reset (clears everything) ──────────────────────────
    fun clearAll() = prefs.edit().clear().apply()

    fun resetUsage() {
        spent = 0.0
        imageCount = 0
        videoSeconds = 0
        periodStart = LocalDate.now().withDayOfMonth(1).format(DATE_FMT)
    }

    fun maybeAutoResetForNewMonth() {
        if (!autoResetMonthly) return
        val now = YearMonth.now()
        val periodMonth = runCatching { YearMonth.from(LocalDate.parse(periodStart, DATE_FMT)) }
            .getOrNull() ?: return resetUsage()
        if (now != periodMonth) resetUsage()
    }

    companion object {
        private const val K_API_KEY = "api_key"
        private const val K_API_KEY_VERIFIED_AT = "api_key_verified_at"
        private const val K_PIN_HASH = "pin_hash"
        private const val K_PIN_SALT = "pin_salt"
        private const val K_PIN_LENGTH = "pin_length"
        private const val K_BIOMETRIC = "biometric_enabled"
        private const val K_LOCK_BG = "lock_on_bg"
        private const val K_FLAG_SECURE = "prevent_screenshots"
        private const val K_BUDGET_CAP = "budget_cap"
        private const val K_LOCK_ON_LIMIT = "lock_on_limit"
        private const val K_AUTO_RESET = "auto_reset_monthly"
        private const val K_PERIOD_START = "period_start"
        private const val K_SPENT = "spent"
        private const val K_IMG_COUNT = "image_count"
        private const val K_VID_SEC = "video_seconds"
        private const val K_THEME = "theme_mode"

        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        @Volatile private var instance: SecurePrefs? = null
        fun get(ctx: Context): SecurePrefs = instance ?: synchronized(this) {
            instance ?: SecurePrefs(ctx.applicationContext).also { instance = it }
        }
    }
}
