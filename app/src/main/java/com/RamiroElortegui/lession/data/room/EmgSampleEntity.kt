package com.RamiroElortegui.lession.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "samples")
data class EmgSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val relativeTimeMs: Long,
    val value: Float
)
