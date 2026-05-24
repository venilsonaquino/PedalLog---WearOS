package com.pedallog.app.modules.tracking.infrastructure.di

import com.pedallog.app.modules.tracking.domain.repositories.IGpsProvider
import com.pedallog.app.modules.tracking.domain.repositories.IVibrator
import com.pedallog.app.modules.tracking.infrastructure.feedback.AndroidVibrator
import com.pedallog.app.modules.tracking.infrastructure.gps.FusedGpsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt encarregado de injetar dependências de sensores e hardware (DIP).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HardwareModule {

    @Binds
    @Singleton
    abstract fun bindGpsProvider(fusedGpsProvider: FusedGpsProvider): IGpsProvider

    @Binds
    @Singleton
    abstract fun bindVibrator(androidVibrator: AndroidVibrator): IVibrator
}
