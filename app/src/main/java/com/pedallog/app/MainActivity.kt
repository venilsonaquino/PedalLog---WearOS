package com.pedallog.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import com.pedallog.app.util.FormatUtils
import android.util.Log
import android.os.Looper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import androidx.lifecycle.lifecycleScope
import com.pedallog.app.data.AppDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

/**
 * Activity principal do PedalLog para Wear OS.
 *
 * Exibe velocidade e distância em tempo real, e gerencia o ciclo
 * de vida da sessão através de três estados de botão:
 *  - Start     → sem sessão ativa
 *  - Pause     → sessão em andamento (rastreando)
 *  - Continuar → sessão pausada
 *
 * Long-press em qualquer botão de sessão ativa dispara ACTION_FINISH.
 */
class MainActivity : ComponentActivity() {

    private var isAmbient by mutableStateOf(false)
    private var ambientUpdateTrigger by mutableStateOf(0)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
        }

        override fun onExitAmbient() {
            isAmbient = false
        }

        override fun onUpdateAmbient() {
            // Força a recomposição a cada minuto em modo ambient
            // para atualizar a posição anti-burn-in
            ambientUpdateTrigger++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))

        // Debug: Listar sessões restauradas do banco
        val dao = AppDatabase.getInstance(this).pedalDao()
        lifecycleScope.launch {
            try {
                val sessions = dao.getAllSessions()
                Log.d("PedalDebug", "Sessões restauradas: ${sessions.size}")
            } catch (e: Exception) {
                Log.e("PedalDebug", "Erro ao acessar banco: ${e.message}")
            }
        }

        setContent { PedalLogApp(isAmbient, ambientUpdateTrigger) }
    }
}

// ── Paleta de cores ────────────────────────────────────────────────────────────
private val GreenAccent     = Color(0xFF00E676)   // Start
private val AmberAccent     = Color(0xFFFFAB00)   // Pause
private val CyanAccent      = Color(0xFF40C4FF)   // Continuar / Distância
private val LabelGray       = Color(0xFFB0BEC5)
private val BackgroundBlack = Color(0xFF000000)

private enum class GpsWarningType {
    DISABLED,
    WEAK
}

