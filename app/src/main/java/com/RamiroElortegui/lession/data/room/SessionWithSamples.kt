package com.RamiroElortegui.lession.data.room

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithSamples(
    @Embedded val session: EmgSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val samples: List<EmgSampleEntity>
)
