package com.pedallog.app.modules.tracking.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/**
 * Gerencia a máquina de estados adaptativa dos botões de controle da pedalada.
 * Respeita a regra de menos de 50 linhas do Object Calisthenics.
 */
@Composable
fun ActionButtonGroup(
    hasActiveSession: Boolean,
    isTracking: Boolean,
    isPaused: Boolean,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onFinishClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        when {
            !hasActiveSession -> ActionButton("Start", Color(0xFF00E676), Color.Black, onShortClick = onStartClick)
            isTracking -> ActionButton("Pause", Color(0xFFFFAB00), Color.Black, onShortClick = onPauseClick, onLongClick = onFinishClick)
            isPaused -> ActionButton("Continuar", Color(0xFF40C4FF), Color.Black, width = 96.dp, onShortClick = onResumeClick, onLongClick = onFinishClick)
        }
        if (hasActiveSession) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Segurar = Finalizar",
                color = Color(0xFFB0BEC5).copy(alpha = 0.55f),
                fontSize = 9.sp
            )
        }
    }
}
