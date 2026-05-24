package com.pedallog.app.modules.tracking.domain.repositories

import com.pedallog.app.modules.tracking.domain.entities.RideSessionEntity
import com.pedallog.app.modules.tracking.domain.valueobjects.Coordinate
import com.pedallog.app.modules.tracking.domain.valueobjects.Distance
import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation
import com.pedallog.app.modules.tracking.domain.valueobjects.Speed
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.models.PedalPointModel

/**
 * Contrato de repositório de domínio para persistência de sessões de ciclismo.
 */
interface ISessionRepository {

    suspend fun getActiveSession(): RideSessionEntity?

    suspend fun getSessionById(id: Long): RideSessionEntity?

    suspend fun save(session: RideSessionEntity): Long

    suspend fun addLocationPoint(
        sessionId: Long,
        coordinate: Coordinate,
        speed: Speed,
        distance: Distance,
        elevation: Elevation,
        timestamp: Long
    )

    suspend fun getPointsForSession(sessionId: Long): List<PedalPointModel>

    suspend fun markAsSynced(sessionId: Long)
}
