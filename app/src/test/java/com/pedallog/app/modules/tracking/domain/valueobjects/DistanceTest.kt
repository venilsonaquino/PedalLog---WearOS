package com.pedallog.app.modules.tracking.domain.valueobjects

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {

    @Test
    fun shouldConvertMetersToKilometersCorrectly() {
        val distance = Distance.fromMeters(1500.0)
        assertEquals(1.5, distance.toKilometers(), 0.0001)
    }

    @Test
    fun shouldConvertKilometersToMetersCorrectly() {
        val distance = Distance.fromKilometers(2.5)
        assertEquals(2500.0, distance.meters, 0.0001)
    }

    @Test
    fun shouldAddDistancesCorrectly() {
        val distance1 = Distance(100.0)
        val distance2 = Distance(250.5)
        val sum = distance1.plus(distance2)
        assertEquals(350.5, sum.meters, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldThrowExceptionWhenDistanceIsNegative() {
        Distance(-10.0)
    }
}
