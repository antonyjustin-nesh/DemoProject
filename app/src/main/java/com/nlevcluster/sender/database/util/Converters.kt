package com.nlevcluster.sender.database.util

import androidx.room.TypeConverter
import com.nlevcluster.sender.model.EvMode
import kotlinx.datetime.Instant
import com.nlevcluster.sender.database.LogType

class InstantConverter {
    @TypeConverter
    fun longToInstant(value: Long?): Instant? =
        value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun instantToLong(instant: Instant?): Long? =
        instant?.toEpochMilliseconds()
}

class LogConverter {
    @TypeConverter
    fun toLogType(value: String): LogType = enumValueOf(value)

    @TypeConverter
    fun fromLogType(value: LogType): String = value.name
}

class EvModeConverter {
    @TypeConverter
    fun toEvMode(value: String): EvMode = enumValueOf(value)

    @TypeConverter
    fun fromEvMode(value: EvMode): String = value.name
}