package com.pedallog.app.modules.tracking.application.usecases

import android.content.Context
import android.util.Log
import com.pedallog.app.modules.tracking.domain.repositories.ISessionRepository
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.mappers.SessionMapper
import com.pedallog.app.modules.sync.infrastructure.wearable.WearSyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Caso de Uso encarregado de finalizar um exercício.
 * Consolida a persistência de encerramento do pedal, recupera os pontos
 * de trajeto e despacha a sincronização Bluetooth com o smartphone.
 */
class FinishRideUseCase @Inject constructor(
    private val sessionRepository: ISessionRepository,
    @ApplicationContext private val context: Context
) {

    suspend fun execute(sessionId: Long) {
        val session = sessionRepository.getSessionById(sessionId) ?: return
        val finishedSession = session.finish()
        
        sessionRepository.save(finishedSession)
        
        try {
            val points = sessionRepository.getPointsForSession(sessionId)
            
            // Invoca a sincronização legada
            WearSyncManager.syncSession(
                context, 
                SessionMapper.toModel(finishedSession), 
                points, 
                finishedSession.state.metrics.performance.durationMs
            )
            
            // Registra sucesso e marca como sincronizada
            sessionRepository.markAsSynced(sessionId)
            Log.d("FinishRide", "Sessão sincronizada com o smartphone.")
        } catch (exception: Exception) {
            // Em caso de erro Bluetooth/Wearable, o encerramento continua ativo
            Log.e("FinishRide", "Erro ao sincronizar sessão Wearable: ${exception.message}")
        }
    }
}
