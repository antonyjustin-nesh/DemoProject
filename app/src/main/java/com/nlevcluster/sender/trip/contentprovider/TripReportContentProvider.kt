package com.nlevcluster.sender.trip.contentprovider

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.nlevcluster.sender.model.MonthlyTripReport
import com.nlevcluster.sender.model.TripReport
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportDatabase
import com.nlevcluster.sender.trip.db.TripReportEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.logcat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class TripReportContentProvider : ContentProvider() {

    private lateinit var gson: Gson
    val tag = "TripReportContentProvider"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var tripDao: TripReportDao? = null

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(): Boolean {
        tripDao = context?.let { TripReportDatabase.getInstance(it).tripReportDao() }
        gson = Gson()
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
                Log.d(tag, "TripReportContentProvider - selectedDate - ${selectedDate}")

                val cursor = MatrixCursor(arrayOf(MONTHLY_REPORT))
                try {

                    runBlocking(Dispatchers.IO) {
                        val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
                        val zoneId = ZoneId.systemDefault()

                        val startDateTime =
                            LocalDateTime.parse("01-05-2025 00:00:00", dateFormatter)
                        //val endDateTime = LocalDateTime.parse("20-05-2025 11:20:00", dateFormatter)
                        val endDateTime = LocalDateTime.parse(selectedDate, dateFormatter)

                        val startTimestamp = startDateTime.atZone(zoneId).toInstant().toEpochMilli()
                        val endTimestamp = endDateTime.atZone(zoneId).toInstant().toEpochMilli()

                        val tripList = tripDao?.getTripsByEndTimeRange(startTimestamp, endTimestamp)
                        Log.d(
                            tag,
                            "Selected Trip List Count - ${tripList?.size} Selected Trip List - $tripList"
                        )

                        val tripReport = tripList?.let { generateMonthlyTripReport(it) }
                        Log.d(tag, "Monthly TripReport - $tripReport")
                        val tripReportData = gson.toJson(tripReport)
                        Log.d(tag, "Monthly TripReportData - $tripReportData")

                        cursor.addRow(arrayOf(tripReportData))
                        Log.d(tag, "Inside Monthly TripReportData Count - ${cursor.count}")
                    }

                    Log.d(tag, "Exit Monthly TripReportData Count - ${cursor.count}")

                    Log.d(tag, "TripReportContentProvider - return cursor - $cursor")
                } catch (e: Exception) {
                    cursor.addRow(arrayOf(null))
                    Log.d(tag, "TripReportContentProvider - return exception cursor - $cursor")
                }
                cursor
            }

            else -> {
                logcat(tag) { "query - no uri matched - uriLastPathSegment - ${uri.lastPathSegment}  uri - $uri" }
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
    private fun generateMonthlyTripReport(tripList: List<TripReportEntity>): MonthlyTripReport {
        if (tripList.isEmpty()) return MonthlyTripReport()

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
        var totalMovingDuration = 0L
        var totalTripDuration = 0L
        var tripDaysSet = mutableSetOf<String>()

        var minStartTime = Long.MAX_VALUE
        var maxEndTime = Long.MIN_VALUE

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

            val date = java.text.SimpleDateFormat("yyyy-MM-dd")
                .format(java.util.Date(trip.tripEndTimeDurationMs))
            tripDaysSet.add(date)

            minStartTime = minOf(minStartTime, trip.tripStartTimeDurationMs)
            maxEndTime = maxOf(maxEndTime, trip.tripEndTimeDurationMs)
        }

        return MonthlyTripReport(
            averageSpeedKmph = totalAverageSpeed / totalTrips,
            maximumSpeedKmph = maxSpeed,
            tripCount = totalTrips,
            speedAlertsCount = totalSpeedAlerts,
            idleAlertsCount = totalIdleAlerts,
            harshAlertsCount = totalHarshAlerts,
            regenCount = totalRegenCount,
            tripDistanceKm = String.format("%.3f", totalDistance),
            idleDurationMs = totalIdleDuration,
            movingDurationMs = totalMovingDuration,
            tripDuration = formatMillisToHHMM(totalTripDuration),
            tripDays = tripDaysSet.size,
        )
    }

    @SuppressLint("DefaultLocale")
    fun formatMillisToHHMM(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return String.format("%02d:%02d", hours, minutes)
    }


    companion object {

        const val MONTHLY_REPORT = "monthly_report"

    }

}