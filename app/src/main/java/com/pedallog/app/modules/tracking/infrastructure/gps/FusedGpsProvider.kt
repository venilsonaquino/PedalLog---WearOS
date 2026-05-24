package com.pedallog.app.modules.tracking.infrastructure.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pedallog.app.modules.tracking.domain.repositories.IGpsProvider
import com.pedallog.app.modules.tracking.domain.valueobjects.Coordinate
import com.pedallog.app.modules.tracking.domain.valueobjects.Distance
import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation
import com.pedallog.app.modules.tracking.domain.valueobjects.GpsAccuracy
import com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal
import com.pedallog.app.modules.tracking.domain.valueobjects.LocationUpdate
import com.pedallog.app.modules.tracking.domain.valueobjects.Speed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Provedor de geolocalização por GPS usando o FusedLocationProviderClient.
 * Desacopla regras de hardware do Android para expor modelos limpos ao domínio.
 */
class FusedGpsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : IGpsProvider {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: Location? = null

    @SuppressLint("MissingPermission")
    override fun observeGpsSignal(): Flow<GpsSignal> = callbackFlow {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            trySend(GpsSignal.DISABLED)
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val accuracy = GpsAccuracy(location.accuracy)
                val signal = if (accuracy.isStrong()) GpsSignal.STRONG else GpsSignal.WEAK
                trySend(signal)
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            locationClient.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    override fun observeLocationUpdates(isPaused: Boolean): Flow<LocationUpdate> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val accuracy = GpsAccuracy(location.accuracy)
                
                if (!accuracy.isStrong() && location.accuracy > ACCURACY_LIMIT_METERS) {
                    return
                }

                val speed = Speed.fromMetersPerSecond(location.speed.toDouble())
                val elevation = Elevation(location.altitude)
                val coordinate = Coordinate(location.latitude, location.longitude)
                
                val segmentDistance = lastLocation?.let { 
                    val distanceMeters = it.distanceTo(location).toDouble()
                    if (distanceMeters > 0.0 && distanceMeters < MAX_SEGMENT_DISTANCE_METERS) {
                        Distance.fromMeters(distanceMeters)
                    } else {
                        Distance(0.0)
                    }
                } ?: Distance(0.0)

                lastLocation = location

                trySend(
                    LocationUpdate(
                        coordinate = coordinate,
                        speed = speed,
                        segmentDistance = segmentDistance,
                        elevation = elevation,
                        timestamp = location.time
                    )
                )
            }
        }

        val interval = if (isPaused) PAUSED_INTERVAL_MS else ACTIVE_INTERVAL_MS
        val fastest = if (isPaused) PAUSED_FASTEST_INTERVAL_MS else ACTIVE_FASTEST_INTERVAL_MS

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(fastest)
            .build()

        locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            locationClient.removeLocationUpdates(callback)
            lastLocation = null
        }
    }

    companion object {
        private const val ACCURACY_LIMIT_METERS = 20f
        private const val MAX_SEGMENT_DISTANCE_METERS = 100.0
        
        private const val ACTIVE_INTERVAL_MS = 1000L
        private const val ACTIVE_FASTEST_INTERVAL_MS = 500L
        private const val PAUSED_INTERVAL_MS = 5000L
        private const val PAUSED_FASTEST_INTERVAL_MS = 2000L
    }
}
