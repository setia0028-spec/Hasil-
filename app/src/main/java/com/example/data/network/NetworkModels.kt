package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LlamaMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class LlamaChatRequest(
    @Json(name = "messages") val messages: List<LlamaMessage>,
    @Json(name = "temperature") val temperature: Double? = 0.7,
    @Json(name = "max_tokens") val maxTokens: Int? = 512,
    @Json(name = "stream") val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class LlamaChatResponse(
    @Json(name = "choices") val choices: List<LlamaChoice>,
    @Json(name = "model") val model: String? = null
)

@JsonClass(generateAdapter = true)
data class LlamaChoice(
    @Json(name = "message") val message: LlamaMessage,
    @Json(name = "finish_reason") val finishReason: String? = null
)

// Gemini API structures
@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "role") val role: String,
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiSystemInstruction(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiSystemInstruction? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null
)
