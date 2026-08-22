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

    // ── v1.8.0 第二供應商 OpenRouter + 目前使用哪家(記住選擇)───────────
    // apiKey 仍是 xAI key(名稱不動,避免動到 KeyBackup / 既有畫面);OpenRouter 另存一把。
    var openRouterKey: String?
        get() = prefs.getString(K_OR_KEY, null)
        set(v) = prefs.edit().putString(K_OR_KEY, v).apply()

    var openRouterKeyVerifiedAt: String?
        get() = prefs.getString(K_OR_KEY_VERIFIED_AT, null)
        set(v) = prefs.edit().putString(K_OR_KEY_VERIFIED_AT, v).apply()

    val isOpenRouterKeySet: Boolean get() = !openRouterKey.isNullOrBlank()

    fun keyFor(p: ApiProvider): String? = when (p) {
        ApiProvider.XAI -> apiKey
        ApiProvider.OPENROUTER -> openRouterKey
    }

    fun hasKeyFor(p: ApiProvider): Boolean = !keyFor(p).isNullOrBlank()

    // v1.8.3 沒有「目前使用哪家」的切換:各功能只記「上次選的模型 id」,有哪家 key 就列哪家的模型,
    // 供應商由模型 id 判斷(ApiProvider.ofModel)。null = 還沒選過,畫面依有無 key 給預設。
    var chatModel: String?
        get() = prefs.getString(K_CHAT_MODEL, null)
        set(v) = prefs.edit().putString(K_CHAT_MODEL, v).apply()
    var imageModel: String?
        get() = prefs.getString(K_IMAGE_MODEL, null)
        set(v) = prefs.edit().putString(K_IMAGE_MODEL, v).apply()
    var videoModel: String?
        get() = prefs.getString(K_VIDEO_MODEL, null)
        set(v) = prefs.edit().putString(K_VIDEO_MODEL, v).apply()

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

    // ── 教學範本「收藏」(以 PromptExample.tag 標識,\n 串接,同 recentSnippets) ──
    var favoriteTemplates: List<String>
        get() = prefs.getString(K_FAVORITE_TEMPLATES, null)
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = prefs.edit().putString(K_FAVORITE_TEMPLATES, v.joinToString("\n")).apply()

    // ── 生成預設參數 (C1) ──────────────────────────────────────
    var defImageResolution: String
        get() = prefs.getString(K_DEF_IMG_RES, "1k")!!
        set(v) = prefs.edit().putString(K_DEF_IMG_RES, v).apply()
    var defImageAspect: String
        get() = prefs.getString(K_DEF_IMG_ASPECT, "1:1")!!
        set(v) = prefs.edit().putString(K_DEF_IMG_ASPECT, v).apply()
    var defImageCount: Int
        get() = prefs.getInt(K_DEF_IMG_COUNT, 1)
        set(v) = prefs.edit().putInt(K_DEF_IMG_COUNT, v).apply()
    var defImageQuality: String
        get() = prefs.getString(K_DEF_IMG_QUALITY, "rapid")!!
        set(v) = prefs.edit().putString(K_DEF_IMG_QUALITY, v).apply()
    var defVideoDuration: Int
        get() = prefs.getInt(K_DEF_VID_DUR, 5)
        set(v) = prefs.edit().putInt(K_DEF_VID_DUR, v).apply()
    var defVideoAspect: String
        get() = prefs.getString(K_DEF_VID_ASPECT, "1:1")!!
        set(v) = prefs.edit().putString(K_DEF_VID_ASPECT, v).apply()
    var defVideoResolution: String
        get() = prefs.getString(K_DEF_VID_RES, "480p")!!
        set(v) = prefs.edit().putString(K_DEF_VID_RES, v).apply()

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
        private const val K_FAVORITE_TEMPLATES = "favorite_templates"
        private const val K_DEF_IMG_RES = "def_img_res"
        private const val K_DEF_IMG_ASPECT = "def_img_aspect"
        private const val K_DEF_IMG_COUNT = "def_img_count"
        private const val K_DEF_IMG_QUALITY = "def_img_quality"
        private const val K_DEF_VID_DUR = "def_vid_dur"
        private const val K_DEF_VID_ASPECT = "def_vid_aspect"
        private const val K_DEF_VID_RES = "def_vid_res"
        // v1.8.0
        private const val K_OR_KEY = "openrouter_key"
        private const val K_OR_KEY_VERIFIED_AT = "openrouter_key_verified_at"
        private const val K_CHAT_MODEL = "sel_chat_model"
        private const val K_IMAGE_MODEL = "sel_image_model"
        private const val K_VIDEO_MODEL = "sel_video_model"

        @Volatile private var instance: SecurePrefs? = null
        fun get(ctx: Context): SecurePrefs = instance ?: synchronized(this) {
            instance ?: SecurePrefs(ctx.applicationContext).also { instance = it }
        }
    }
}
