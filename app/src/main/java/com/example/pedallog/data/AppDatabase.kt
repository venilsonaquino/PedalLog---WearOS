package com.example.pedallog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Banco de dados Room do PedalLog — versão 3.
 *
 * Histórico de versões:
 *  v1 → Schema inicial com PedalPoint apenas.
 *  v2 → Adicionada PedalSession + coluna sessionId em PedalPoint + TypeConverters (Date).
 *  v3 → Adicionada coluna syncUuid em PedalSession para sincronização via Wearable Data Layer.
 */
@Database(
    entities = [PedalSession::class, PedalPoint::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pedalDao(): PedalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pedallog.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
