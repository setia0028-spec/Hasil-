package com.example.data.network

import retrofit2.Response
import retrofit2.http.*

interface LlamaApiService {
    @GET("v1/models")
    suspend fun getModels(): Response<Map<String, Any>>

    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Body request: LlamaChatRequest
    ): Response<LlamaChatResponse>
}

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