@Composable
fun PedalLogApp(isAmbient: Boolean, ambientUpdateTrigger: Int) {
    val context = LocalContext.current

    // ── Observa o estado do serviço ───────────────────────────────────────────
    val speed         by TrackingService.speedKmh.collectAsState()
    val distance      by TrackingService.distanceTraveled.collectAsState()
    val isTracking    by TrackingService.isTracking.collectAsState()
    val isPaused      by TrackingService.isPaused.collectAsState()
    val hasSession    by TrackingService.hasActiveSession.collectAsState()
    val activeTime    by TrackingService.activeTimeSeconds.collectAsState()
    val gpsService    by TrackingService.gpsSignal.collectAsState()
    val elevationGain by TrackingService.elevationGainMeters.collectAsState()

    // ── Permissões ────────────────────────────────────────────────────────────
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingStart by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Solicita permissão ao abrir o app (sem esperar o clique em Start)
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Inicia serviço após permissão concedida via pendingStart
    LaunchedEffect(hasPermission) {
        if (hasPermission && pendingStart) {
            sendServiceAction(context, TrackingService.ACTION_START)
            pendingStart = false
        }
    }

    // ── Pre-Ride GPS Status Checking ──────────────────────────────────────────
    var localGpsSignal by remember { mutableStateOf(com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.ACQUIRING) }

    LaunchedEffect(hasSession, hasPermission) {
        if (hasSession || !hasPermission) {
            return@LaunchedEffect
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        val localCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val accuracyMeters = if (location.hasAccuracy()) location.accuracy else 999f
                val gpsAccuracy = com.pedallog.app.modules.tracking.domain.valueobjects.GpsAccuracy(accuracyMeters)
                if (gpsAccuracy.isStrong()) {
                    localGpsSignal = com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.STRONG
                    return
                }
                localGpsSignal = com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.WEAK
            }
        }

        try {
            val isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (isEnabled) {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                    .setMinUpdateIntervalMillis(1000L)
                    .build()
                fusedClient.requestLocationUpdates(request, localCallback, Looper.getMainLooper())
            }

            while (isActive) {
                val isGpsActive = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                if (!isGpsActive) {
                    localGpsSignal = com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.DISABLED
                }
                if (isGpsActive && localGpsSignal == com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.DISABLED) {
                    localGpsSignal = com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.ACQUIRING
                    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                        .setMinUpdateIntervalMillis(1000L)
                        .build()
                    fusedClient.requestLocationUpdates(request, localCallback, Looper.getMainLooper())
                }
                delay(3000L)
            }
        } finally {
            fusedClient.removeLocationUpdates(localCallback)
        }
    }

    val currentGpsSignal = if (hasSession) gpsService else localGpsSignal
    var gpsWarningType by remember { mutableStateOf<GpsWarningType?>(null) }

    // ── Prevenção de Burn-in ──────────────────────────────────────────────────
    // Calcula um offset aleatório baseado no tempo. Como ambientUpdateTrigger
    // muda a cada minuto (onUpdateAmbient), a tela se deslocará levemente.
    val burnInOffset = remember(isAmbient, ambientUpdateTrigger) {
        if (isAmbient) {
            val minutes = System.currentTimeMillis() / 60000
            val shiftX = (((minutes % 5) - 2) * 2).toInt() // -4 a +4 dp
            val shiftY = ((((minutes / 5) % 5) - 2) * 2).toInt() // -4 a +4 dp
            Modifier.offset(shiftX.dp, shiftY.dp)
        } else {
            Modifier
        }
    }

    // ── Paleta Adaptativa ─────────────────────────────────────────────────────
    val mainMetricColor = if (isAmbient) Color.White else CyanAccent
    val secondaryMetricColor = if (isAmbient) Color.LightGray else Color.White
    val unitColor = if (isAmbient) Color.Gray else LabelGray

    // ── Layout ────────────────────────────────────────────────────────────────
    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundBlack)
                        .then(burnInOffset), // Aplica o shift de burn-in em toda a coluna
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Spacer inicial para afastar do TimeText no topo do relógio
                    Spacer(Modifier.height(12.dp))

                    // ── Distância (Métrica Principal) ──
                    Text(
                        text = "%.2f".format(distance),
                        color = mainMetricColor,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "km",
                        color = unitColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    // ── Métricas Secundárias (Tempo e Subida) ──
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tempo Ativo
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = FormatUtils.formatActiveTime(activeTime, isAmbient),
                                color = secondaryMetricColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "tempo",
                                color = unitColor,
                                fontSize = 11.sp
                            )
                        }

                        // Divisor
                        Text(text = "|", color = unitColor.copy(alpha = 0.3f), fontSize = 18.sp)

                        // Ganho de Elevação (Subida)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "%.0f".format(elevationGain),
                                color = secondaryMetricColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "subida (m)",
                                color = unitColor,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // ── Ocultar botões em modo Ambient ──
                    if (!isAmbient) {
                        if (!hasSession) {
                            GpsStatusIndicator(status = currentGpsSignal)
                            Spacer(Modifier.height(6.dp))
                        }

                        // ── Botão principal (máquina de estados) ──────────────────────
                        when {
                            !hasSession -> {
                                // ── Estado: Sem sessão → Start ──
                                ActionButton(
                                    label = "Start",
                                    color = GreenAccent,
                                    textColor = Color.Black,
                                    onShortClick = {
                                        if (!hasPermission) {
                                            pendingStart = true
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                        if (hasPermission) {
                                            if (currentGpsSignal == com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.DISABLED) {
                                                gpsWarningType = GpsWarningType.DISABLED
                                            }
                                            if (currentGpsSignal == com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.WEAK ||
                                                currentGpsSignal == com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.ACQUIRING) {
                                                gpsWarningType = GpsWarningType.WEAK
                                            }
                                            if (currentGpsSignal == com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.STRONG) {
                                                sendServiceAction(context, TrackingService.ACTION_START)
                                            }
                                        }
                                    }
                                )
                            }

                            isTracking -> {
                                // ── Estado: Rastreando → Pause (long-press = Finalizar) ──
                                ActionButton(
                                    label = "Pause",
                                    color = AmberAccent,
                                    textColor = Color.Black,
                                    onShortClick = {
                                        sendServiceAction(context, TrackingService.ACTION_PAUSE)
                                    },
                                    onLongClick = {
                                        sendServiceAction(context, TrackingService.ACTION_FINISH)
                                    }
                                )
                            }

                            isPaused -> {
                                // ── Estado: Pausado → Continuar (long-press = Finalizar) ──
                                ActionButton(
                                    label = "Continuar",
                                    color = CyanAccent,
                                    textColor = Color.Black,
                                    width = 96.dp,
                                    onShortClick = {
                                        sendServiceAction(context, TrackingService.ACTION_START)
                                    },
                                    onLongClick = {
                                        sendServiceAction(context, TrackingService.ACTION_FINISH)
                                    }
                                )
                            }
                        }

                        // Dica de long-press visível quando há sessão ativa
                        if (hasSession) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Segurar = Finalizar",
                                color = LabelGray.copy(alpha = 0.55f),
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    // Spacer final para garantir margem segura da borda inferior redonda
                    Spacer(Modifier.height(10.dp))
                }

                // ── Warning dialog overlay ──
                if (gpsWarningType != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f))
                            .pointerInput(Unit) {}, // block clicks to behind
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val isGpsDisabled = gpsWarningType == GpsWarningType.DISABLED
                            
                            Text(
                                text = if (isGpsDisabled) "⚠️ GPS Desativado" else "📡 Sinal Fraco",
                                color = if (isGpsDisabled) Color.Red else AmberAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Text(
                                text = if (isGpsDisabled) {
                                    "Ative o GPS nas configurações do relógio."
                                } else {
                                    "A distância inicial pode ser imprecisa. Iniciar mesmo assim?"
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isGpsDisabled) {
                                    ActionButton(
                                        label = "Ajustes",
                                        color = Color.DarkGray,
                                        textColor = Color.White,
                                        width = 76.dp,
                                        onShortClick = {
                                            gpsWarningType = null
                                            context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                        }
                                    )
                                    ActionButton(
                                        label = "Voltar",
                                        color = CyanAccent,
                                        textColor = Color.Black,
                                        width = 76.dp,
                                        onShortClick = { gpsWarningType = null }
                                    )
                                }
                                if (!isGpsDisabled) {
                                    ActionButton(
                                        label = "Iniciar",
                                        color = GreenAccent,
                                        textColor = Color.Black,
                                        width = 76.dp,
                                        onShortClick = {
                                            gpsWarningType = null
                                            sendServiceAction(context, TrackingService.ACTION_START)
                                        }
                                    )
                                    ActionButton(
                                        label = "Aguardar",
                                        color = Color.DarkGray,
                                        textColor = Color.White,
                                        width = 76.dp,
                                        onShortClick = { gpsWarningType = null }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * GpsStatusIndicator exibe visualmente a qualidade do sinal GPS
 * com cores harmoniosas e elegantes.
 */
@Composable
fun GpsStatusIndicator(status: com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal) {
    val (label, color) = when (status) {
        com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.DISABLED -> "GPS Desativado" to Color(0xFFEF5350)
        com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.ACQUIRING -> "Buscando Sinal..." to Color(0xFFFFB74D)
        com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.WEAK -> "Sinal Fraco" to Color(0xFFFFD54F)
        com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal.STRONG -> "GPS Pronto" to Color(0xFF66BB6A)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = LabelGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Botão de ação com suporte a clique curto e long-press.
 */
@Composable
private fun ActionButton(
    label: String,
    color: Color,
    textColor: Color,
    width: Dp = 88.dp,
    onShortClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    if (onLongClick != null) {
        Box(
            modifier = Modifier
                .size(width = width, height = 40.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onShortClick() },
                        onLongPress = { onLongClick() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
    if (onLongClick == null) {
        androidx.wear.compose.material.Button(
            onClick = onShortClick,
            modifier = Modifier.size(width = width, height = 40.dp),
            colors = androidx.wear.compose.material.ButtonDefaults.buttonColors(
                backgroundColor = color,
                contentColor = textColor
            )
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/**
 * Envia uma Intent action ao [TrackingService] iniciando-o como foreground se necessário.
 */
private fun sendServiceAction(context: Context, action: String) {
    val intent = Intent(context, TrackingService::class.java).apply { this.action = action }
    ContextCompat.startForegroundService(context, intent)
}
