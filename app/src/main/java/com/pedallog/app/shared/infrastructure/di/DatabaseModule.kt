package com.pedallog.app.shared.infrastructure.di

import android.content.Context
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.AppDatabase
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.daos.PedalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt encarregado de injetar dependências relacionadas à base de dados Room.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun providePedalDao(database: AppDatabase): PedalDao {
        return database.pedalDao()
    }
}
