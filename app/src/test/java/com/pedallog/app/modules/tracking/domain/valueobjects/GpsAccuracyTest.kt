package com.pedallog.app.modules.tracking.domain.valueobjects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários para validar a lógica de precisão do sinal GPS.
 */
class GpsAccuracyTest {

    @Test
    fun isStrong_withAccuracyBelowThreshold_returnsTrue() {
        val accuracy = GpsAccuracy(15.0f)
        assertTrue(accuracy.isStrong())
    }

    @Test
    fun isStrong_withAccuracyAtThreshold_returnsTrue() {
        val accuracy = GpsAccuracy(20.0f)
        assertTrue(accuracy.isStrong())
    }

    @Test
    fun isStrong_withAccuracyAboveThreshold_returnsFalse() {
        val accuracy = GpsAccuracy(25.0f)
        assertFalse(accuracy.isStrong())
    }
}
