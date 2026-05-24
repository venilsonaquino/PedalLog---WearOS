package com.pedallog.app.modules.tracking.infrastructure.persistence.room.mappers

import com.pedallog.app.modules.tracking.domain.entities.RideMetricsEntity
import com.pedallog.app.modules.tracking.domain.entities.RideSessionEntity
import com.pedallog.app.modules.tracking.domain.valueobjects.Distance
import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation
import com.pedallog.app.modules.tracking.domain.valueobjects.ElevationState
import com.pedallog.app.modules.tracking.domain.valueobjects.RidePerformance
import com.pedallog.app.modules.tracking.domain.valueobjects.SessionIdentity
import com.pedallog.app.modules.tracking.domain.valueobjects.SessionState
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.models.PedalSessionModel

/**
 * Tradutor de persistência encarregado de isolar o domínio puro do Room Database (Data Mapper).
 */
object SessionMapper {

    fun toDomain(model: PedalSessionModel): RideSessionEntity {
        val identity = SessionIdentity(
            id = model.id,
            syncUuid = model.syncUuid,
            startTime = model.startTime,
            endTime = model.endTime
        )

        val performance = RidePerformance(
            distance = Distance.fromKilometers(model.totalDistance.toDouble()),
            durationMs = model.activeDurationMs
        )

        val elevationState = ElevationState(
            accumulatedGain = Elevation(model.totalElevationGain.toDouble()),
            lastRecorded = null
        )

        val metrics = RideMetricsEntity(performance, elevationState)
        val state = SessionState(isPaused = model.isPaused, metrics = metrics)

        return RideSessionEntity(identity, state)
    }

    fun toModel(domain: RideSessionEntity, isSynced: Boolean = false): PedalSessionModel {
        return PedalSessionModel(
            id = domain.identity.id,
            startTime = domain.identity.startTime,
            endTime = domain.identity.endTime,
            totalDistance = domain.state.metrics.performance.distance.toKilometers().toFloat(),
            isPaused = domain.state.isPaused,
            isSynced = isSynced,
            activeDurationMs = domain.state.metrics.performance.durationMs,
            syncUuid = domain.identity.syncUuid,
            totalElevationGain = domain.state.metrics.elevationState.accumulatedGain.meters.toFloat()
        )
    }
}
