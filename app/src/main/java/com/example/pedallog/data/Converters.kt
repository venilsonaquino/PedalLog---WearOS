package com.example.pedallog.data

import androidx.room.TypeConverter
import java.util.Date

/**
 * TypeConverters para o Room Database do PedalLog.
 *
 * O Room não sabe serializar [Date] nativamente, então este conversor
 * mapeia Date ↔ Long (epoch em milissegundos) para armazenamento no SQLite.
 *
 * Registrado em [AppDatabase] via @TypeConverters(Converters::class).
 */
class Converters {

    /**
     * Converte [Date] para [Long] (epoch ms) para persistir no banco.
     * Null é preservado — usado para campos como [PedalSession.endTime].
     */
    @TypeConverter
    fun fromDate(date: Date?): Long? = date?.time

    /**
     * Reconstrói um [Date] a partir do valor Long armazenado no banco.
     */
    @TypeConverter
    fun toDate(value: Long?): Date? = value?.let { Date(it) }
}
