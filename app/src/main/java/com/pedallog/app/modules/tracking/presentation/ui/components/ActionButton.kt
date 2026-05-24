package com.pedallog.app.modules.tracking.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text

/**
 * Botão adaptativo com suporte a clique curto e clique longo.
 * Respeita a regra de menos de 50 linhas do Object Calisthenics.
 */
@Composable
fun ActionButton(
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
            Text(label, color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        return
    }

    Button(
        onClick = onShortClick,
        modifier = Modifier.size(width = width, height = 40.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = color, contentColor = textColor)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
