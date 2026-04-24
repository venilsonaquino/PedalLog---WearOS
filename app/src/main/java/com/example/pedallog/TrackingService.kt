package com.example.pedallog

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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pedallog.data.AppDatabase
import com.example.pedallog.data.PedalDao
import com.example.pedallog.data.PedalPoint
import com.example.pedallog.data.PedalSession
import com.example.pedallog.sync.WearSyncManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        const val ACTION_START  = "com.example.pedallog.ACTION_START"
        const val ACTION_PAUSE  = "com.example.pedallog.ACTION_PAUSE"
        const val ACTION_FINISH = "com.example.pedallog.ACTION_FINISH"

        // ── Parâmetros de localização ─────────────────────────────────────────
        private const val LOCATION_UPDATE_INTERVAL  = 1000L
        private const val LOCATION_FASTEST_INTERVAL = 500L

        // ── Filtros anti-drift ────────────────────────────────────────────────
        /** TEMPORARIAMENTE 100 m para debug — reduzir para 20f em produção. */
        private const val MIN_ACCURACY_METERS = 100f
        private const val MIN_SPEED_MS = 0.5f

        // ── StateFlows expostos para a UI ─────────────────────────────────────
        private val _speedKmh = MutableStateFlow(0.0)
        val speedKmh: StateFlow<Double> = _speedKmh

        private val _distanceTraveled = MutableStateFlow(0.0)
        val distanceTraveled: StateFlow<Double> = _distanceTraveled

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
        serviceScope.cancel()
        // Reseta estado público para a UI refletir ausência de serviço
        _isTracking.value = false
        _speedKmh.value = 0.0
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
                Log.d("PedalDebug", "Nova PedalSession criada: id=$insertedId")
            } else {
                // ── Retoma sessão pausada ────────────────────────────────────
                val resumed = active.copy(isPaused = false)
                currentSession = resumed
                totalDistanceMeters = active.totalDistance * 1000f
                _distanceTraveled.value = active.totalDistance.toDouble()
                withContext(Dispatchers.IO) { dao.updateSession(resumed) }
                Log.d("PedalDebug", "Sessão id=${active.id} retomada")
            }

            lastLocation = null   // reinicia cálculo de segmento para evitar salto
            _isPaused.value = false
            _hasActiveSession.value = true
            _isTracking.value = true
            startLocationUpdates()
        }
    }

    /**
     * ACTION_PAUSE — pausa a sessão. GPS para, serviço permanece vivo.
     */
    private fun handlePause() {
        stopLocationUpdates()
        _isTracking.value = false
        _speedKmh.value = 0.0
        _isPaused.value = true

        currentSession?.let { session ->
            val paused = session.copy(
                isPaused = true,
                totalDistance = totalDistanceMeters / 1000f
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
                    totalDistance = totalDistanceMeters / 1000f
                )
                withContext(Dispatchers.IO) { dao.updateSession(finished) }
                Log.d("PedalDebug", "Sessão id=${finished.id} finalizada. Dist=${finished.totalDistance} km")

                // ── 2. Sincroniza com o celular via Data Layer ─────────────
                try {
                    val points = withContext(Dispatchers.IO) {
                        dao.getPointsForSession(finished.id)
                    }
                    withContext(Dispatchers.IO) {
                        WearSyncManager.syncSession(applicationContext, finished, points)
                    }
                    Log.d(TAG, "Sincronização concluída: ${points.size} pontos enviados")
                } catch (e: Exception) {
                    // Falha na sincronização não bloqueia o encerramento da sessão
                    Log.e(TAG, "Erro na sincronização Wearable: ${e.message}")
                }
            }

            // ── 3. Reseta o estado e para o serviço ──────────────────────
            _isPaused.value = false
            _hasActiveSession.value = false
            _distanceTraveled.value = 0.0
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
                startLocationUpdates()
                Log.d("PedalDebug", "Sessão id=${active.id} restaurada e retomada automaticamente")
            } else {
                _isPaused.value = true
                _isTracking.value = false
                Log.d("PedalDebug", "Sessão id=${active.id} restaurada em estado pausado")
            }
        }
    }

    // ── GPS ───────────────────────────────────────────────────────────────────

    private fun buildLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {

                // ── Regra crítica: não processa dados com sessão pausada ──────
                if (_isPaused.value) return

                val location = result.lastLocation ?: run {
                    Log.d("PedalDebug", "lastLocation null")
                    return
                }

                // ── Log de diagnóstico ────────────────────────────────────────
                Log.d(
                    "PedalDebug",
                    "Fix | Acc=${"%.1f".format(location.accuracy)} m " +
                    "Vel=${"%.2f".format(location.speed)} m/s " +
                    "hasSpeed=${location.hasSpeed()}"
                )

                // ── Filtro 1: Precisão ────────────────────────────────────────
                if (!location.hasAccuracy() || location.accuracy > MIN_ACCURACY_METERS) {
                    Log.d("PedalDebug", ">>> Fix DESCARTADO: acc=${location.accuracy} m")
                    return
                }

                // ── Filtro 2: Deadband de velocidade ──────────────────────────
                val rawSpeedMs = if (location.hasSpeed()) location.speed else 0f
                val speedMs = if (rawSpeedMs >= MIN_SPEED_MS) rawSpeedMs else 0f
                _speedKmh.value = speedMs * 3.6

                // ── Filtro 3: Acúmulo de distância + persistência ─────────────
                if (speedMs > 0f) {
                    lastLocation?.let { prev ->
                        val segmentMeters = prev.distanceTo(location)
                        // Rejeita segmentos implausíveis (>100 m/s ≈ 360 km/h)
                        if (segmentMeters > 0f && segmentMeters < 100f) {
                            totalDistanceMeters += segmentMeters
                            _distanceTraveled.value = totalDistanceMeters / 1000.0

                            val sessionId = currentSession?.id ?: return@let
                            val point = PedalPoint(
                                sessionId = sessionId,
                                latitude  = location.latitude,
                                longitude = location.longitude,
                                speed     = _speedKmh.value,
                                distance  = _distanceTraveled.value
                            )
                            serviceScope.launch(Dispatchers.IO) {
                                val rowId = dao.insertPoint(point)
                                Log.d("PedalDebug", "PedalPoint inserido rowId=$rowId, sessionId=$sessionId")
                            }
                        }
                    }
                }
                lastLocation = location
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
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
