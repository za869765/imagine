package com.za869765.imagine.ui.component

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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

// 解析 {欄位名: 值} 的平面 JSON，只收認得的欄位；值不限既有選項（LLM 自由發揮也收，
// 組裝端 assembleBuilderPrompt 本來就吃任意字串）。解析失敗或一欄都對不上 → null。
fun parseBuilderJson(text: String): Map<String, String>? {
    val obj = runCatching { Json.parseToJsonElement(text.trim()) as? JsonObject }
        .getOrNull() ?: return null
    val knownLabels = BUILDER_FIELDS.map { it.label }.toSet()
    val out = mutableMapOf<String, String>()
    obj.forEach { (k, v) ->
        if (k in knownLabels) {
            val s = (v as? JsonPrimitive)?.content
            if (!s.isNullOrBlank()) out[k] = s
        }
    }
    return out.ifEmpty { null }
}
