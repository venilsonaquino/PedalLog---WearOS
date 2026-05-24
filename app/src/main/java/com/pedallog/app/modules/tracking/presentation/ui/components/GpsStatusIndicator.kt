package com.pedallog.app.modules.tracking.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal

/**
 * Indicador visual elegante da qualidade do sinal GPS para relógios.
 * Respeita a regra de menos de 50 linhas do Object Calisthenics.
 */
@Composable
fun GpsStatusIndicator(status: GpsSignal) {
    val (label, color) = when (status) {
        GpsSignal.DISABLED -> "GPS Desativado" to Color(0xFFEF5350)
        GpsSignal.ACQUIRING -> "Buscando Sinal..." to Color(0xFFFFB74D)
        GpsSignal.WEAK -> "Sinal Fraco" to Color(0xFFFFD54F)
        GpsSignal.STRONG -> "GPS Pronto" to Color(0xFF66BB6A)
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
            color = Color(0xFFB0BEC5), // LabelGray
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
