package com.nlevcluster.sender.trip.contentprovider

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.nlevcluster.sender.di.ProviderEntryPoint
import com.nlevcluster.sender.model.MonthlyTripReport
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.nllogger.NLLoggerLogHelper
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportDatabase
import com.nlevcluster.sender.trip.db.TripReportEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.log

class TripReportContentProvider : ContentProvider() {

    private val tag = "TripReportContentProvider"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private lateinit var tripDao: TripReportDao
    lateinit var logHelper: LogHelper
    lateinit var gson: Gson

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(): Boolean {

        val context = context?.applicationContext ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ProviderEntryPoint::class.java
        )
        gson = entryPoint.gson()
        tripDao = entryPoint.tripDao()
        logHelper = NLLoggerLogHelper(context)

        return true
    }

    override fun getType(uri: Uri): String? {
        return null
    }


    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uri.lastPathSegment) {
            MONTHLY_REPORT -> {
                val selectedDate = uri.getQueryParameter("selectedDate")
                val cursor = MatrixCursor(arrayOf(MONTHLY_REPORT))

                logHelper.log(
                    type = LogType.INFO,
                    tag = tag,
                    message = "Query received for MONTHLY_REPORT with selectedDate: $selectedDate"
                )

                try {

                    val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
                    val zoneId = ZoneId.systemDefault()

                    val firstDayOfMonth = LocalDate.now().withDayOfMonth(1)
                    val startOfDay = LocalDateTime.of(firstDayOfMonth, LocalTime.MIDNIGHT)
                    val formattedDateTime = startOfDay.format(dateFormatter)

                    val startDateTime =
                        LocalDateTime.parse(formattedDateTime, dateFormatter)
                    val endDateTime = LocalDateTime.parse(selectedDate, dateFormatter)

                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "StartDateTime: $startDateTime, EndDateTime: $endDateTime"
                    )

                    val startTimestamp = startDateTime.atZone(zoneId).toInstant().toEpochMilli()
                    val endTimestamp = endDateTime.atZone(zoneId).toInstant().toEpochMilli()

                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "StartTimestamp: $startTimestamp, EndTimestamp: $endTimestamp"
                    )

                    val tripList = tripDao.getTripsByEndTimeRange(startTimestamp, endTimestamp)

                    val tripReport = generateMonthlyTripReport(tripList)
                    val tripReportData = gson.toJson(tripReport)

                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "Generated MonthlyTripReport JSON"
                    )

                    cursor.addRow(arrayOf(tripReportData))

                } catch (e: Exception) {
                    logHelper.log(
                        type = LogType.ERROR,
                        tag = tag,
                        message = "Exception in MONTHLY_REPORT query: ${e.message}"
                    )
                    cursor.addRow(arrayOf(null))
                }
                cursor
            }

            else -> {
                logHelper.log(
                    type = LogType.ERROR,
                    tag = tag,
                    message = "Unknown URI path: ${uri.lastPathSegment}"
                )
                null
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        return uri
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }

    @SuppressLint("SimpleDateFormat", "DefaultLocale")
    fun generateMonthlyTripReport(tripList: List<TripReportEntity>): MonthlyTripReport {
        if (tripList.isEmpty()) {
            logHelper.log(
                type = LogType.INFO,
                tag = tag,
                message = "Trip list is empty, returning empty report"
            )
            return MonthlyTripReport()
        }

        logHelper.log(
            type = LogType.INFO,
            tag = tag,
            message = "Generating MonthlyTripReport from ${tripList.size} trips"
        )

        val totalTrips = tripList.size

        var totalAverageSpeed = 0f
        var maxSpeed = 0f

        var totalSocConsumption = 0

        var totalSpeedAlerts = 0
        var totalIdleAlerts = 0
        var totalHarshAlerts = 0
        var totalRegenCount = 0

        var totalDistance = 0.0

        var totalIdleDuration = 0L
        val totalMovingDuration = 0L
        var totalTripDuration = 0L
        val tripDaysSet = mutableSetOf<String>()

        var minStartTime = Long.MAX_VALUE
        var maxEndTime = Long.MIN_VALUE

        var date = ""
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd")

        for (trip in tripList) {
            totalAverageSpeed += trip.averageSpeedKmph
            maxSpeed = maxOf(maxSpeed, trip.maximumSpeedKmph)
            totalSocConsumption += (trip.endSOC - trip.startSOC)
            totalSpeedAlerts += trip.speedAlertsCount
            totalIdleAlerts += trip.idleAlertsCount
            totalHarshAlerts += trip.harshAlertsCount
            totalRegenCount += trip.regenCount
            totalDistance += (trip.endOdometer - trip.startOdometer)
            totalIdleDuration += trip.idleDurationMs
            totalTripDuration += (trip.tripEndTimeDurationMs - trip.tripStartTimeDurationMs)
            date = dateFormatter.format(Date(trip.tripEndTimeDurationMs))
            if (date.isNotEmpty()) {
                tripDaysSet.add(date)
            }
            minStartTime = minOf(minStartTime, trip.tripStartTimeDurationMs)
            maxEndTime = maxOf(maxEndTime, trip.tripEndTimeDurationMs)
        }

        logHelper.log(type = LogType.INFO, tag = tag, message = "Completed trip data aggregation")

        return MonthlyTripReport(
            averageSpeedKmph = totalAverageSpeed / totalTrips,
            maximumSpeedKmph = maxSpeed,
            tripCount = totalTrips,
            speedAlertsCount = totalSpeedAlerts,
            idleAlertsCount = totalIdleAlerts,
            harshAlertsCount = totalHarshAlerts,
            regenCount = totalRegenCount,
            tripDistanceKm = String.format("%.2f", totalDistance),
            idleDurationMs = totalIdleDuration,
            movingDurationMs = totalMovingDuration,
            tripDuration = formatMillisToHHMMSS(totalTripDuration),
            tripDays = tripDaysSet.size,
        )
    }

    @SuppressLint("DefaultLocale")
    private fun formatMillisToHHMMSS(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    companion object {

        const val MONTHLY_REPORT = "monthly_report"

    }

}