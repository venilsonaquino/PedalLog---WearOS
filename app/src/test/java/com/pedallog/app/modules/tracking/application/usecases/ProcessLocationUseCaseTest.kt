package com.pedallog.app.modules.tracking.application.usecases

import com.pedallog.app.modules.tracking.domain.entities.RideSessionEntity
import com.pedallog.app.modules.tracking.domain.repositories.ISessionRepository
import com.pedallog.app.modules.tracking.domain.valueobjects.*
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.models.PedalPointModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessLocationUseCaseTest {

    private val fakeRepository = FakeSessionRepository()
    private val useCase = ProcessLocationUseCase(fakeRepository)

    @Test
    fun shouldDoNothingWhenNoActiveSessionExists() = runBlocking {
        fakeRepository.activeSession = null
        val update = createUpdate(10.0, 50.0, 100.0)

        useCase.execute(update)

        assertNull(fakeRepository.savedSession)
        assertNull(fakeRepository.addedPoint)
    }

    @Test
    fun shouldUpdateMetricsAndAddLocationPointWhenActiveSessionExists() = runBlocking {
        val active = RideSessionEntity.createNew(id = 88L)
        fakeRepository.activeSession = active
        val update = createUpdate(10.0, 50.0, 100.0)

        useCase.execute(update)

        val saved = fakeRepository.savedSession
        assertNotNull(saved)
        assertEquals(88L, saved!!.identity.id)
        
        assertEquals(50.0, saved.state.metrics.performance.distance.meters, 0.0001)
        assertEquals(0.0, saved.state.metrics.elevationState.accumulatedGain.meters, 0.0001)

        val point = fakeRepository.addedPoint
        assertNotNull(point)
        assertEquals(88L, point!!.sessionId)
        assertEquals(-23.5, point.latitude, 0.0001)
        assertEquals(10.0, point.speed, 0.0001)
        assertEquals(50.0, point.distance, 0.0001)
    }

    private fun createUpdate(speedMs: Double, distanceM: Double, elevationM: Double): LocationUpdate {
        return LocationUpdate(
            coordinate = Coordinate(-23.5, -46.6),
            speed = Speed(speedMs),
            segmentDistance = Distance(distanceM),
            elevation = Elevation(elevationM),
            timestamp = 1000L
        )
    }

    class FakeSessionRepository : ISessionRepository {
        var activeSession: RideSessionEntity? = null
        var savedSession: RideSessionEntity? = null
        var addedPoint: PedalPointModel? = null
        var isSyncedMarked = false

        override suspend fun getActiveSession(): RideSessionEntity? = activeSession

        override suspend fun getSessionById(id: Long): RideSessionEntity? = savedSession

        override suspend fun save(session: RideSessionEntity): Long {
            savedSession = session
            return session.identity.id
        }

        override suspend fun addLocationPoint(
            sessionId: Long,
            coordinate: Coordinate,
            speed: Speed,
            distance: Distance,
            elevation: Elevation,
            timestamp: Long
        ) {
            addedPoint = PedalPointModel(
                sessionId = sessionId,
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                speed = speed.toKilometersPerHour(),
                distance = distance.toKilometers(),
                elevation = elevation.meters,
                timestamp = timestamp
            )
        }

        override suspend fun getPointsForSession(sessionId: Long): List<PedalPointModel> = emptyList()

        override suspend fun markAsSynced(sessionId: Long) {
            isSyncedMarked = true
        }
    }
}
