package com.pedallog.app.modules.tracking.domain.valueobjects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeedTest {

    @Test
    fun shouldConvertMetersPerSecondToKilometersPerHour() {
        val speed = Speed.fromMetersPerSecond(10.0) // 10 m/s = 36 km/h
        assertEquals(36.0, speed.toKilometersPerHour(), 0.0001)
    }

    @Test
    fun shouldDetectSlowSpeed() {
        val slowSpeed = Speed(0.4)
        val normalSpeed = Speed(0.6)
        assertTrue(slowSpeed.isSlow())
        assertFalse(normalSpeed.isSlow())
    }

    @Test
    fun shouldDetectMovingSpeed() {
        val movingSpeed = Speed(1.2)
        val stoppedSpeed = Speed(0.8)
        assertTrue(movingSpeed.isMoving())
        assertFalse(stoppedSpeed.isMoving())
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldThrowExceptionWhenSpeedIsNegative() {
        Speed(-2.0)
    }
}
