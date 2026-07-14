package com.za869765.imagine.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// xAI chat completions（OpenAI 相容格式）— 目前只給「AI 填表」單發使用。

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatResponseFormat(
    val type: String, // "json_object" = 強制回 JSON
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ChatResponseFormat? = null,
    val temperature: Double? = null,
)

@Serializable
data class ChatChoice(
    val message: ChatMessage? = null,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(),
)
