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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SessionMapperTest {

    @Test
    fun shouldMapModelToDomainCorrectly() {
        val date = Date()
        val model = PedalSessionModel(
            id = 42L,
            startTime = date,
            endTime = null,
            totalDistance = 12.5f,
            isPaused = true,
            isSynced = false,
            activeDurationMs = 3600000L,
            syncUuid = "test-uuid-123",
            totalElevationGain = 150.5f
        )

        val domain = SessionMapper.toDomain(model)

        assertEquals(42L, domain.identity.id)
        assertEquals("test-uuid-123", domain.identity.syncUuid)
        assertEquals(date, domain.identity.startTime)
        assertNull(domain.identity.endTime)
        assertTrue(domain.state.isPaused)

        assertEquals(12500.0, domain.state.metrics.performance.distance.meters, 0.0001)
        assertEquals(3600000L, domain.state.metrics.performance.durationMs)
        assertEquals(150.5, domain.state.metrics.elevationState.accumulatedGain.meters, 0.0001)
        assertNull(domain.state.metrics.elevationState.lastRecorded)
    }

    @Test
    fun shouldMapDomainToModelCorrectly() {
        val date = Date()
        val identity = SessionIdentity(
            id = 99L,
            syncUuid = "another-uuid",
            startTime = date,
            endTime = date
        )
        val performance = RidePerformance(
            distance = Distance.fromMeters(25000.0),
            durationMs = 7200000L
        )
        val elevationState = ElevationState(
            accumulatedGain = Elevation(350.0),
            lastRecorded = null
        )
        val metrics = RideMetricsEntity(performance, elevationState)
        val state = SessionState(isPaused = false, metrics = metrics)
        val domain = RideSessionEntity(identity, state)

        val model = SessionMapper.toModel(domain, isSynced = true)

        assertEquals(99L, model.id)
        assertEquals("another-uuid", model.syncUuid)
        assertEquals(date, model.startTime)
        assertEquals(date, model.endTime)
        assertFalse(model.isPaused)
        assertTrue(model.isSynced)

        assertEquals(25.0f, model.totalDistance, 0.0001f)
        assertEquals(7200000L, model.activeDurationMs)
        assertEquals(350.0f, model.totalElevationGain, 0.0001f)
    }
}
