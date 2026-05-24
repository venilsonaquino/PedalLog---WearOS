package com.pedallog.app.modules.tracking.domain.valueobjects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ElevationStateTest {

    @Test
    fun shouldInitializeWithEmptyState() {
        val state = ElevationState.createEmpty()
        assertEquals(0.0, state.accumulatedGain.meters, 0.0001)
        assertEquals(null, state.lastRecorded)
    }

    @Test
    fun shouldSetFirstAltitudeOnFirstUpdateWithoutAccumulatingGain() {
        val state = ElevationState.createEmpty()
        val updated = state.update(Elevation(100.0))
        
        assertEquals(0.0, updated.accumulatedGain.meters, 0.0001)
        assertNotNull(updated.lastRecorded)
        assertEquals(100.0, updated.lastRecorded!!.meters, 0.0001)
    }

    @Test
    fun shouldAccumulateElevationGainWhenAltitudeIncreases() {
        val state = ElevationState(Elevation(0.0), Elevation(100.0))
        val updated = state.update(Elevation(115.5))
        
        assertEquals(15.5, updated.accumulatedGain.meters, 0.0001)
        assertEquals(115.5, updated.lastRecorded!!.meters, 0.0001)
    }

    @Test
    fun shouldNotAccumulateElevationGainWhenAltitudeDecreases() {
        val state = ElevationState(Elevation(10.0), Elevation(120.0))
        val updated = state.update(Elevation(110.0))
        
        assertEquals(10.0, updated.accumulatedGain.meters, 0.0001)
        assertEquals(110.0, updated.lastRecorded!!.meters, 0.0001)
    }
}
