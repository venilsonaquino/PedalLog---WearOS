package com.pedallog.app.shared.util

object FormatUtils {

    /**
     * Converte metros para quilômetros.
     */
    fun metersToKm(meters: Float): Float {
        return meters / 1000f
    }

    /**
     * Converte metros por segundo para quilômetros por hora.
     */
    fun msToKmh(ms: Float): Float {
        return ms * 3.6f
    }

    /**
     * Formata o tempo ativo da sessão.
     * @param activeSeconds Segundos totais da sessão (excluindo pausas).
     * @param isAmbient Se true, oculta os segundos para poupar a tela (ex: HH:mm ou mm).
     */
    fun formatActiveTime(activeSeconds: Long, isAmbient: Boolean): String {
        val hours = activeSeconds / 3600
        val minutes = (activeSeconds % 3600) / 60
        val seconds = activeSeconds % 60

        return if (isAmbient) {
            // Em modo Ambient, mostramos HH:mm para economizar bateria (sem segundos piscando)
            if (hours > 0) {
                String.format("%02d:%02d", hours, minutes)
            } else {
                String.format("00:%02d", minutes)
            }
        } else {
            // Modo ativo: mostra HH:mm:ss ou mm:ss
            if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}
