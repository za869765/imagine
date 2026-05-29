package com.za869765.imagine.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    // v1.0.29 砍 GitHub PAT — repo 改 public 後 in-app updater 匿名 access 即可
    // 舊資料 (K_GITHUB_PAT) 留在 prefs 不主動清，後續升級若 K_* 重用同名 key 才會覆蓋

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

    // ── Theme + locale ──────────────────────────────────────────
    var themeMode: String
        get() = prefs.getString(K_THEME, "system")!!
        set(v) = prefs.edit().putString(K_THEME, v).apply()

    // ── Prompt 片語「最近用過」(B3) ───────────────────────────────
    // 以 \n 串接存一條字串;讀回時濾掉空行。上限由寫入端控制(取前 6)。
    var recentSnippets: List<String>
        get() = prefs.getString(K_RECENT_SNIPPETS, null)
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = prefs.edit().putString(K_RECENT_SNIPPETS, v.joinToString("\n")).apply()

    // ── Bulk reset (clears everything) ──────────────────────────
    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val K_API_KEY = "api_key"
        private const val K_API_KEY_VERIFIED_AT = "api_key_verified_at"
        private const val K_PIN_HASH = "pin_hash"
        private const val K_PIN_SALT = "pin_salt"
        private const val K_PIN_LENGTH = "pin_length"
        private const val K_BIOMETRIC = "biometric_enabled"
        private const val K_LOCK_BG = "lock_on_bg"
        private const val K_FLAG_SECURE = "prevent_screenshots"
        private const val K_THEME = "theme_mode"
        private const val K_RECENT_SNIPPETS = "recent_snippets"

        @Volatile private var instance: SecurePrefs? = null
        fun get(ctx: Context): SecurePrefs = instance ?: synchronized(this) {
            instance ?: SecurePrefs(ctx.applicationContext).also { instance = it }
        }
    }
}
