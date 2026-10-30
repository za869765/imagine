package com.za869765.imagine.nav

object Routes {
    const val SPLASH = "splash"

    const val MATERIAL_HUB = "material_hub"
    const val MATERIAL_LIBRARY = "material_library"
    const val LONG_VIDEO = "long_video"
    const val TUTORIAL = "tutorial"
    const val H3_RESULTS = "h3_results"
    const val GROK_CHAT = "grok_chat"

    const val GENERATE_IMAGE = "generate_image"
    const val GENERATE_VIDEO = "generate_video"
    const val COMBINE_EXTEND = "combine_extend" // 組合延長:進階獨立頁(非素材生成 tab)

    const val EDIT = "edit"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history_detail"

    const val SETTINGS = "settings"
    const val API_KEY_EDIT = "api_key_edit"
    // v1.8.0
    const val CHAT = "chat"                       // API 對話(xAI / OpenRouter 皆可,非 WebView)
    const val MATERIAL_REVIEW = "material_review" // 素材總覽・去留審查
    // v2.0.0 H3 Studio 任務頁(帶 runId)
    const val H3_JOB = "h3_job/{runId}"
    fun h3Job(runId: String) = "h3_job/$runId"
    // v2.0.5 H3 Studio 網頁分頁(主導航)
    const val H3_STUDIO = "h3_studio"
    // v2.0.7 H3 生圖任務頁(帶 runId)
    const val H3_IMAGE_JOB = "h3_image_job/{runId}"
    // v2.1.6 姿勢參考庫
    const val POSE_LIBRARY = "pose_library"
    fun h3ImageJob(runId: String) = "h3_image_job/$runId"
}
