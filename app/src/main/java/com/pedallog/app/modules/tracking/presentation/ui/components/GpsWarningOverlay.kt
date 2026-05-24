package com.pedallog.app.modules.tracking.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/**
 * Overlay modal que alerta o ciclista sobre problemas de sinal de GPS.
 * Respeita a regra de menos de 50 linhas do Object Calisthenics.
 */
@Composable
fun GpsWarningOverlay(
    warningType: GpsWarningType,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isGpsDisabled = warningType == GpsWarningType.DISABLED
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = if (isGpsDisabled) "⚠️ GPS Desativado" else "📡 Sinal Fraco",
                color = if (isGpsDisabled) Color.Red else Color(0xFFFFAB00),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isGpsDisabled) "Ative o GPS nas configurações do relógio." else "A distância inicial pode ser imprecisa. Iniciar mesmo assim?",
                color = Color.White,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isGpsDisabled) {
                    ActionButton("Ajustes", Color.DarkGray, Color.White, 76.dp, onSettingsClick)
                    ActionButton("Voltar", Color(0xFF40C4FF), Color.Black, 76.dp, onDismiss)
                }
                if (!isGpsDisabled) {
                    ActionButton("Iniciar", Color(0xFF00E676), Color.Black, 76.dp, onConfirm)
                    ActionButton("Aguardar", Color.DarkGray, Color.White, 76.dp, onDismiss)
                }
            }
        }
    }
}
