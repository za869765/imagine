package com.za869765.imagine

/** 散落各處的固定值集中(UI_REDESIGN_PLAN 3.5)。純自用 App:xAI team id 寫死。 */
object Constants {
    const val XAI_TEAM_ID = "02192454-54ee-4835-9680-212eda8ba708"
    const val XAI_USAGE_URL = "https://console.x.ai/team/$XAI_TEAM_ID/usage?category=image"
    const val XAI_KEYS_URL = "https://console.x.ai/team/default/api-keys"

    // 假裝成真實 Chrome 的 UA — 降低部分 OAuth(Google/X)把內嵌 WebView 擋成「不安全瀏覽器」的機率。
    const val GROK_WEBVIEW_UA =
        "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    // 下載影片前要求的最少剩餘空間(UI_REDESIGN_PLAN 6.3)
    const val MIN_FREE_BYTES_FOR_DOWNLOAD = 200L * 1024 * 1024
}
