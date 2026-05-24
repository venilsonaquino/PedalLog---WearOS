package com.pedallog.app.modules.tracking.application.usecases

import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes unitários para validar a lógica de cálculo de ganho de elevação.
 */
class CalculateElevationTest {

    @Test
    fun calculateGain_withNullPrevious_returnsZero() {
        val current = Elevation(100.0)
        val gain = CalculateElevation.calculateGain(null, current)
        assertEquals(0.0, gain, 0.001)
    }

    @Test
    fun calculateGain_withAscendingElevation_returnsDifference() {
        val previous = Elevation(100.0)
        val current = Elevation(150.0)
        val gain = CalculateElevation.calculateGain(previous, current)
        assertEquals(50.0, gain, 0.001)
    }

    @Test
    fun calculateGain_withDescendingElevation_returnsZero() {
        val previous = Elevation(100.0)
        val current = Elevation(80.0)
        val gain = CalculateElevation.calculateGain(previous, current)
        assertEquals(0.0, gain, 0.001)
    }

    @Test
    fun calculateGain_withEqualElevation_returnsZero() {
        val previous = Elevation(100.0)
        val current = Elevation(100.0)
        val gain = CalculateElevation.calculateGain(previous, current)
        assertEquals(0.0, gain, 0.001)
    }
}
