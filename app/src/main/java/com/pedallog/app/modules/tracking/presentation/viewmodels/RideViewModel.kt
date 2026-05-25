package com.pedallog.app.modules.tracking.presentation.viewmodels

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedallog.app.modules.tracking.domain.repositories.IGpsProvider
import com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal
import com.pedallog.app.modules.tracking.infrastructure.service.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RideViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gpsProvider: IGpsProvider
) : ViewModel() {
    val speed: StateFlow<Double> = TrackingService.speedKmh
    val distance: StateFlow<Double> = TrackingService.distanceTraveled
    val isTracking: StateFlow<Boolean> = TrackingService.isTracking
    val isPaused: StateFlow<Boolean> = TrackingService.isPaused
    val hasActiveSession: StateFlow<Boolean> = TrackingService.hasActiveSession
    val activeTime: StateFlow<Long> = TrackingService.activeTimeSeconds
    val elevationGain: StateFlow<Double> = TrackingService.elevationGainMeters

    val gpsSignal: StateFlow<GpsSignal> = gpsProvider.observeGpsSignal()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = GpsSignal.ACQUIRING
        )

    fun startRide() = sendServiceAction(TrackingService.ACTION_START)
    fun pauseRide() = sendServiceAction(TrackingService.ACTION_PAUSE)
    fun finishRide() = sendServiceAction(TrackingService.ACTION_FINISH)

    private fun sendServiceAction(action: String) {
        val intent = Intent(context, TrackingService::class.java).apply { this.action = action }
        ContextCompat.startForegroundService(context, intent)
    }
}
