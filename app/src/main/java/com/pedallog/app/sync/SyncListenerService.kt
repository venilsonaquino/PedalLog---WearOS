package com.pedallog.app.sync

import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.pedallog.app.data.AppDatabase
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Escuta requisições vindas do app mobile, como o pedido de sincronização manual ('Pull').
 */
class SyncListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        
        Log.d(TAG, "Mensagem recebida: ${messageEvent.path}")

        if (messageEvent.path == "/request_sync") {
            Log.d(TAG, "Pedido de sincronização recebido do mobile. Iniciando processo...")
            
            // Fornecer feedback visual/tátil de que o relógio recebeu o pedido
            vibrateFeedback()

            serviceScope.launch {
                processSyncRequest()
            }
        }
    }

    private suspend fun processSyncRequest() {
        try {
            val database = AppDatabase.getInstance(applicationContext)
            val pedalDao = database.pedalDao()

            val unsyncedSessions = pedalDao.getUnsyncedSessions()
            
            if (unsyncedSessions.isEmpty()) {
                Log.d(TAG, "Não há sessões pendentes de sincronização.")
                return
            }

            Log.d(TAG, "Encontradas ${unsyncedSessions.size} sessões pendentes para sincronizar.")

            for (session in unsyncedSessions) {
                // Recuperar pontos da sessão
                val points = pedalDao.getPointsForSession(session.id)
                
                // Enviar via Data Layer (comprime + envia)
                WearSyncManager.syncSession(applicationContext, session, points)
                
                // Atualizar flag isSynced no banco local
                val updatedSession = session.copy(isSynced = true)
                pedalDao.updateSession(updatedSession)
                
                Log.d(TAG, "Sessão ${session.id} sincronizada e marcada como isSynced = true.")
            }
            
            Log.d(TAG, "Processo de sincronização finalizado com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar sincronização: ${e.message}", e)
        }
    }

    private fun vibrateFeedback() {
        val vibrator = getSystemService(Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator()) {
            val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        }
    }

    companion object {
        private const val TAG = "SyncListenerService"
    }
}
