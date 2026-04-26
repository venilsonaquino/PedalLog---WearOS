package com.pedallog.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pedallog.app.data.AppDatabase
import com.pedallog.app.data.PedalDao
import com.pedallog.app.data.PedalPoint
import com.pedallog.app.data.PedalSession
import com.pedallog.app.sync.WearSyncManager
import com.pedallog.app.util.FormatUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Serviço de primeiro plano responsável pelo rastreamento GPS do PedalLog.
 *
 * Implementa uma máquina de estados com três ações enviadas via Intent:
 *  - [ACTION_START]  → inicia nova sessão ou retoma uma pausada
 *  - [ACTION_PAUSE]  → pausa o GPS; serviço continua vivo com sessão ativa
 *  - [ACTION_FINISH] → finaliza a sessão (salva endTime) e pára o serviço
 *
 * Regra crítica: dentro de [onLocationResult], nenhum dado é acumulado
 * nem gravado no banco enquanto [_isPaused] for true.
 */
class TrackingService : Service() {

    companion object {
        private const val TAG = "TrackingService"
        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_ID = 1

        // ── Intent Actions ────────────────────────────────────────────────────
        const val ACTION_START  = "com.pedallog.app.ACTION_START"
        const val ACTION_PAUSE  = "com.pedallog.app.ACTION_PAUSE"
        const val ACTION_FINISH = "com.pedallog.app.ACTION_FINISH"

        // ── Parâmetros de localização ─────────────────────────────────────────
        private const val LOCATION_UPDATE_INTERVAL_ACTIVE  = 1000L
        private const val LOCATION_FASTEST_INTERVAL_ACTIVE = 500L
        private const val LOCATION_UPDATE_INTERVAL_PAUSED  = 5000L
        private const val LOCATION_FASTEST_INTERVAL_PAUSED = 2000L

        // ── Filtros anti-drift ────────────────────────────────────────────────
        /** TEMPORARIAMENTE 100 m para debug — reduzir para 20f em produção. */
        private const val MIN_ACCURACY_METERS = 20f
        private const val MIN_SPEED_MS = 0.5f

        // ── StateFlows expostos para a UI ─────────────────────────────────────
        private val _speedKmh = MutableStateFlow(0.0)
        val speedKmh: StateFlow<Double> = _speedKmh

        private val _distanceTraveled = MutableStateFlow(0.0)
        val distanceTraveled: StateFlow<Double> = _distanceTraveled

        private val _activeTimeSeconds = MutableStateFlow(0L)
        val activeTimeSeconds: StateFlow<Long> = _activeTimeSeconds

        /** true quando o GPS está ativo E a sessão NÃO está pausada. */
        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking

        /** true quando existe uma sessão sem endTime (ativa ou pausada). */
        private val _hasActiveSession = MutableStateFlow(false)
        val hasActiveSession: StateFlow<Boolean> = _hasActiveSession

        /** true quando a sessão existe mas está temporariamente pausada. */
        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused
    }

    // ── Instâncias ────────────────────────────────────────────────────────────
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var dao: PedalDao

    /**
     * Escopo de coroutines vinculado ao ciclo de vida do Service.
     * SupervisorJob: falha em um filho não cancela os demais.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Sessão corrente em memória — espelho do registro no banco. */
    private var currentSession: PedalSession? = null

    /** Último fix GPS válido para cálculo de distância por segmento. */
    private var lastLocation: Location? = null

    /** Acumulador de distância em metros (convertido para km ao publicar no StateFlow). */
    private var totalDistanceMeters: Float = 0f

    // ── Contadores Auto-Pause / Auto-Resume ───────────────────────────────────
    private var lowSpeedSeconds = 0
    private var highSpeedSeconds = 0

    private var timerJob: kotlinx.coroutines.Job? = null

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        dao = AppDatabase.getInstance(applicationContext).pedalDao()
        buildLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        when (intent?.action) {
            ACTION_START  -> handleStart()
            ACTION_PAUSE  -> handlePause()
            ACTION_FINISH -> handleFinish()
            null          -> handleSystemRestart()   // START_STICKY reiniciou o serviço
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        timerJob?.cancel()
        serviceScope.cancel()
        // Reseta estado público para a UI refletir ausência de serviço
        _isTracking.value = false
        _speedKmh.value = 0.0
        _activeTimeSeconds.value = 0L
    }

    // ── Máquina de Estados ────────────────────────────────────────────────────

