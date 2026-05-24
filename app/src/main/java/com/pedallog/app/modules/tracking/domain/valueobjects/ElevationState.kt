package com.pedallog.app.modules.tracking.domain.valueobjects

/**
 * Encapsula o estado e a lógica de ganho acumulado de elevação durante o pedal.
 * Mantém alta coesão e limita a quantidade de variáveis de instância.
 */
data class ElevationState(
    val accumulatedGain: Elevation,
    val lastRecorded: Elevation? = null
) {

    fun update(current: Elevation): ElevationState {
        val last = lastRecorded ?: return ElevationState(accumulatedGain, current)
        val difference = current.meters - last.meters
        
        return if (difference > 0.0) {
            val newGain = Elevation(accumulatedGain.meters + difference)
            ElevationState(newGain, current)
        } else {
            ElevationState(accumulatedGain, current)
        }
    }

    companion object {
        fun createEmpty(): ElevationState {
            return ElevationState(Elevation(0.0), null)
        }
    }
}
