package com.pedallog.app.modules.tracking.domain.valueobjects

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationUpdateTest {

    @Test
    fun shouldInitializeLocationUpdateCorrectly() {
        val speed = Speed(12.5) // m/s
        val segmentDistance = Distance(50.0) // metros
        val elevation = Elevation(250.0)
        val timestamp = 1716584400000L
        val coordinate = Coordinate(-23.5505, -46.6333)

        val locationUpdate = LocationUpdate(
            coordinate = coordinate,
            speed = speed,
            segmentDistance = segmentDistance,
            elevation = elevation,
            timestamp = timestamp
        )

        assertEquals(-23.5505, locationUpdate.coordinate.latitude, 0.0001)
        assertEquals(-46.6333, locationUpdate.coordinate.longitude, 0.0001)
        
        // Valida propriedades consolidadas
        assertEquals(12.5, locationUpdate.speed.metersPerSecond, 0.0001)
        assertEquals(50.0, locationUpdate.segmentDistance.meters, 0.0001)
        assertEquals(250.0, locationUpdate.elevation.meters, 0.0001)
        assertEquals(1716584400000L, locationUpdate.timestamp)
    }
}
