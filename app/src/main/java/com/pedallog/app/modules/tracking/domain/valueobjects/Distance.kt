package com.pedallog.app.modules.tracking.domain.valueobjects

/**
 * Representa a distância física de uma atividade de ciclismo.
 * Encapsula o valor primitivo de distância para evitar anomalias de tipos e
 * centralizar as regras de conversão.
 */
data class Distance(val meters: Double) {

    init {
        require(meters >= 0.0) { "A distância não pode ser negativa." }
    }

    fun toKilometers(): Double {
        return meters / METERS_IN_A_KILOMETER
    }

    fun plus(other: Distance): Distance {
        return Distance(this.meters + other.meters)
    }

    companion object {
        private const val METERS_IN_A_KILOMETER = 1000.0

        fun fromMeters(meters: Double): Distance {
            return Distance(meters)
        }

        fun fromKilometers(kilometers: Double): Distance {
            return Distance(kilometers * METERS_IN_A_KILOMETER)
        }
    }
}
