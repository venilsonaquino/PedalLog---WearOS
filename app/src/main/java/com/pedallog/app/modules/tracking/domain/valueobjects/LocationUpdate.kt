package com.pedallog.app.modules.tracking.domain.valueobjects

/**
 * Representa uma atualização de geolocalização consolidada vinda do GPS.
 * Encapsula de forma limpa todas as métricas em uma única classe plana de domínio,
 * evitando cascatas de classes complexas e respeitando a Lei de Demeter.
 */
data class LocationUpdate(
    val coordinate: Coordinate,
    val speed: Speed,
    val segmentDistance: Distance,
    val elevation: Elevation,
    val timestamp: Long
)
