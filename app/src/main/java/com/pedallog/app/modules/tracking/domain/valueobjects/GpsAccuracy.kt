package com.pedallog.app.modules.tracking.domain.valueobjects

/**
 * Encapsula o valor primitivo de precisão do GPS (metros).
 * Fornece métodos ricos baseados no princípio Tell Don't Ask.
 */
data class GpsAccuracy(
    val meters: Float
) {
    /**
     * Define o limite para um sinal considerado forte (20 metros).
     */
    fun isStrong(): Boolean = meters <= 20.0f
}
