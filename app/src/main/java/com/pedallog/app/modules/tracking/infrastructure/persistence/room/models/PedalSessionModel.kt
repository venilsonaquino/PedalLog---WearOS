package com.pedallog.app.modules.tracking.infrastructure.persistence.room.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

/**
 * Representa o modelo de persistência física de uma jornada de ciclismo (Room Model).
 */
@Entity(tableName = "pedal_sessions")
data class PedalSessionModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Date = Date(),
    val endTime: Date? = null,
    val totalDistance: Float = 0f,
    val isPaused: Boolean = false,
    val isSynced: Boolean = false,
    val activeDurationMs: Long = 0,
    val syncUuid: String = UUID.randomUUID().toString(),
    val totalElevationGain: Float = 0f
)
