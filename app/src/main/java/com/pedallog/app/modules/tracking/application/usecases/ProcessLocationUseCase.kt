package com.pedallog.app.modules.tracking.application.usecases

import com.pedallog.app.modules.tracking.domain.repositories.ISessionRepository
import com.pedallog.app.modules.tracking.domain.valueobjects.LocationUpdate
import javax.inject.Inject

/**
 * Caso de Uso encarregado de processar uma nova localização do GPS.
 * Atualiza o agregado de sessão e insere o ponto do trajeto de forma atômica e desacoplada.
 */
class ProcessLocationUseCase @Inject constructor(
    private val sessionRepository: ISessionRepository
) {

    suspend fun execute(locationUpdate: LocationUpdate) {
        val activeSession = sessionRepository.getActiveSession() ?: return
        
        val updatedSession = activeSession.updateMetrics(
            segmentDistance = locationUpdate.segmentDistance,
            currentElevation = locationUpdate.elevation
        )
        
        val sessionId = sessionRepository.save(updatedSession)
        
        sessionRepository.addLocationPoint(
            sessionId = sessionId,
            coordinate = locationUpdate.coordinate,
            speed = locationUpdate.speed,
            distance = updatedSession.state.metrics.performance.distance,
            elevation = locationUpdate.elevation,
            timestamp = locationUpdate.timestamp
        )
    }
}
