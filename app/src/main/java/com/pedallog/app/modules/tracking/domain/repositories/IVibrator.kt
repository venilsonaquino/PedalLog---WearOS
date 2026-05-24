package com.pedallog.app.modules.tracking.domain.repositories

/**
 * Contrato abstrato de domínio para o feedback tátil de vibração do hardware do dispositivo.
 */
interface IVibrator {

    fun vibratePause()

    fun vibrateResume()
}
