package com.za869765.imagine.ui.component

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// 「自己組」的 JSON 輸出/匯入 — key=BUILDER_FIELDS 欄位名（中文）、值=選項字串。
// 匯出的 JSON 本身就是匯入格式（round-trip），也可以直接拿去當 LLM 填表 schema：
// 「照這個 JSON 的欄位，根據我的點子填一份」→ 複製回來一鍵貼上。

fun assembleBuilderJson(sel: Map<String, String>): String {
    val obj = buildJsonObject {
        BUILDER_FIELDS.forEach { f ->
            val v = sel[f.label].orEmpty()
            if (v.isNotEmpty() && v != "(不指定)") put(f.label, v)
        }
    }
    return PRETTY_JSON.encodeToString(JsonObject.serializer(), obj)
}

// 「AI 填表」system prompt — 欄位＋候選值清單餵給模型，一句話點子 → 單一 JSON。
// 候選清單是關鍵：填出來的值貼著 app 詞庫走，比放任自由發揮穩。
fun buildFillFormSystemPrompt(forVideo: Boolean): String {
    val fields =
        if (forVideo) BUILDER_FIELDS else BUILDER_FIELDS.filter { it.label !in VIDEO_ONLY_FIELDS }
    return buildString {
        append("你是 AI 生圖/生影片提示詞的填表員。根據使用者的一句話點子，")
        append("輸出一個 JSON 物件（只輸出 JSON，不要任何其他文字或說明），")
        append("key 必須是下列欄位名，value 是欄位值。規則：")
        append("1) 值優先從該欄位的候選清單中挑選；候選都不合適才自由發揮（簡短、同風格）。")
        append("2) 與點子無關的欄位直接省略，不要硬填。")
        append("3) 「主體對象」「場景地點」「光線時辰」「構圖鏡頭」「風格類型」「氣質類型」盡量都給。")
        append("4) 全部使用繁體中文。\n\n欄位與候選值：\n")
        fields.forEach { f ->
            append("- ").append(f.label).append("：")
            append(f.options.filter { it != "(不指定)" }.joinToString("、"))
            append("\n")
        }
    }
}

// 從模型回文抽出第一個 {...} 區塊（json_object 模式失效或模型加了 ``` 圍欄時的 fallback）
fun extractJsonObject(text: String): String? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    return if (start in 0 until end) text.substring(start, end + 1) else null
}

// 解析 {欄位名: 值} 的平面 JSON，只收認得的欄位；值不限既有選項（LLM 自由發揮也收，
// 組裝端 assembleBuilderPrompt 本來就吃任意字串）。解析失敗或一欄都對不上 → null。
fun parseBuilderJson(text: String): Map<String, String>? {
    val obj = runCatching { Json.parseToJsonElement(text.trim()) as? JsonObject }
        .getOrNull() ?: return null
    val knownLabels = BUILDER_FIELDS.map { it.label }.toSet()
    val out = mutableMapOf<String, String>()
    obj.forEach { (k, v) ->
        if (k in knownLabels) {
            // contentOrNull: JsonNull 回 null 而非字面 "null"
            val s = (v as? JsonPrimitive)?.contentOrNull
            if (!s.isNullOrBlank()) out[k] = s
        }
    }
    return out.ifEmpty { null }
}
