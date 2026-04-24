package com.example.pedallog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

        setContent { PedalLogApp(isAmbient, ambientUpdateTrigger) }
    }
}

// ── Paleta de cores ────────────────────────────────────────────────────────────
private val GreenAccent     = Color(0xFF00E676)   // Start
private val AmberAccent     = Color(0xFFFFAB00)   // Pause
private val CyanAccent      = Color(0xFF40C4FF)   // Continuar / Distância
private val LabelGray       = Color(0xFFB0BEC5)
private val BackgroundBlack = Color(0xFF000000)

@Composable
fun PedalLogApp(isAmbient: Boolean, ambientUpdateTrigger: Int) {
    val context = LocalContext.current

    // ── Observa o estado do serviço ───────────────────────────────────────────
    val speed      by TrackingService.speedKmh.collectAsState()
    val distance   by TrackingService.distanceTraveled.collectAsState()
    val isTracking by TrackingService.isTracking.collectAsState()
    val isPaused   by TrackingService.isPaused.collectAsState()
    val hasSession by TrackingService.hasActiveSession.collectAsState()

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
    val speedColor    = if (isAmbient) Color.White else GreenAccent
    val distanceColor = if (isAmbient) Color.LightGray else CyanAccent
    val unitColor     = if (isAmbient) Color.DarkGray else LabelGray

    // ── Layout ────────────────────────────────────────────────────────────────
    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundBlack)
                    .then(burnInOffset), // Aplica o shift de burn-in em toda a coluna
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Velocidade ──
                Text(
                    text = "%.1f".format(speed),
                    color = speedColor,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "km/h",
                    color = unitColor,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                // ── Distância ──
                Text(
                    text = "Distância: ${"%.2f".format(distance)} km",
                    color = distanceColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                // ── Ocultar botões em modo Ambient ──
                if (!isAmbient) {
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
                                    } else {
                                        sendServiceAction(context, TrackingService.ACTION_START)
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Segurar = Finalizar",
                            color = LabelGray.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Botão de ação com suporte a clique curto e long-press.
 *
 * Quando [onLongClick] é fornecido, o controle de gestos é feito via
 * [Modifier.pointerInput] + [detectTapGestures] — compatível com Wear OS
 * sem conflito com o scroll da tela redonda. Quando não há long-press,
 * usa o [androidx.wear.compose.material.Button] padrão.
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
        // Box com detecção de gestos manual para suportar long-press
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
    } else {
        // Botão padrão do Wear Compose quando não há long-press
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
