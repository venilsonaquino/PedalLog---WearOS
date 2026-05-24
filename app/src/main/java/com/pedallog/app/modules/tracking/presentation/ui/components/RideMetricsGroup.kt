package com.pedallog.app.modules.tracking.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.pedallog.app.shared.util.FormatUtils

/**
 * Agrupador visual das métricas físicas exibidas na tela do relógio (distância, tempo, subida).
 * Respeita a regra de menos de 50 linhas do Object Calisthenics.
 */
@Composable
fun RideMetricsGroup(
    distance: Double,
    activeTime: Long,
    elevationGain: Double,
    isAmbient: Boolean
) {
    val mainMetricColor = if (isAmbient) Color.White else Color(0xFF40C4FF)
    val secondaryMetricColor = if (isAmbient) Color.LightGray else Color.White
    val unitColor = if (isAmbient) Color.Gray else Color(0xFFB0BEC5)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "%.2f".format(distance),
            color = mainMetricColor,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(text = "km", color = unitColor, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = FormatUtils.formatActiveTime(activeTime, isAmbient),
                    color = secondaryMetricColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "tempo", color = unitColor, fontSize = 11.sp)
            }
            Text(text = "|", color = unitColor.copy(alpha = 0.3f), fontSize = 18.sp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f".format(elevationGain),
                    color = secondaryMetricColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "subida (m)", color = unitColor, fontSize = 11.sp)
            }
        }
    }
}
