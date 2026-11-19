package com.RamiroElortegui.lession.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class EmgSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val source: String, // "SIMULATED" or "BLE"
    val startedAt: Long,
    val finishedAt: Long,
    val sampleRateHz: Int,
    val minimumValue: Float,
    val maximumValue: Float,
    val meanAbsoluteValue: Float,
    val rmsValue: Float,
    val sampleCount: Int
)
