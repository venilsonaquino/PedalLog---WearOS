package com.pedallog.app.modules.tracking.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import com.pedallog.app.modules.tracking.presentation.ui.components.*
import com.pedallog.app.modules.tracking.presentation.viewmodels.RideViewModel

/**
 * Tela principal do relógio que coordena a visualização reativa dos componentes.
 * Respeita a regra de menos de 50 linhas do Object Calisthenics.
 */
@Composable
fun RideScreen(
    viewModel: RideViewModel,
    isAmbient: Boolean,
    burnInOffset: Modifier,
    gpsWarningType: GpsWarningType?,
    onDismissWarning: () -> Unit,
    onConfirmWarning: () -> Unit,
    onSettingsWarning: () -> Unit,
    onStartClick: () -> Unit
) {
    val distance by viewModel.distance.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val hasActiveSession by viewModel.hasActiveSession.collectAsState()
    val activeTime by viewModel.activeTime.collectAsState()
    val gpsSignal by viewModel.gpsSignal.collectAsState()
    val elevationGain by viewModel.elevationGain.collectAsState()
    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.Black).then(burnInOffset),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RideMetricsGroup(distance, activeTime, elevationGain, isAmbient)
                    Spacer(Modifier.height(6.dp))
                    if (!isAmbient) {
                        if (!hasActiveSession) {
                            GpsStatusIndicator(gpsSignal)
                            Spacer(Modifier.height(6.dp))
                        }
                        ActionButtonGroup(
                            hasActiveSession, isTracking, isPaused,
                            onStartClick = onStartClick,
                            onPauseClick = { viewModel.pauseRide() },
                            onResumeClick = { viewModel.startRide() },
                            onFinishClick = { viewModel.finishRide() }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                gpsWarningType?.let { GpsWarningOverlay(it, onDismissWarning, onConfirmWarning, onSettingsWarning) }
            }
        }
    }
}
