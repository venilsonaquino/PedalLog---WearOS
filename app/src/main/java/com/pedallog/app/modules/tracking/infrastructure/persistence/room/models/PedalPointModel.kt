package com.pedallog.app.modules.tracking.infrastructure.persistence.room.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa um ponto de rastreamento GPS capturado durante uma [PedalSessionModel].
 */
@Entity(
    tableName = "pedal_points",
    foreignKeys = [
        ForeignKey(
            entity = PedalSessionModel::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class PedalPointModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Double,
    val distance: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val segmentBreak: Int = 0,
    val elevation: Double = 0.0
)
