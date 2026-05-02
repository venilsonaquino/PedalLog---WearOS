package com.pedallog.app.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Banco de dados Room do PedalLog.
 */
@Database(
    entities = [PedalSession::class, PedalPoint::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pedalDao(): PedalDao

    companion object {
        private const val TAG = "PedalDebug"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE pedal_points ADD COLUMN segmentBreak INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    Log.w(TAG, "Coluna segmentBreak já existe", e)
                }
            }
        }

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
                    .addMigrations(MIGRATION_4_5)
                    // .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
