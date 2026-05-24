package com.pedallog.app.modules.tracking.application.usecases

import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation

/**
 * Caso de uso puro para calcular o ganho de elevação acumulado.
 * Segue a regra de Object Calisthenics: sem "else" (Early Return).
 */
object CalculateElevation {

    /**
     * Calcula o ganho positivo de elevação entre o ponto anterior e o atual.
     * Se for uma subida, retorna a diferença em metros.
     * Se for descida ou se não houver ponto anterior, retorna 0.0.
     */
    fun calculateGain(
        previous: Elevation?,
        current: Elevation
    ): Double {
        if (previous == null) {
            return 0.0
        }

        val difference = current.meters - previous.meters
        if (difference > 0.0) {
            return difference
        }

        return 0.0
    }
}
