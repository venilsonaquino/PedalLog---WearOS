package com.pedallog.app.modules.tracking.application

import com.pedallog.app.modules.tracking.application.usecases.AutoPauseDetectorUseCase
import com.pedallog.app.modules.tracking.application.usecases.FinishRideUseCase
import com.pedallog.app.modules.tracking.application.usecases.ProcessLocationUseCase
import com.pedallog.app.modules.tracking.domain.entities.RideSessionEntity
import com.pedallog.app.modules.tracking.domain.repositories.IGpsProvider
import com.pedallog.app.modules.tracking.domain.repositories.ISessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestrador reativo da sessão de ciclismo.
 * Coleta atualizações do GPS e do cronômetro em background e invoca os Casos de Uso.
 */
@Singleton
class TrackingOrchestrator @Inject constructor(
    private val gpsProvider: IGpsProvider,
    private val sessionRepository: ISessionRepository,
    private val processLocation: ProcessLocationUseCase,
    private val autoPauseDetector: AutoPauseDetectorUseCase,
    private val finishRide: FinishRideUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var trackingJob: Job? = null
    private var timerJob: Job? = null

    fun startTracking(isPaused: Boolean) {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            gpsProvider.observeLocationUpdates(isPaused).collectLatest { update ->
                processLocation.execute(update)
                val speed = update.speed
                if (!isPaused) {
                    autoPauseDetector.checkAutoPause(speed)
                } else {
                    autoPauseDetector.checkAutoResume(speed, isManuallyPaused = false)
                }
            }
        }
        startTimer(isPaused)
    }

    fun stopTracking() {
        trackingJob?.cancel()
        timerJob?.cancel()
        autoPauseDetector.resetCounters()
    }

    private fun startTimer(isPaused: Boolean) {
        timerJob?.cancel()
        if (isPaused) return
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val active = sessionRepository.getActiveSession() ?: continue
                val ticked = active.tickTime(1000L)
                sessionRepository.save(ticked)
            }
        }
    }
}
