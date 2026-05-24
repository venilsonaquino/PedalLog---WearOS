package com.pedallog.app.modules.tracking.domain.entities

import com.pedallog.app.modules.tracking.domain.valueobjects.Distance
import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSessionEntityTest {

    @Test
    fun shouldInitializeActiveSessionCorrectly() {
        val session = RideSessionEntity.createNew(id = 12L)

        assertEquals(12L, session.identity.id)
        assertFalse(session.state.isPaused)
        assertEquals(0.0, session.state.metrics.performance.distance.meters, 0.0001)
        assertEquals(0, session.state.metrics.performance.durationMs)
        assertEquals(0.0, session.state.metrics.elevationState.accumulatedGain.meters, 0.0001)
        assertFalse(session.isFinished())
    }

    @Test
    fun shouldPauseAndResumeSession() {
        var session = RideSessionEntity.createNew()

        session = session.pause()
        assertTrue(session.state.isPaused)

        session = session.resume()
        assertFalse(session.state.isPaused)
    }

    @Test
    fun shouldUpdateMetricsWhenActive() {
        var session = RideSessionEntity.createNew()

        // Simular movimento
        session = session.updateMetrics(Distance(100.0), Elevation(150.0))

        assertEquals(100.0, session.state.metrics.performance.distance.meters, 0.0001)
        assertEquals(150.0, session.state.metrics.elevationState.lastRecorded!!.meters, 0.0001)
    }

    @Test
    fun shouldNotUpdateMetricsWhenPaused() {
        var session = RideSessionEntity.createNew()
        session = session.pause()

        session = session.updateMetrics(Distance(100.0), Elevation(150.0))

        assertEquals(0.0, session.state.metrics.performance.distance.meters, 0.0001)
    }

    @Test
    fun shouldTickTimeWhenActive() {
        var session = RideSessionEntity.createNew()
        session = session.tickTime(1000L)

        assertEquals(1000L, session.state.metrics.performance.durationMs)
    }

    @Test
    fun shouldNotTickTimeWhenPaused() {
        var session = RideSessionEntity.createNew()
        session = session.pause()
        session = session.tickTime(1000L)

        assertEquals(0L, session.state.metrics.performance.durationMs)
    }

    @Test
    fun shouldFinishSession() {
        var session = RideSessionEntity.createNew()

        session = session.finish()

        assertTrue(session.isFinished())
        assertNotNull(session.identity.endTime)
    }

    @Test
    fun shouldIgnoreActionsWhenFinished() {
        var session = RideSessionEntity.createNew()
        session = session.finish()

        // Tentativas de alterar o estado após finalização devem ser ignoradas
        val paused = session.pause()
        assertEquals(session, paused)

        val updated = session.updateMetrics(Distance(100.0), Elevation(150.0))
        assertEquals(session, updated)

        val ticked = session.tickTime(1000L)
        assertEquals(session, ticked)
    }
}
