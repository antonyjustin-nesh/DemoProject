package com.nlevcluster.sender.database.util

import androidx.room.TypeConverter
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.nllogger.LogType
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.Instant
import logcat.logcat
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    private var dateFormat = SimpleDateFormat("ddMMyy", Locale.getDefault())
    private var timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())

    fun getTimeFromLong(dtLong: Long): String {
        timeFormat.timeZone = TimeZone.getTimeZone(ZoneId.of("UTC"))
        val time = timeFormat.format(Date(dtLong))
        logcat { "time converted $time" }
        return time
    }

    fun getDateFromLong(dtLong: Long): String {
        dateFormat.timeZone = TimeZone.getTimeZone(ZoneId.of("UTC"))
        val date = dateFormat.format(Date(dtLong))
        logcat { "date converted $date" }
        return date
    }

}