package com.pedallog.app.modules.tracking.application.usecases

import com.pedallog.app.modules.tracking.domain.repositories.ISessionRepository
import com.pedallog.app.modules.tracking.domain.repositories.IVibrator
import com.pedallog.app.modules.tracking.domain.valueobjects.Speed
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caso de Uso encarregado de monitorar velocidades e disparar
 * as transições automáticas de Pausa e Retomada do exercício físico.
 */
@Singleton
class AutoPauseDetectorUseCase @Inject constructor(
    private val sessionRepository: ISessionRepository,
    private val vibrator: IVibrator
) {

    private var slowSpeedSeconds = 0
    private var highSpeedSeconds = 0

    suspend fun checkAutoPause(speed: Speed) {
        if (!speed.isSlow()) {
            slowSpeedSeconds = 0
            return
        }

        slowSpeedSeconds++
        if (slowSpeedSeconds >= PAUSE_DELAY_LIMIT_UPDATES) {
            triggerAutoPause()
        }
    }

    suspend fun checkAutoResume(speed: Speed, isManuallyPaused: Boolean) {
        if (isManuallyPaused) {
            return
        }

        if (!speed.isMoving()) {
            highSpeedSeconds = 0
            return
        }

        highSpeedSeconds += RESUME_SEGMENT_INCREMENT
        if (highSpeedSeconds >= RESUME_DELAY_LIMIT_UPDATES) {
            triggerAutoResume()
        }
    }

    fun resetCounters() {
        slowSpeedSeconds = 0
        highSpeedSeconds = 0
    }

    private suspend fun triggerAutoPause() {
        val activeSession = sessionRepository.getActiveSession() ?: return
        if (activeSession.state.isPaused) {
            return
        }
        val pausedSession = activeSession.pause()
        sessionRepository.save(pausedSession)
        vibrator.vibratePause()
        slowSpeedSeconds = 0
    }

    private suspend fun triggerAutoResume() {
        val activeSession = sessionRepository.getActiveSession() ?: return
        if (!activeSession.state.isPaused) {
            return
        }
        val resumedSession = activeSession.resume()
        sessionRepository.save(resumedSession)
        vibrator.vibrateResume()
        highSpeedSeconds = 0
    }

    companion object {
        private const val PAUSE_DELAY_LIMIT_UPDATES = 5
        
        // No original: incrementa 5 e engaja no primeiro trigger maior ou igual a 2
        private const val RESUME_SEGMENT_INCREMENT = 5
        private const val RESUME_DELAY_LIMIT_UPDATES = 2
    }
}
