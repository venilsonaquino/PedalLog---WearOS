package com.pedallog.app.modules.tracking.domain.valueobjects

/**
 * Estados possíveis do sinal GPS no domínio.
 */
enum class GpsSignal {
    DISABLED,
    ACQUIRING,
    WEAK,
    STRONG
}
