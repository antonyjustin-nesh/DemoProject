package com.nlevcluster.sender.database.util

import androidx.room.TypeConverter
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.model.EvMode
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

@Module
@InstallIn(SingletonComponent::class)
object Util {

    private val dateFormat = SimpleDateFormat("ddMMyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())

    fun getTimeFromLong(dtLong: Long): String {
        return timeFormat.format(Date(dtLong))
    }

    fun getDateFromLong(dtLong: Long): String {
        return dateFormat.format(Date(dtLong))
    }

}