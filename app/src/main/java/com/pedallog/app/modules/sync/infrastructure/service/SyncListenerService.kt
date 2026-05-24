package com.pedallog.app.modules.sync.infrastructure.service

import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.pedallog.app.modules.sync.infrastructure.wearable.WearSyncManager
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.AppDatabase
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
            Log.d(TAG, "Pedido de sincronização recebido do mobile. Iniciando processo (Sincronizando todas as sessões finalizadas)...")

            // Fornecer feedback visual/tátil de que o relógio recebeu o pedido
            vibrateFeedback()

            serviceScope.launch {
                processSyncRequest(forceAll = true)
            }
        }
    }

    private suspend fun processSyncRequest(forceAll: Boolean = false) {
        try {
            val database = AppDatabase.getInstance(applicationContext)
            val pedalDao = database.pedalDao()

            val sessionsToSync = if (forceAll) {
                pedalDao.getAllCompletedSessions()
            } else {
                pedalDao.getUnsyncedSessions()
            }

            if (sessionsToSync.isEmpty()) {
                Log.d(TAG, "Não há sessões pendentes de sincronização.")
                return
            }

            Log.d(TAG, "Encontradas ${sessionsToSync.size} sessões para sincronizar (forceAll=$forceAll).")

            for (session in sessionsToSync) {
                Log.d(TAG, "Processando sessão ID=${session.id}, UUID=${session.syncUuid}")
                val points = pedalDao.getPointsForSession(session.id)

                Log.d(TAG, "Sessão ${session.id}: ${points.size} pontos recuperados do banco.")

                WearSyncManager.syncSession(applicationContext, session, points, session.activeDurationMs)

                val updatedSession = session.copy(isSynced = true)
                pedalDao.updateSession(updatedSession)

                Log.d(TAG, "Sessão ${session.id} marcada como isSynced = true no banco local.")
            }

            Log.d(TAG, "Processo de sincronização finalizado com sucesso.")
        } catch (exception: Exception) {
            Log.e(TAG, "Erro ao processar sincronização: ${exception.message}", exception)
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
