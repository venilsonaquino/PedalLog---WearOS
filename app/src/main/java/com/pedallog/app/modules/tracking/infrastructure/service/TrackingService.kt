package com.pedallog.app.modules.tracking.infrastructure.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pedallog.app.modules.tracking.application.TrackingOrchestrator
import com.pedallog.app.modules.tracking.application.usecases.FinishRideUseCase
import com.pedallog.app.modules.tracking.domain.entities.RideSession
import com.pedallog.app.modules.tracking.domain.repositories.IGpsProvider
import com.pedallog.app.modules.tracking.domain.repositories.ISessionRepository
import com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal
import com.pedallog.app.modules.tracking.domain.valueobjects.LocationUpdate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service de primeiro plano do Android (Foreground Service) do PedalLog.
 * Atua de forma limpa apenas como um contêiner de ciclo de vida do sistema,
 * delegando toda a lógica reativa e operacional ao [TrackingOrchestrator].
 */
@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var orchestrator: TrackingOrchestrator
    @Inject lateinit var sessionRepository: ISessionRepository
    @Inject lateinit var gpsProvider: IGpsProvider
    @Inject lateinit var finishRideUseCase: FinishRideUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var syncJob: Job? = null

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START  = "com.pedallog.app.ACTION_START"
        const val ACTION_PAUSE  = "com.pedallog.app.ACTION_PAUSE"
        const val ACTION_FINISH = "com.pedallog.app.ACTION_FINISH"

        private val _speedKmh = MutableStateFlow(0.0)
        val speedKmh: StateFlow<Double> = _speedKmh

        private val _distanceTraveled = MutableStateFlow(0.0)
        val distanceTraveled: StateFlow<Double> = _distanceTraveled

        private val _activeTimeSeconds = MutableStateFlow(0L)
        val activeTimeSeconds: StateFlow<Long> = _activeTimeSeconds

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking

        private val _hasActiveSession = MutableStateFlow(false)
        val hasActiveSession: StateFlow<Boolean> = _hasActiveSession

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused

        private val _gpsSignal = MutableStateFlow(GpsSignal.ACQUIRING)
        val gpsSignal: StateFlow<GpsSignal> = _gpsSignal

        private val _elevationGainMeters = MutableStateFlow(0.0)
        val elevationGainMeters: StateFlow<Double> = _elevationGainMeters
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        
        syncJob = serviceScope.launch {
            launch {
                gpsProvider.observeGpsSignal().collectLatest { signal ->
                    _gpsSignal.value = signal
                }
            }
            launch {
                gpsProvider.observeLocationUpdates(isPaused.value).collectLatest { update ->
                    _speedKmh.value = update.speed.toKilometersPerHour()
                }
            }
            launch {
                while (isActive) {
                    delay(1000L)
                    val active = sessionRepository.getActiveSession() ?: continue
                    _distanceTraveled.value = active.state.metrics.performance.distance.toKilometers()
                    _elevationGainMeters.value = active.state.metrics.elevationState.accumulatedGain.meters
                    _activeTimeSeconds.value = active.state.metrics.performance.durationMs / 1000L
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_FINISH -> handleFinish()
        }
        return START_STICKY
    }

    private fun handleStart() {
        serviceScope.launch {
            val active = sessionRepository.getActiveSession()
            if (active == null) {
                val newSession = RideSession.createNew()
                sessionRepository.save(newSession)
            } else {
                val resumed = active.resume()
                sessionRepository.save(resumed)
            }
            _isPaused.value = false
            _hasActiveSession.value = true
            _isTracking.value = true
            orchestrator.startTracking(isPaused = false)
        }
    }

    private fun handlePause() {
        serviceScope.launch {
            val active = sessionRepository.getActiveSession() ?: return@launch
            val paused = active.pause()
            sessionRepository.save(paused)
            _isPaused.value = true
            _isTracking.value = false
            _speedKmh.value = 0.0
            orchestrator.startTracking(isPaused = true)
        }
    }

    private fun handleFinish() {
        serviceScope.launch {
            orchestrator.stopTracking()
            val active = sessionRepository.getActiveSession()
            if (active != null) {
                finishRideUseCase.execute(active.identity.id)
            }
            _isPaused.value = false
            _hasActiveSession.value = false
            _isTracking.value = false
            _distanceTraveled.value = 0.0
            _activeTimeSeconds.value = 0L
            _elevationGainMeters.value = 0.0
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        orchestrator.stopTracking()
        syncJob?.cancel()
        serviceScope.cancel()
        _isTracking.value = false
        _speedKmh.value = 0.0
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, "Rastreamento de Pedalada", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PedalLog").setContentText("Rastreando sua pedalada...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
