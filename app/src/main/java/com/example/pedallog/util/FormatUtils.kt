package com.example.pedallog.util

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

        return if (hours > 0) {
            if (isAmbient) {
                String.format("%d:%02d", hours, minutes)
            } else {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            }
        } else {
            if (isAmbient) {
                String.format("%02d", minutes) // Ou HH:mm -> 00:mm se preferir manter formato padrão. A regra diz: "exiba apenas HH:mm".
                // Para manter consistência com o pedido "apenas HH:mm", usaremos sempre horas e minutos se ambient
                String.format("%02d:%02d", hours, minutes)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}
