package com.pedallog.app.modules.tracking.domain.repositories

import com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal
import com.pedallog.app.modules.tracking.domain.valueobjects.LocationUpdate
import kotlinx.coroutines.flow.Flow

/**
 * Contrato abstrato de domínio para o provedor de geolocalização do GPS.
 * Desacopla a regra de negócio do SDK do Android.
 */
interface IGpsProvider {

    fun observeGpsSignal(): Flow<GpsSignal>

    fun observeLocationUpdates(isPaused: Boolean): Flow<LocationUpdate>
}
