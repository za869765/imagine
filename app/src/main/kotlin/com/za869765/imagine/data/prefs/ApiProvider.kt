package com.za869765.imagine.data.prefs

// v1.8.0: 第二個 API 供應商(OpenRouter,OpenAI 相容)。xAI 仍是預設;使用者在設定頁切換並各自存 key。
enum class ApiProvider(
    val id: String,
    val label: String,
    val keyPrefix: String,
    val keyHint: String,
    // 「查詢帳單」快捷鈕開的網址(跟既有 Grok 那顆同一套做法:系統瀏覽器開啟)
    val billingUrl: String,
    val keysUrl: String,
) {
    XAI(
        id = "xai",
        label = "xAI (Grok)",
        keyPrefix = "xai-",
        keyHint = "xai-...",
        billingUrl = "https://console.x.ai/team/02192454-54ee-4835-9680-212eda8ba708/usage?category=image",
        keysUrl = "https://console.x.ai/team/default/api-keys",
    ),
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter",
        keyPrefix = "sk-or-",
        keyHint = "sk-or-v1-...",
        billingUrl = "https://openrouter.ai/settings/credits",
        keysUrl = "https://openrouter.ai/settings/keys",
    );

    companion object {
        fun fromId(id: String?): ApiProvider = entries.firstOrNull { it.id == id } ?: XAI
    }
}
