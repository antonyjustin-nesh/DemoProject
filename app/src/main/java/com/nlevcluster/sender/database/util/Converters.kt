package com.nlevcluster.sender.database.util

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import logcat.logcat
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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