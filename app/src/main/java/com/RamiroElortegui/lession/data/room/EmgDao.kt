package com.RamiroElortegui.lession.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EmgDao {
    @Query("SELECT * FROM sessions ORDER BY finishedAt DESC")
    fun getAllSessions(): Flow<List<EmgSessionEntity>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionWithSamples(sessionId: Long): SessionWithSamples?

    @Insert
    suspend fun insertSession(session: EmgSessionEntity): Long

    @Insert
    suspend fun insertSamples(samples: List<EmgSampleEntity>)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM samples WHERE sessionId = :sessionId")
    suspend fun deleteSamplesBySessionId(sessionId: Long)
}
