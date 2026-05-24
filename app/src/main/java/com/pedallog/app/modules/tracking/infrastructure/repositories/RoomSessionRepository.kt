package com.pedallog.app.modules.tracking.infrastructure.repositories

import com.pedallog.app.modules.tracking.domain.entities.RideSessionEntity
import com.pedallog.app.modules.tracking.domain.repositories.ISessionRepository
import com.pedallog.app.modules.tracking.domain.valueobjects.Coordinate
import com.pedallog.app.modules.tracking.domain.valueobjects.Distance
import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation
import com.pedallog.app.modules.tracking.domain.valueobjects.Speed
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.daos.PedalDao
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.mappers.SessionMapper
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.models.PedalPointModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementação do repositório utilizando Room.
 * Traduz o fluxo de agregados do domínio para o Room Database utilizando o [SessionMapper].
 */
class RoomSessionRepository @Inject constructor(
    private val pedalDao: PedalDao
) : ISessionRepository {

    override suspend fun getActiveSession(): RideSessionEntity? = withContext(Dispatchers.IO) {
        val model = pedalDao.getActiveSession() ?: return@withContext null
        SessionMapper.toDomain(model)
    }

    override suspend fun getSessionById(id: Long): RideSessionEntity? = withContext(Dispatchers.IO) {
        val model = pedalDao.getSessionById(id) ?: return@withContext null
        SessionMapper.toDomain(model)
    }

    override suspend fun save(session: RideSessionEntity): Long = withContext(Dispatchers.IO) {
        val model = SessionMapper.toModel(session)
        val id = if (model.id == 0L) {
            pedalDao.insertSession(model)
        } else {
            pedalDao.updateSession(model)
            model.id
        }
        
        if (session.state.isPaused) {
            pedalDao.markLastPointAsBreak(id)
        }
        
        id
    }

    override suspend fun addLocationPoint(
        sessionId: Long,
        coordinate: Coordinate,
        speed: Speed,
        distance: Distance,
        elevation: Elevation,
        timestamp: Long
    ): Unit = withContext(Dispatchers.IO) {
        val point = PedalPointModel(
            sessionId = sessionId,
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            speed = speed.toKilometersPerHour(),
            distance = distance.toKilometers(),
            elevation = elevation.meters,
            timestamp = timestamp
        )
        pedalDao.insertPoint(point)
    }

    override suspend fun getPointsForSession(sessionId: Long): List<PedalPointModel> = withContext(Dispatchers.IO) {
        pedalDao.getPointsForSession(sessionId)
    }

    override suspend fun markAsSynced(sessionId: Long): Unit = withContext(Dispatchers.IO) {
        val session = pedalDao.getSessionById(sessionId) ?: return@withContext
        pedalDao.updateSession(session.copy(isSynced = true))
    }
}
