package com.RamiroElortegui.lession.data.room

import android.content.Context
import com.RamiroElortegui.lession.data.EmgSample
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import kotlin.math.sqrt

class EmgRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).emgDao()

    val allSessions: Flow<List<EmgSessionEntity>> = dao.getAllSessions()

    suspend fun getSessionDetail(sessionId: Long): SessionWithSamples? {
        return dao.getSessionWithSamples(sessionId)
    }

    suspend fun saveSession(
        source: String,
        startedAt: Long,
        finishedAt: Long,
        sampleRateHz: Int,
        minValue: Float,
        maxValue: Float,
        samples: List<EmgSample>
    ): Long {
        if (samples.isEmpty()) return -1L

        val mav = samples.map { abs(it.value) }.average().toFloat()
        val rms = sqrt(samples.map { it.value * it.value }.average()).toFloat()

        val sessionEntity = EmgSessionEntity(
            source = source,
            startedAt = startedAt,
            finishedAt = finishedAt,
            sampleRateHz = sampleRateHz,
            minimumValue = minValue,
            maximumValue = maxValue,
            meanAbsoluteValue = mav,
            rmsValue = rms,
            sampleCount = samples.size
        )
        val sessionId = dao.insertSession(sessionEntity)

        val sampleEntities = samples.map {
            EmgSampleEntity(
                sessionId = sessionId,
                relativeTimeMs = it.timestampMs,
                value = it.value
            )
        }
        dao.insertSamples(sampleEntities)
        return sessionId
    }

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSamplesBySessionId(sessionId)
        dao.deleteSessionById(sessionId)
    }
}
