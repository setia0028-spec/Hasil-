package com.example.data.database

import androidx.room.*
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.ServerSetting
import com.example.data.model.AiCharacter
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Sessions
    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: String)

    @Transaction
    suspend fun deleteSessionWithMessages(sessionId: String) {
        deleteMessagesBySessionId(sessionId)
        deleteSessionById(sessionId)
    }

    // Messages
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    // Settings
    @Query("SELECT * FROM server_settings")
    fun getAllSettingsFlow(): Flow<List<ServerSetting>>

    @Query("SELECT value FROM server_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: ServerSetting)

    // AI Characters
    @Query("SELECT * FROM ai_characters ORDER BY isDefault DESC, name ASC")
    fun getAllCharacters(): Flow<List<AiCharacter>>

    @Query("SELECT * FROM ai_characters WHERE id = :characterId LIMIT 1")
    suspend fun getCharacterById(characterId: String): AiCharacter?

    @Query("SELECT COUNT(*) FROM ai_characters")
    suspend fun getCharacterCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: AiCharacter)

    @Update
    suspend fun updateCharacter(character: AiCharacter)

    @Query("DELETE FROM ai_characters WHERE id = :characterId")
    suspend fun deleteCharacterById(characterId: String)
}
