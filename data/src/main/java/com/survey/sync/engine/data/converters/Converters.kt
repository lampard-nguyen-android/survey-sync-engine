package com.survey.sync.engine.data.converters

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converters for Room database.
 * Handles conversion of complex types that Room doesn't support natively.
 */
class Converters {

    /**
     * Convert timestamp to Date object.
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * Convert Date object to timestamp.
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
