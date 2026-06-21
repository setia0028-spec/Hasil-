package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modelName: String = "llama.cpp",
    val isGroupChat: Boolean = false,
    val groupAvatar: String = "👥",
    val participantIds: String = ""
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val senderName: String? = null,
    val senderEmoji: String? = null
)

@Entity(tableName = "ai_characters")
data class AiCharacter(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val personality: String,
    val emoji: String,
    val isDefault: Boolean = false
)

@Entity(tableName = "server_settings")
data class ServerSetting(
    @PrimaryKey val key: String,
    val value: String
)
