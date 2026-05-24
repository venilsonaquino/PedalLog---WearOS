package com.pedallog.app.modules.tracking.domain.valueobjects

/**
 * Representa uma coordenada geográfica válida.
 * Encapsula os primitivos de latitude e longitude sem abreviações.
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double
)
