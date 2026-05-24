package com.pedallog.app.modules.tracking.domain.valueobjects

/**
 * Consolida as métricas físicas de performance do pedal: distância e duração ativa.
 * Respeita estritamente o limite de 2 atributos do Object Calisthenics.
 */
data class RidePerformance(
    val distance: Distance,
    val durationMs: Long
) {

    fun addDistance(segment: Distance): RidePerformance {
        return RidePerformance(
            distance = this.distance.plus(segment),
            durationMs = this.durationMs
        )
    }

    fun incrementDuration(ms: Long): RidePerformance {
        return RidePerformance(
            distance = this.distance,
            durationMs = this.durationMs + ms
        )
    }

    companion object {
        fun createEmpty(): RidePerformance {
            return RidePerformance(
                distance = Distance(0.0),
                durationMs = 0L
            )
        }
    }
}
