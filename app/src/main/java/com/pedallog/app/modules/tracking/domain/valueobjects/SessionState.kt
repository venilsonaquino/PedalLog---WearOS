package com.pedallog.app.modules.tracking.domain.valueobjects

import com.pedallog.app.modules.tracking.domain.entities.RideMetricsEntity

/**
 * Representa o estado dinâmico (status e métricas) de uma sessão de pedal.
 * Respeita a regra de no máximo 2 variáveis de instância.
 */
data class SessionState(
    val isPaused: Boolean,
    val metrics: RideMetricsEntity
) {

    fun pause(): SessionState {
        return SessionState(isPaused = true, metrics = metrics)
    }

    fun resume(): SessionState {
        return SessionState(isPaused = false, metrics = metrics)
    }

    fun updateMetrics(segmentDistance: Distance, currentElevation: Elevation): SessionState {
        if (isPaused) {
            return this
        }
        val newMetrics = metrics.update(segmentDistance, currentElevation)
        return SessionState(isPaused = false, metrics = newMetrics)
    }

    fun tickTime(ms: Long): SessionState {
        if (isPaused) {
            return this
        }
        val newMetrics = metrics.incrementTime(ms)
        return SessionState(isPaused = false, metrics = newMetrics)
    }

    companion object {
        fun createActive(): SessionState {
            return SessionState(
                isPaused = false,
                metrics = RideMetricsEntity.createEmpty()
            )
        }
    }
}