    /**
     * ACTION_START — cria nova sessão ou retoma uma pausada.
     */
    private fun handleStart() {
        serviceScope.launch {
            val active = withContext(Dispatchers.IO) { dao.getActiveSession() }

            if (active == null) {
                // ── Nova sessão ──────────────────────────────────────────────
                val newSession = PedalSession()
                val insertedId = withContext(Dispatchers.IO) { dao.insertSession(newSession) }
                currentSession = newSession.copy(id = insertedId)
                totalDistanceMeters = 0f
                _distanceTraveled.value = 0.0
                _activeTimeSeconds.value = 0L
                Log.d("PedalDebug", "Nova PedalSession criada: id=$insertedId")
            } else {
                // ── Retoma sessão pausada ────────────────────────────────────
                val resumed = active.copy(isPaused = false)
                currentSession = resumed
                totalDistanceMeters = FormatUtils.metersToKm(active.totalDistance) * 1000f // Reverte pra metros internamente
                totalDistanceMeters = active.totalDistance * 1000f
                _distanceTraveled.value = active.totalDistance.toDouble()
                withContext(Dispatchers.IO) { dao.updateSession(resumed) }
                Log.d("PedalDebug", "Sessão id=${active.id} retomada")
            }

            lastLocation = null   // reinicia cálculo de segmento para evitar salto
            lowSpeedSeconds = 0
            highSpeedSeconds = 0
            _isPaused.value = false
            _hasActiveSession.value = true
            _isTracking.value = true
            
            // Inicia o GPS com intervalo rápido de 1s
            restartLocationUpdates(isPaused = false)
            startActiveTimer()
        }
    }

    /**
     * ACTION_PAUSE — pausa a sessão. O GPS continua em modo de economia para permitir Auto-Resume.
     */
    private fun handlePause() {
        // Em vez de parar totalmente, diminui a frequência para economia de bateria
        restartLocationUpdates(isPaused = true)
        
        lowSpeedSeconds = 0
        highSpeedSeconds = 0
        _isTracking.value = false
        _speedKmh.value = 0.0
        _isPaused.value = true

        currentSession?.let { session ->
            val paused = session.copy(
                isPaused = true,
                totalDistance = totalDistanceMeters / 1000f,
                activeDurationMs = _activeTimeSeconds.value * 1000L
            )
            currentSession = paused
            serviceScope.launch(Dispatchers.IO) {
                dao.updateSession(paused)
                Log.d("PedalDebug", "Sessão id=${paused.id} pausada. Dist=${paused.totalDistance} km")
            }
        }
    }

    /**
     * ACTION_FINISH — finaliza a sessão com timestamp, sincroniza com o celular e para o serviço.
     *
     * Fluxo:
     *  1. Para o GPS imediatamente.
     *  2. Salva [endTime] e [totalDistance] no banco (Dispatchers.IO).
     *  3. Carrega todos os pontos da sessão (Dispatchers.IO).
     *  4. Publica os dados via Wearable Data Layer ([WearSyncManager]).
     *  5. Reseta o estado e para o serviço.
     *
     * Erros na sincronização são capturados e logados, mas não impedem o
     * término normal da sessão e o encerramento do serviço.
     */
    private fun handleFinish() {
        stopLocationUpdates()
        _isTracking.value = false
        _speedKmh.value = 0.0

        serviceScope.launch {
            currentSession?.let { session ->
                // ── 1. Persiste o fim da sessão ──────────────────────────────
                val finished = session.copy(
                    endTime = Date(),
                    isPaused = false,
                    totalDistance = totalDistanceMeters / 1000f,
                    activeDurationMs = _activeTimeSeconds.value * 1000L
                )
                withContext(Dispatchers.IO) { dao.updateSession(finished) }
                Log.d("PedalDebug", "Sessão id=${finished.id} finalizada. Dist=${finished.totalDistance} km")

                // ── 2. Sincroniza com o celular via Data Layer ─────────────
                try {
                    val points = withContext(Dispatchers.IO) {
                        dao.getPointsForSession(finished.id)
                    }
                    withContext(Dispatchers.IO) {
                        WearSyncManager.syncSession(
                            applicationContext, 
                            finished, 
                            points,
                            _activeTimeSeconds.value * 1000L
                        )
                        // Marca como sincronizado após o sucesso
                        dao.updateSession(finished.copy(isSynced = true))
                    }
                    Log.d(TAG, "Sincronização concluída: ${points.size} pontos enviados e marcados como isSynced")
                } catch (e: Exception) {
                    // Falha na sincronização não bloqueia o encerramento da sessão
                    Log.e(TAG, "Erro na sincronização Wearable: ${e.message}")
                }
            }

            // ── 3. Reseta o estado e para o serviço ──────────────────────
            _isPaused.value = false
            _hasActiveSession.value = false
            _distanceTraveled.value = 0.0
            _activeTimeSeconds.value = 0L
            currentSession = null
            totalDistanceMeters = 0f
            lastLocation = null
            stopSelf()
        }
    }

    /**
     * Chamado quando o sistema reinicia o serviço via START_STICKY (intent = null).
     * Restaura o estado da sessão ativa encontrada no banco.
     */
    private fun handleSystemRestart() {
        serviceScope.launch {
            val active = withContext(Dispatchers.IO) { dao.getActiveSession() }
            if (active == null) {
                Log.d(TAG, "Reinício sem sessão ativa — encerrando serviço")
                stopSelf()
                return@launch
            }
            currentSession = active
            totalDistanceMeters = active.totalDistance * 1000f
            _distanceTraveled.value = active.totalDistance.toDouble()
            _hasActiveSession.value = true

            if (!active.isPaused) {
                _isPaused.value = false
                _isTracking.value = true
                restartLocationUpdates(isPaused = false)
                startActiveTimer()
                Log.d("PedalDebug", "Sessão id=${active.id} restaurada e retomada automaticamente")
            } else {
                _isPaused.value = true
                _isTracking.value = false
                restartLocationUpdates(isPaused = true)
                startActiveTimer()
                Log.d("PedalDebug", "Sessão id=${active.id} restaurada em estado pausado")
            }
        }
    }

