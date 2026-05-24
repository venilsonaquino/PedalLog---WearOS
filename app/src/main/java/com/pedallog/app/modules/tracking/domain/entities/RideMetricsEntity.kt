package com.pedallog.app.modules.tracking.domain.entities

import com.pedallog.app.modules.tracking.domain.valueobjects.Distance
import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation
import com.pedallog.app.modules.tracking.domain.valueobjects.ElevationState
import com.pedallog.app.modules.tracking.domain.valueobjects.RidePerformance

/**
 * Entidade de Domínio que consolida as métricas acumuladas de um pedal.
 * Respeita rigidamente a regra de no máximo 2 variáveis de instância.
 */
data class RideMetricsEntity(
    val performance: RidePerformance,
    val elevationState: ElevationState
) {

    fun update(segmentDistance: Distance, currentElevation: Elevation): RideMetricsEntity {
        val newPerformance = performance.addDistance(segmentDistance)
        val newElevationState = elevationState.update(currentElevation)
        
        return RideMetricsEntity(newPerformance, newElevationState)
    }

    fun incrementTime(ms: Long): RideMetricsEntity {
        return RideMetricsEntity(
            performance = performance.incrementDuration(ms),
            elevationState = elevationState
        )
    }

    companion object {
        fun createEmpty(): RideMetricsEntity {
            return RideMetricsEntity(
                performance = RidePerformance.createEmpty(),
                elevationState = ElevationState.createEmpty()
            )
        }
    }
}
