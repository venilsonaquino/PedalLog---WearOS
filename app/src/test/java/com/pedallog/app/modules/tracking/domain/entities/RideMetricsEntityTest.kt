package com.pedallog.app.modules.tracking.domain.entities

import com.pedallog.app.modules.tracking.domain.valueobjects.Distance
import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation
import com.pedallog.app.modules.tracking.domain.valueobjects.ElevationState
import com.pedallog.app.modules.tracking.domain.valueobjects.RidePerformance
import org.junit.Assert.assertEquals
import org.junit.Test

class RideMetricsEntityTest {

    @Test
    fun shouldCreateEmptyMetrics() {
        val metrics = RideMetricsEntity.createEmpty()
        assertEquals(0.0, metrics.performance.distance.meters, 0.0001)
        assertEquals(0.0, metrics.elevationState.accumulatedGain.meters, 0.0001)
    }

    @Test
    fun shouldUpdateMetricsCorrectly() {
        val initialMetrics = RideMetricsEntity(
            performance = RidePerformance(Distance(100.0), 0L),
            elevationState = ElevationState(Elevation(5.0), Elevation(200.0))
        )

        // Simular um deslocamento de mais 50 metros subindo até 210 metros
        val updatedMetrics = initialMetrics.update(
            segmentDistance = Distance(50.0),
            currentElevation = Elevation(210.0)
        )

        assertEquals(150.0, updatedMetrics.performance.distance.meters, 0.0001)
        assertEquals(15.0, updatedMetrics.elevationState.accumulatedGain.meters, 0.0001)
        assertEquals(210.0, updatedMetrics.elevationState.lastRecorded!!.meters, 0.0001)
    }

    @Test
    fun shouldIncrementTimeCorrectly() {
        val metrics = RideMetricsEntity.createEmpty()
        val updated = metrics.incrementTime(5000L)

        assertEquals(5000L, updated.performance.durationMs)
        assertEquals(0.0, updated.elevationState.accumulatedGain.meters, 0.0001)
    }
}