    private fun startActiveTimer() {
        if (timerJob?.isActive == true) return
        timerJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(1000)
                if (!_isPaused.value && _isTracking.value) {
                    _activeTimeSeconds.value += 1
                }
            }
        }
    }

    // ── GPS ───────────────────────────────────────────────────────────────────

    private fun buildLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {

                val location = result.lastLocation ?: return

                // ── Filtro 1: Precisão ────────────────────────────────────────
                if (!location.hasAccuracy() || location.accuracy > MIN_ACCURACY_METERS) {
                    return
                }

                val rawSpeedMs = if (location.hasSpeed()) location.speed else 0f
                val speedMs = if (rawSpeedMs >= MIN_SPEED_MS) rawSpeedMs else 0f

                // ── Lógica de Auto-Resume (se pausado) ────────────────────────
                if (_isPaused.value) {
                    // Como o intervalo está em 5s, um único fix rápido > 1.0 m/s 
                    // já representa movimento sustentado.
                    if (rawSpeedMs > 1.0f) {
                        highSpeedSeconds += 5 // Soma 5s logo de cara
                        if (highSpeedSeconds >= 2) {
                            Log.d(TAG, "Auto-Resume engatado! (Velocidade: $rawSpeedMs m/s)")
                            triggerResumeVibration()
                            handleStart()
                        }
                    } else {
                        highSpeedSeconds = 0
                    }
                    return // Não acumula distância em modo pause
                }

                // ── Lógica de Auto-Pause (se ativo) ───────────────────────────
                _speedKmh.value = FormatUtils.msToKmh(speedMs).toDouble()

                if (rawSpeedMs < MIN_SPEED_MS) {
                    lowSpeedSeconds++ // GPS em modo ativo atualiza a cada 1s
                    if (lowSpeedSeconds >= 5) {
                        Log.d(TAG, "Auto-Pause engatado! (Abaixo de 0.5 m/s por 5s)")
                        triggerPauseVibration()
                        handlePause()
                        return
                    }
                } else {
                    lowSpeedSeconds = 0
                }

                // ── Filtro 3: Acúmulo de distância + persistência ─────────────
                if (speedMs > 0f) {
                    lastLocation?.let { prev ->
                        val segmentMeters = prev.distanceTo(location)
                        // Rejeita segmentos implausíveis (>100 m/s ≈ 360 km/h)
                        if (segmentMeters > 0f && segmentMeters < 100f) {
                            totalDistanceMeters += segmentMeters
                            _distanceTraveled.value = FormatUtils.metersToKm(totalDistanceMeters).toDouble()

                            val sessionId = currentSession?.id ?: return@let
                            val point = PedalPoint(
                                sessionId = sessionId,
                                latitude  = location.latitude,
                                longitude = location.longitude,
                                speed     = _speedKmh.value,
                                distance  = _distanceTraveled.value,
                                timestamp = location.time
                            )
                            
                            // Se este for o primeiro ponto de uma nova sessão, 
                            // atualizamos o startTime para o horário real do GPS
                            if (totalDistanceMeters <= segmentMeters) {
                                val updatedSession = currentSession?.copy(startTime = Date(location.time))
                                if (updatedSession != null) {
                                    currentSession = updatedSession
                                    serviceScope.launch(Dispatchers.IO) {
                                        dao.updateSession(updatedSession)
                                    }
                                }
                            }

                            serviceScope.launch(Dispatchers.IO) {
                                dao.insertPoint(point)
                            }
                        }
                    }
                }
                lastLocation = location
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun restartLocationUpdates(isPaused: Boolean) {
        stopLocationUpdates()
        
        val interval = if (isPaused) LOCATION_UPDATE_INTERVAL_PAUSED else LOCATION_UPDATE_INTERVAL_ACTIVE
        val fastest  = if (isPaused) LOCATION_FASTEST_INTERVAL_PAUSED else LOCATION_FASTEST_INTERVAL_ACTIVE
        
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(fastest)
            .build()
            
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        restartLocationUpdates(isPaused = _isPaused.value)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // ── Vibrador (Feedback Tátil) ─────────────────────────────────────────────

    private fun triggerPauseVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
            // Dois toques curtos (150ms vibra, 100ms pausa, 150ms vibra)
            val timings = longArrayOf(0, 150, 100, 150)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun triggerResumeVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
            // Um toque longo (400ms)
            vibrator.vibrate(VibrationEffect.createOneShot(400, 255))
        }
    }

    // ── Notificação Foreground ────────────────────────────────────────────────

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Rastreamento de Pedalada",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Notificação do rastreamento GPS ativo" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PedalLog")
            .setContentText("Rastreando sua pedalada...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
