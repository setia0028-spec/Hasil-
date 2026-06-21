package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)

    // Sessions flow
    val sessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Characters flow
    val characters: StateFlow<List<com.example.data.model.AiCharacter>> = repository.allCharacters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active session selection
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    // Active messages list flow
    val messages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) {
                flow { emit(emptyList()) }
            } else {
                repository.getMessages(sessionId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings States
    private val _serverUrl = MutableStateFlow("http://10.0.2.2:8080")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _systemPrompt = MutableStateFlow("Anda adalah asisten AI lokal yang ramah.")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _maxTokens = MutableStateFlow(512)
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _useFallbackGemini = MutableStateFlow(false)
    val useFallbackGemini: StateFlow<Boolean> = _useFallbackGemini.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    // UI auxiliary states
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _serverHealthStatus = MutableStateFlow<Boolean?>(null) // null = unchecked, true = ok, false = dead
    val serverHealthStatus: StateFlow<Boolean?> = _serverHealthStatus.asStateFlow()

    private val _isCheckingHealth = MutableStateFlow(false)
    val isCheckingHealth: StateFlow<Boolean> = _isCheckingHealth.asStateFlow()

    init {
        loadSettings()
        // Try selecting the first session eventually on first start if available
        viewModelScope.launch {
            sessions.first { it.isNotEmpty() }.firstOrNull()?.let { firstSession ->
                if (_currentSessionId.value == null) {
                    _currentSessionId.value = firstSession.id
                }
            }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            _serverUrl.value = repository.getSetting("server_url", "http://10.0.2.2:8080")
            _systemPrompt.value = repository.getSetting("system_prompt", "Anda adalah asisten AI lokal yang ramah.")
            _temperature.value = repository.getSetting("temperature", "0.7").toFloatOrNull() ?: 0.7f
            _maxTokens.value = repository.getSetting("max_tokens", "512").toIntOrNull() ?: 512
            _useFallbackGemini.value = repository.getSetting("use_fallback_gemini", "false").toBoolean()
            _geminiApiKey.value = repository.getSetting("gemini_api_key", "")
            checkServerHealth()
        }
    }

    fun saveSettings(
        url: String,
        prompt: String,
        temp: Float,
        tokens: Int,
        fallback: Boolean,
        apiKey: String
    ) {
        viewModelScope.launch {
            repository.saveSetting("server_url", url)
            repository.saveSetting("system_prompt", prompt)
            repository.saveSetting("temperature", temp.toString())
            repository.saveSetting("max_tokens", tokens.toString())
            repository.saveSetting("use_fallback_gemini", fallback.toString())
            repository.saveSetting("gemini_api_key", apiKey)

            _serverUrl.value = url
            _systemPrompt.value = prompt
            _temperature.value = temp
            _maxTokens.value = tokens
            _useFallbackGemini.value = fallback
            _geminiApiKey.value = apiKey

            checkServerHealth()
        }
    }

    fun checkServerHealth() {
        if (_useFallbackGemini.value) {
            _serverHealthStatus.value = true // Gemini is assumed cloud online
            return
        }
        viewModelScope.launch {
            _isCheckingHealth.value = true
            _serverHealthStatus.value = repository.checkLlamaHealth(_serverUrl.value)
            _isCheckingHealth.value = false
        }
    }

    fun selectSession(sessionId: String?) {
        _currentSessionId.value = sessionId
    }

    fun createSession() {
        viewModelScope.launch {
            val sessionCount = sessions.value.size
            val freshSession = repository.createNewSession("Diskusi Baru #${sessionCount + 1}", isGroupChat = false)
            _currentSessionId.value = freshSession.id
        }
    }

    fun createGroupSession(title: String, groupAvatar: String, participantIds: String) {
        viewModelScope.launch {
            val freshSession = repository.createNewSession(
                title = title.ifBlank { "Group Chat" },
                isGroupChat = true,
                groupAvatar = groupAvatar.ifBlank { "👥" },
                participantIds = participantIds
            )
            _currentSessionId.value = freshSession.id
        }
    }

    fun updateGroupSession(sessionId: String, title: String, groupAvatar: String, participantIds: String) {
        viewModelScope.launch {
            repository.updateSessionDetails(sessionId, title, groupAvatar, participantIds)
        }
    }

    fun addCharacter(name: String, emoji: String, personality: String) {
        viewModelScope.launch {
            repository.insertCharacter(
                com.example.data.model.AiCharacter(
                    name = name,
                    emoji = emoji,
                    personality = personality
                )
            )
        }
    }

    fun updateCharacter(character: com.example.data.model.AiCharacter) {
        viewModelScope.launch {
            repository.updateCharacter(character)
        }
    }

    fun deleteCharacter(characterId: String) {
        viewModelScope.launch {
            repository.deleteCharacterById(characterId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                val remSessions = sessions.value.filter { it.id != sessionId }
                _currentSessionId.value = remSessions.firstOrNull()?.id
            }
        }
    }

    fun clearActiveSessionMessages() {
        val currentId = _currentSessionId.value ?: return
        viewModelScope.launch {
            repository.clearSessionMessages(currentId)
        }
    }

    fun triggerNextCharacterResponse() {
        val currentId = _currentSessionId.value ?: return
        val currentSession = sessions.value.find { it.id == currentId } ?: return
        if (!currentSession.isGroupChat || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            val mHistory = messages.value
            val activeIds = currentSession.participantIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val charList = characters.value.filter { activeIds.contains(it.id) }
            if (charList.isNotEmpty()) {
                // Try and alternate so the same speaker doesn't speak twice in a row
                val lastSpeakerName = mHistory.lastOrNull { it.role == "assistant" }?.senderName
                val nextChar = charList.find { it.name != lastSpeakerName } ?: charList.first()

                val responseMsg = repository.getCharacterCompletion(
                    sessionId = currentId,
                    historyMessages = mHistory,
                    character = nextChar,
                    temperature = _temperature.value.toDouble(),
                    maxTokens = _maxTokens.value
                )
                repository.addMessage(responseMsg)
            }
            _isGenerating.value = false
        }
    }

    fun sendMessage(inputText: String) {
        if (inputText.isBlank() || _isGenerating.value) return
        val currentId = _currentSessionId.value ?: return
        val currentSession = sessions.value.find { it.id == currentId } ?: return

        viewModelScope.launch {
            _isGenerating.value = true

            // 1. Save user message locally
            val userMsg = ChatMessage(
                sessionId = currentId,
                role = "user",
                content = inputText
            )
            repository.addMessage(userMsg)

            // Auto rename title if it was default and not a group chat
            if (!currentSession.isGroupChat && currentSession.title.startsWith("Diskusi Baru")) {
                val cleanTitle = if (inputText.length > 25) inputText.take(22) + "..." else inputText
                repository.updateSessionTitle(currentId, cleanTitle)
            }

            if (currentSession.isGroupChat) {
                // Filter characters to only include selected participant ids
                val activeIds = currentSession.participantIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val charList = characters.value.filter { activeIds.contains(it.id) }
                if (charList.isNotEmpty()) {
                    for (char in charList) {
                        val mHistory = repository.getMessages(currentId).first()
                        val responseMsg = repository.getCharacterCompletion(
                            sessionId = currentId,
                            historyMessages = mHistory,
                            character = char,
                            temperature = _temperature.value.toDouble(),
                            maxTokens = _maxTokens.value
                        )
                        repository.addMessage(responseMsg)
                        // Organic pacing delay
                        kotlinx.coroutines.delay(1000)
                    }
                } else {
                    // Fallback if no characters exist/are selected
                    val mHistory = repository.getMessages(currentId).first()
                    val responseMsg = repository.getChatCompletion(
                        sessionId = currentId,
                        historyMessages = mHistory,
                        systemPrompt = _systemPrompt.value,
                        temperature = _temperature.value.toDouble(),
                        maxTokens = _maxTokens.value
                    )
                    repository.addMessage(responseMsg)
                }
            } else {
                // Standard single chat
                val mHistory = repository.getMessages(currentId).first()
                val responseMsg = repository.getChatCompletion(
                    sessionId = currentId,
                    historyMessages = mHistory,
                    systemPrompt = _systemPrompt.value,
                    temperature = _temperature.value.toDouble(),
                    maxTokens = _maxTokens.value
                )
                repository.addMessage(responseMsg)
            }
            _isGenerating.value = false
        }
    }
}
