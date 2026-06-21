package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.ChatDao
import com.example.data.database.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.ServerSetting
import com.example.data.network.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ChatRepository(private val context: Context) {
    private val chatDao: ChatDao = AppDatabase.getDatabase(context).chatDao()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val geminiRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(okHttpClient)
        .build()

    private val geminiService: GeminiApiService = geminiRetrofit.create(GeminiApiService::class.java)

    // Flow getters
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getMessages(sessionId: String): Flow<List<ChatMessage>> = chatDao.getMessagesForSession(sessionId)

    // Database interactions
    suspend fun createNewSession(title: String): ChatSession = withContext(Dispatchers.IO) {
        val session = ChatSession(title = title)
        chatDao.insertSession(session)
        session
    }

    suspend fun updateSessionTitle(sessionId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            chatDao.insertSession(session.copy(title = newTitle))
        }
    }

    suspend fun addMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteSessionWithMessages(sessionId)
    }

    suspend fun clearSessionMessages(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesBySessionId(sessionId)
    }

    // Dynamic Server Setting values
    suspend fun getSetting(key: String, defaultValue: String): String {
        return chatDao.getSettingValue(key) ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) {
        chatDao.insertSetting(ServerSetting(key, value))
    }

    // Dynamic Llama Service Builder
    private fun getLlamaService(baseUrl: String): LlamaApiService {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
            .create(LlamaApiService::class.java)
    }

    // Health check local Llama server
    suspend fun checkLlamaHealth(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getLlamaService(serverUrl)
            val response = service.getModels()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("ChatRepository", "Llama health check failed: ${e.message}")
            false
        }
    }

    // Chat completion routing
    suspend fun getChatCompletion(
        sessionId: String,
        historyMessages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Double,
        maxTokens: Int
    ): ChatMessage = withContext(Dispatchers.IO) {
        // Read active settings
        val useFallback = getSetting("use_fallback_gemini", "false").toBoolean()
        val serverUrl = getSetting("server_url", "http://10.0.2.2:8080")

        if (useFallback) {
            // Use Gemini
            return@withContext callGemini(historyMessages, systemPrompt)
        } else {
            // Use Llama.cpp
            return@withContext callLlama(serverUrl, historyMessages, systemPrompt, temperature, maxTokens)
        }
    }

    private suspend fun callLlama(
        serverUrl: String,
        historyMessages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Double,
        maxTokens: Int
    ): ChatMessage {
        try {
            val service = getLlamaService(serverUrl)

            // Pack system prompt + history messages
            val promptList = mutableListOf<LlamaMessage>()
            if (systemPrompt.isNotEmpty()) {
                promptList.add(LlamaMessage(role = "system", content = systemPrompt))
            }
            historyMessages.forEach { msg ->
                promptList.add(LlamaMessage(role = msg.role, content = msg.content))
            }

            val request = LlamaChatRequest(
                messages = promptList,
                temperature = temperature,
                maxTokens = maxTokens,
                stream = false
            )

            val response = service.chatCompletions(request)
            if (response.isSuccessful && response.body() != null) {
                val choice = response.body()!!.choices.firstOrNull()
                if (choice != null) {
                    return ChatMessage(
                        sessionId = historyMessages.first().sessionId,
                        role = "assistant",
                        content = choice.message.content
                    )
                }
            }
            
            // Build error message
            val errorBody = response.errorBody()?.string() ?: "Hubungan terputus atau format server llama.cpp tidak sesuai."
            return ChatMessage(
                sessionId = historyMessages.first().sessionId,
                role = "assistant",
                content = "Error: Llama server error (${response.code()}) - $errorBody",
                isError = true
            )
        } catch (e: Exception) {
            Log.e("ChatRepository", "Llama communication error", e)
            return ChatMessage(
                sessionId = historyMessages.first().sessionId,
                role = "assistant",
                content = "Gagal menghubungi Server llama.cpp di $serverUrl.\n\nPastikan llama-server sedang berjalan di PC Anda dan HP/Emulator dapat mengakses alamat ini. (Gunakan 'fallback Gemini' di Pengaturan jika ingin mencoba simulasi langsung!).\n\nDetail: ${e.localizedMessage}",
                isError = true
            )
        }
    }

    private suspend fun callGemini(
        historyMessages: List<ChatMessage>,
        systemPrompt: String
    ): ChatMessage {
        try {
            // Obtain Key
            var apiKey = getSetting("gemini_api_key", "")
            if (apiKey.isEmpty()) {
                // Read from BuildConfig (inserted from Secret panel)
                apiKey = BuildConfig.GEMINI_API_KEY
            }

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return ChatMessage(
                    sessionId = historyMessages.first().sessionId,
                    role = "assistant",
                    content = "Kunci API Gemini tidak ditemukan. Harap masukkan API key yang valid di Pengaturan atau melalui panel Rahasia agar fallback Gemini berfungsi.",
                    isError = true
                )
            }

            // Map messages to Gemini format: ONLY supports alternate roles user/model
            val contents = mutableListOf<GeminiContent>()
            historyMessages.forEach { msg ->
                val geminiRole = if (msg.role == "assistant") "model" else "user"
                contents.add(
                    GeminiContent(
                        role = geminiRole,
                        parts = listOf(GeminiPart(text = msg.content))
                    )
                )
            }

            val systemInstruction = if (systemPrompt.isNotEmpty()) {
                GeminiSystemInstruction(parts = listOf(GeminiPart(text = systemPrompt)))
            } else null

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = systemInstruction
            )

            // Call API
            val response = geminiService.generateContent(
                model = "gemini-1.5-flash",
                apiKey = apiKey,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val modelText = response.body()!!.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (modelText != null) {
                    return ChatMessage(
                        sessionId = historyMessages.first().sessionId,
                        role = "assistant",
                        content = modelText
                    )
                }
            }

            val errorMsg = response.errorBody()?.string() ?: "Kesalahan yang tidak diketahui dari API Gemini."
            return ChatMessage(
                sessionId = historyMessages.first().sessionId,
                role = "assistant",
                content = "Error Gemini API: $errorMsg",
                isError = true
            )
        } catch (e: Exception) {
            Log.e("ChatRepository", "Gemini call error", e)
            return ChatMessage(
                sessionId = historyMessages.first().sessionId,
                role = "assistant",
                content = "Kesalahan koneksi Gemini: ${e.localizedMessage}",
                isError = true
            )
        }
    }
}
