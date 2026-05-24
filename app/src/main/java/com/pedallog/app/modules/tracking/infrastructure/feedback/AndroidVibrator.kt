package com.pedallog.app.modules.tracking.infrastructure.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import com.pedallog.app.modules.tracking.domain.repositories.IVibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Implementação concreta de [IVibrator] usando o hardware de vibração do Android.
 * Respeita a regra de classes com menos de 50 linhas do Object Calisthenics.
 */
class AndroidVibrator @Inject constructor(
    @ApplicationContext private val context: Context
) : IVibrator {

    override fun vibratePause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
            val timings = longArrayOf(0, 150, 100, 150)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    override fun vibrateResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(400, 255))
        }
    }
}
