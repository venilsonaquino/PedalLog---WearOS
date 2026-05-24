package com.pedallog.app.modules.tracking.domain.valueobjects

/**
 * Representa a velocidade de deslocamento de uma atividade de ciclismo.
 * Encapsula a velocidade em metros por segundo e fornece regras de conversão
 * e classificação de atividade física.
 */
data class Speed(val metersPerSecond: Double) {

    init {
        require(metersPerSecond >= 0.0) { "A velocidade não pode ser negativa." }
    }

    fun toKilometersPerHour(): Double {
        return metersPerSecond * MS_TO_KMH_FACTOR
    }

    fun isSlow(): Boolean {
        return metersPerSecond < SLOW_SPEED_LIMIT_MS
    }

    fun isMoving(): Boolean {
        return metersPerSecond >= MOVING_SPEED_LIMIT_MS
    }

    companion object {
        private const val MS_TO_KMH_FACTOR = 3.6
        private const val SLOW_SPEED_LIMIT_MS = 0.5
        private const val MOVING_SPEED_LIMIT_MS = 1.0

        fun fromMetersPerSecond(speed: Double): Speed {
            return Speed(speed)
        }
    }
}
