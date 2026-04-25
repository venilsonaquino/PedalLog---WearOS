package com.pedallog.app.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Banco de dados Room do PedalLog.
 */
@Database(
    entities = [PedalSession::class, PedalPoint::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pedalDao(): PedalDao

    companion object {
        private const val TAG = "PedalDebug"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbPath = context.getDatabasePath("pedal_database")
                Log.d(TAG, "Caminho do banco: ${dbPath.absolutePath} | Existe: ${dbPath.exists()} | Tamanho: ${dbPath.length()} bytes")

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pedal_database"
                )
                    // .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
