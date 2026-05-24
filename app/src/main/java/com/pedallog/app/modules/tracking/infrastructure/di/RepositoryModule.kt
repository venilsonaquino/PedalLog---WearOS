package com.pedallog.app.modules.tracking.infrastructure.di

import com.pedallog.app.modules.tracking.domain.repositories.ISessionRepository
import com.pedallog.app.modules.tracking.infrastructure.repositories.RoomSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt encarregado de vincular abstrações com implementações concretas (DIP).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(roomSessionRepository: RoomSessionRepository): ISessionRepository
}
