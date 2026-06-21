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

    val allCharacters: Flow<List<com.example.data.model.AiCharacter>> = chatDao.getAllCharacters()

    fun getMessages(sessionId: String): Flow<List<ChatMessage>> = chatDao.getMessagesForSession(sessionId)

    // Database interactions
    suspend fun createNewSession(
        title: String,
        isGroupChat: Boolean = false,
        groupAvatar: String = "👥",
        participantIds: String = ""
    ): ChatSession = withContext(Dispatchers.IO) {
        val session = ChatSession(
            title = title,
            isGroupChat = isGroupChat,
            groupAvatar = groupAvatar,
            participantIds = participantIds
        )
        chatDao.insertSession(session)
        session
    }

    // AI Characters CRUD
    suspend fun seedDefaultCharactersIfNeeded() = withContext(Dispatchers.IO) {
        if (chatDao.getCharacterCount() == 0) {
            val defaults = listOf(
                com.example.data.model.AiCharacter(
                    name = "Socrates",
                    emoji = "🏛️",
                    personality = "Kamu adalah Socrates, filsuf Yunani kuno. Jawab pertanyaan dengan metode Sokrates (bertanya balik dengan bijaksana untuk memancing pemikiran mendalam), ramah tapi memancing rasa ingin tahu, gunakan bahasa Indonesia kuno yang sopan.",
                    isDefault = true
                ),
                com.example.data.model.AiCharacter(
                    name = "Sherlock Holmes",
                    emoji = "🕵️‍♂️",
                    personality = "Kamu adalah Sherlock Holmes, detektif legendaris. Jawab secara analitis, tajam, dingin, suka mengamati detail kecil yang dilewatkan orang lain, dan skeptis.",
                    isDefault = true
                ),
                com.example.data.model.AiCharacter(
                    name = "Komedian Jenaka",
                    emoji = "🤡",
                    personality = "Kamu adalah komedian yang selalu ceria dan suka membuat lelucon atau plesetan kata (pun) dalam bahasa Indonesia. Semua jawabanmu harus diselingi humor cerdas.",
                    isDefault = true
                ),
                com.example.data.model.AiCharacter(
                    name = "Cyberpunk Hacker",
                    emoji = "💻",
                    personality = "Kamu adalah hacker elite dari masa depan distopia. Gaya bicaramu penuh istilah teknologi, sering menyisipkan glitch, keren, misterius, dan solutif.",
                    isDefault = true
                )
            )
            defaults.forEach { chatDao.insertCharacter(it) }
        }
    }

    suspend fun insertCharacter(character: com.example.data.model.AiCharacter) = withContext(Dispatchers.IO) {
        chatDao.insertCharacter(character)
    }

    suspend fun updateCharacter(character: com.example.data.model.AiCharacter) = withContext(Dispatchers.IO) {
        chatDao.updateCharacter(character)
    }

    suspend fun deleteCharacterById(characterId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteCharacterById(characterId)
    }

    suspend fun updateSessionTitle(sessionId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            chatDao.insertSession(session.copy(title = newTitle))
        }
    }

    suspend fun updateSessionDetails(
        sessionId: String,
        newTitle: String,
        newAvatar: String,
        newParticipantIds: String
    ) = withContext(Dispatchers.IO) {
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            chatDao.insertSession(session.copy(
                title = newTitle,
                groupAvatar = newAvatar,
                participantIds = newParticipantIds
            ))
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

    private fun formatHistoryForGroup(historyMessages: List<ChatMessage>): List<ChatMessage> {
        return historyMessages.map { msg ->
            if (msg.role == "assistant" && msg.senderName != null) {
                msg.copy(content = "[${msg.senderEmoji ?: ""} ${msg.senderName}]: ${msg.content}")
            } else if (msg.role == "user") {
                msg.copy(content = "[User]: ${msg.content}")
            } else {
                msg
            }
        }
    }

    suspend fun getCharacterCompletion(
        sessionId: String,
        historyMessages: List<ChatMessage>,
        character: com.example.data.model.AiCharacter,
        temperature: Double,
        maxTokens: Int
    ): ChatMessage = withContext(Dispatchers.IO) {
        val useFallback = getSetting("use_fallback_gemini", "false").toBoolean()
        val serverUrl = getSetting("server_url", "http://10.0.2.2:8080")

        val formattedHistory = formatHistoryForGroup(historyMessages)
        val systemPromptCombine = "Personality: ${character.personality}\n\nYou are locked in a group chat named \"Group AI Diskusi\" with other AI characters. Respond creatively as ${character.name} ${character.emoji} to the conversation flow/most recent message. Keep responses concise, natural, and under 5 sentences. Speak in Indonesian."

        val responseMsg = if (useFallback) {
            callGemini(formattedHistory, systemPromptCombine)
        } else {
            callLlama(serverUrl, formattedHistory, systemPromptCombine, temperature, maxTokens)
        }

        responseMsg.copy(
            senderName = character.name,
            senderEmoji = character.emoji
        )
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
