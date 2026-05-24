package com.pedallog.app.modules.tracking.infrastructure.persistence.room

import androidx.room.TypeConverter
import java.util.Date

/**
 * TypeConverters para o Room Database do PedalLog.
 */
class Converters {

    @TypeConverter
    fun fromDate(date: Date?): Long? = date?.time

    @TypeConverter
    fun toDate(value: Long?): Date? = value?.let { Date(it) }
}
