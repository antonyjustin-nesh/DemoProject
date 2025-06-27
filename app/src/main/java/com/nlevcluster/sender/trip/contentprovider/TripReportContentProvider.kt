package com.nlevcluster.sender.trip.contentprovider

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.nlevcluster.sender.di.ProviderEntryPoint
import com.nlevcluster.sender.model.DayTripDetails
import com.nlevcluster.sender.model.DayWiseTripReport
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.ModeStats
import com.nlevcluster.sender.model.MonthlyTripReport
import com.nlevcluster.sender.model.TripDetails
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.nllogger.NLLoggerLogHelper
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TripReportContentProvider : ContentProvider() {

    private val tag = "TripReportContentProvider"

    private lateinit var tripDao: TripReportDao
    lateinit var logHelper: LogHelper

    private var modeStats: MutableMap<EvMode, ModeStats> = enumValues<EvMode>()
        .associateWith { ModeStats() }
        .toMutableMap()

    private val json = Json { ignoreUnknownKeys = true }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(): Boolean {

        val context = context?.applicationContext ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ProviderEntryPoint::class.java
        )
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

                val startDate = uri.getQueryParameter("startDate")
                val endDate = uri.getQueryParameter("endDate")
                val cursor = MatrixCursor(arrayOf(MONTHLY_REPORT))

                logHelper.log(
                    type = LogType.INFO,
                    tag = tag,
                    message = "Query received for MONTHLY_REPORT with startDate: $startDate endDate: $endDate"
                )

                try {

                    val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
                    val zoneId = ZoneId.systemDefault()

                    val startDateTime = LocalDateTime.parse(startDate, dateFormatter)
                    val endDateTime = LocalDateTime.parse(endDate, dateFormatter)

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

                    val monthlyTripReport = generateMonthlyTripReport(tripList)
                    val monthlyTripReportDetails = json.encodeToString(monthlyTripReport)
                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "monthlyTripReportDetails - $monthlyTripReportDetails"
                    )

                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "Generated MonthlyTripReport JSON"
                    )

                    cursor.addRow(arrayOf(monthlyTripReportDetails))

                } catch (e: Exception) {
                    logHelper.log(
                        type = LogType.ERROR,
                        tag = tag,
                        message =
                        "Exception in MONTHLY_REPORT query: ${e.message}"
                    )
                    cursor.addRow(arrayOf(null))
                }
                cursor
            }

            DAY_WISE_REPORT -> {

                val startDate = uri.getQueryParameter("startDate")
                val endDate = uri.getQueryParameter("endDate")
                val cursor = MatrixCursor(arrayOf(DAY_WISE_REPORT))

                logHelper.log(
                    type = LogType.INFO,
                    tag = tag,
                    message = "Query received for DAY_WISE_REPORT with startDate: $startDate  endDate: $endDate "
                )

                try {

                    val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
                    val zoneId = ZoneId.systemDefault()

                    val startDateTime = LocalDateTime.parse(startDate, dateFormatter)
                    val endDateTime = LocalDateTime.parse(endDate, dateFormatter)


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

                    val dayWiseTripReport = generateDayWiseTripReport(tripList)
                    val dayWiseTripReportDetails = json.encodeToString(dayWiseTripReport)
                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "dayWiseTripReportDetails - $dayWiseTripReportDetails"
                    )

                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "Generated DayWiseTripReport JSON"
                    )

                    cursor.addRow(arrayOf(dayWiseTripReportDetails))

                } catch (e: Exception) {
                    logHelper.log(
                        type = LogType.ERROR,
                        tag = tag,
                        message =
                        "Exception in DAY_WISE_REPORT query: ${e.message}"
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
        when (uri.lastPathSegment) {
            CLEAR_ALL_TRIP -> {
                tripDao.deleteAllTrips()
                context?.contentResolver?.notifyChange(uri, null)
                return 1
            }

            else -> {
                return 0
            }
        }
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

        var averageSpeed = 0f
        var totalAverageSpeed = 0f
        var maxSpeed = 0f

        var totalSocConsumption = 0

        var totalSpeedAlerts = 0
        var totalIdleAlerts = 0
        var totalHarshAlerts = 0
        var totalRegenCount = 0

        var ecoDistance = 0.0
        var sportsDistance = 0.0
        var powerDistance = 0.0

        var totalDistance = 0.0

        var totalIdleDuration = 0L
        var totalMovingDuration = 0L
        var totalTripDuration = 0L
        val tripDaysSet = mutableSetOf<String>()

        var minStartTime = Long.MAX_VALUE
        var maxEndTime = Long.MIN_VALUE

        var date = ""
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
        val lastTrip = TripDetails()

        var monthStartOdometer = 0.0
        var monthEndOdometer = 0.0

        var vehicleUsage = 0

        tripList.forEachIndexed { index, trip ->
            if (index == 0) {
                lastTrip.apply {
                    tripDuration =
                        formatMillisToHHMMSS(trip.tripEndTimeDurationMs - trip.tripStartTimeDurationMs)
                    tripDistance = (trip.endOdometer - trip.startOdometer)
                    tripDate = convertMillisToDate(trip.tripEndTimeDurationMs)
                    socConsumption = (trip.startSOC - trip.endSOC)
                    tripStartSOC = trip.startSOC
                    tripEndSOC = trip.endSOC
                    batteryEfficiency = batteryEfficiencyKmPerUnit(
                        (trip.startSOC - trip.endSOC),
                        (trip.endOdometer - trip.startOdometer).toInt()
                    )
                    averageSpeedKmph = trip.averageSpeedKmph.toDouble()
                    tripStartOdometer = trip.startOdometer
                    tripEndOdometer = trip.endOdometer
                    startLatitude = trip.startTripLocationLat
                    startLongitude = trip.startTripLocationLon
                    endLatitude = trip.endTripLocationLat
                    endLongitude = trip.endTripLocationLon
                    modeStat = trip.modeDuration
                    tripStartTime = convertMillisToTime(trip.tripStartTimeDurationMs)
                    tripEndTime = convertMillisToTime(trip.tripEndTimeDurationMs)
                }
                monthEndOdometer = trip.endOdometer
            }
            if (index == tripList.lastIndex) {
                monthStartOdometer = trip.startOdometer
            }
            maxSpeed = maxOf(maxSpeed, trip.maximumSpeedKmph)
            totalSocConsumption += (trip.endSOC - trip.startSOC)
            totalSpeedAlerts += trip.speedAlertsCount
            totalAverageSpeed += trip.averageSpeedKmph
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

            if (trip.modeDuration.isNotEmpty()) {
                modeStats.clear()
                modeStats =
                    Json.decodeFromString<MutableMap<EvMode, ModeStats>>(trip.modeDuration)
                ecoDistance += modeStats[EvMode.ECO]?.distanceKm ?: 0.0
                sportsDistance += modeStats[EvMode.SPORTS]?.distanceKm ?: 0.0
                powerDistance += modeStats[EvMode.POWER]?.distanceKm ?: 0.0
            }

        }

        averageSpeed = totalAverageSpeed / tripList.size
        totalMovingDuration += (totalTripDuration - totalIdleDuration)

        val usageLimit = totalDistance.toInt() / tripDaysSet.size
        vehicleUsage = getUsageLevel(usageLimit)

        logHelper.log(type = LogType.INFO, tag = tag, message = "Completed trip data aggregation")

        return MonthlyTripReport(
            monthStartOdometer = monthStartOdometer,
            monthEndOdometer = monthEndOdometer,
            averageSpeedKmph = averageSpeed,
            maximumSpeedKmph = maxSpeed,
            tripCount = totalTrips,
            speedAlertsCount = totalSpeedAlerts,
            idleAlertsCount = totalIdleAlerts,
            harshAlertsCount = totalHarshAlerts,
            regenCount = totalRegenCount,
            tripDistanceKm = String.format("%.2f", totalDistance),
            idleDuration = formatMillisToHHMMSS(totalIdleDuration),
            movingDuration = formatMillisToHHMMSS(totalMovingDuration),
            tripDuration = formatMillisToHHMMSS(totalTripDuration),
            tripDays = tripDaysSet.size,
            ecoDistance = ecoDistance,
            sportsDistance = sportsDistance,
            powerDistance = powerDistance,
            vehicleUsage = vehicleUsage,
            lastTrip = lastTrip
        )
    }

    @SuppressLint("SimpleDateFormat", "DefaultLocale")
    fun generateDayWiseTripReport(tripList: List<TripReportEntity>): DayWiseTripReport {

        if (tripList.isEmpty()) {
            logHelper.log(
                type = LogType.INFO,
                tag = tag,
                message = "Trip list is empty, returning empty report"
            )
            return DayWiseTripReport()
        }

        logHelper.log(
            type = LogType.INFO,
            tag = tag,
            message = "Generating DayWiseTripReport from ${tripList.size} trips"
        )

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val grouped: Map<String, List<TripReportEntity>> = tripList.groupBy { trip ->
            dateFormatter.format(Date(trip.tripEndTimeDurationMs))
        }

        val dayTripDetailsList = grouped.map { (date, trips) ->
            val totalDistance = trips.sumOf { (it.endOdometer - it.startOdometer) }
            val totalSOC = trips.sumOf { (it.endSOC - it.startSOC) }
            val allAlerts = trips.map { it.maximumSpeedKmph.toInt() }

            DayTripDetails(
                tripDate = date,
                tripDistance = "%.1f".format(totalDistance),
                socConsumption = totalSOC,
                speedAlerts = allAlerts
            )
        }

        return DayWiseTripReport(
            days = dayTripDetailsList
        )

    }

    @SuppressLint("SimpleDateFormat")
    private fun convertMillisToDate(millis: Long): String {
        val date = Date(millis)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val dateString = dateFormat.format(date)
        return dateString
    }

    @SuppressLint("SimpleDateFormat")
    private fun convertMillisToTime(millis: Long): String {
        val date = Date(millis)
        val timeFormat = SimpleDateFormat("HH:mm a")
        val timeString = timeFormat.format(date)
        return timeString
    }

    @SuppressLint("DefaultLocale")
    private fun formatMillisToHHMMSS(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun getUsageLevel(usageLimit: Int): Int {
        return when {
            usageLimit <= 0 -> 0
            usageLimit < 30 -> 1
            usageLimit < 50 -> 2
            else -> 3
        }
    }

    private fun batteryEfficiencyKmPerUnit(
        totalConsumedEnergy: Int,
        totalTripDistanceKm: Int
    ): Float {
        if (totalConsumedEnergy == 0 || totalTripDistanceKm == 0) return 0f
        val totalConsumedEnergyWh = (totalConsumedEnergy / 100.0) * NOMINAL_ENERGY
        val totalConsumedEnergyKWh = totalConsumedEnergyWh / 1000.0
        return (totalConsumedEnergyKWh / totalTripDistanceKm).toFloat()
    }

    companion object {

        const val NOMINAL_ENERGY = 2960
        const val MONTHLY_REPORT = "monthly_report"
        const val DAY_WISE_REPORT = "day_wise_report"
        const val CLEAR_ALL_TRIP = "clear_all_trip"

    }

}