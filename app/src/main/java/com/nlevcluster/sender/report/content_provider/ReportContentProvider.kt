package com.nlevcluster.sender.report.content_provider

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.nlevcluster.sender.di.ProviderEntryPoint
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.ModeStats
import com.nlevcluster.sender.model.MonthlyChargingSummary
import com.nlevcluster.sender.model.MonthlyDrivingSummary
import com.nlevcluster.sender.model.TripRecord
import com.nlevcluster.sender.model.TripReportByDay
import com.nlevcluster.sender.model.TripsOnDay
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.nllogger.NLLoggerLogHelper
import com.nlevcluster.sender.report.alerts.AlertDao
import com.nlevcluster.sender.report.alerts.convertToAlerts
import com.nlevcluster.sender.report.charge.db.ChargingReportDao
import com.nlevcluster.sender.report.charge.db.ChargingSessionEntity
import com.nlevcluster.sender.report.trip.db.TripReportDao
import com.nlevcluster.sender.report.trip.db.TripReportEntity
import com.nlevcluster.sender.report.trip.mapper.toTripRecord
import dagger.hilt.android.EntryPointAccessors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class ReportContentProvider : ContentProvider() {

    private val tag = "ReportContentProvider"

    private lateinit var tripDao: TripReportDao
    private lateinit var chargingReportDao: ChargingReportDao
    private lateinit var alertDao: AlertDao
    lateinit var logHelper: LogHelper

    private var modeStats: MutableMap<EvMode, ModeStats> = enumValues<EvMode>()
        .associateWith { ModeStats() }
        .toMutableMap()

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(): Boolean {

        val context = context?.applicationContext ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ProviderEntryPoint::class.java
        )
        tripDao = entryPoint.tripDao()
        chargingReportDao = entryPoint.chargingDao()
        alertDao = entryPoint.alertDao()
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
            MONTHLY_TRIP_REPORT -> {

                val startDate = uri.getQueryParameter("startDate")
                val endDate = uri.getQueryParameter("endDate")
                val cursor = MatrixCursor(arrayOf(MONTHLY_TRIP_REPORT))

                logHelper.log(
                    type = LogType.INFO,
                    tag = tag,
                    message = "Query received for TRIP_MONTHLY_REPORT with startDate: $startDate endDate: $endDate"
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

                    val monthlyTripReport = generateMonthlyDrivingSummary(tripList)
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

            DAY_WISE_TRIP_REPORT -> {

                val startDate = uri.getQueryParameter("startDate")
                val endDate = uri.getQueryParameter("endDate")
                val cursor = MatrixCursor(arrayOf(DAY_WISE_TRIP_REPORT))

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

            MONTHLY_CHARGING_REPORT -> {

                val startDate = uri.getQueryParameter("startDate")
                val endDate = uri.getQueryParameter("endDate")
                val cursor = MatrixCursor(arrayOf(MONTHLY_CHARGING_REPORT))

                logHelper.log(
                    type = LogType.INFO,
                    tag = tag,
                    message = "Query received for CHARGING_MONTHLY_REPORT with startDate: $startDate endDate: $endDate"
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

                    val chargingSessionList = chargingReportDao.getChargingSessionsByEndTimeRange(
                        startTimestamp,
                        endTimestamp
                    )

                    val monthlyChargingSummary = generateMonthlyChargingSummary(chargingSessionList)
                    val monthlyChargingReportDetails = json.encodeToString(monthlyChargingSummary)
                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "monthlyChargingReportDetails - $monthlyChargingReportDetails"
                    )

                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "Generated MonthlyChargingReport JSON"
                    )

                    cursor.addRow(arrayOf(monthlyChargingReportDetails))

                } catch (e: Exception) {
                    logHelper.log(
                        type = LogType.ERROR,
                        tag = tag,
                        message =
                        "Exception in MONTHLY_CHARGING_REPORT query: ${e.message}"
                    )
                    cursor.addRow(arrayOf(null))
                }
                cursor
            }

            FETCH_ALERTS_BY_DATE -> {
                val startDate = uri.getQueryParameter("startDate")
                val endDate = uri.getQueryParameter("endDate")
                val cursor = MatrixCursor(arrayOf(FETCH_ALERTS_BY_DATE))

                logHelper.log(
                    type = LogType.INFO,
                    tag = tag,
                    message = "Query received for FETCH_ALERTS_BY_DATE with startDate: $startDate endDate: $endDate"
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

                    val alertList = alertDao.getAlertsByTimeRange(startTimestamp, endTimestamp)
                    val montlyAlerts = json.encodeToString(convertToAlerts(alertList))

                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "montlyAlerts - $montlyAlerts"
                    )

                    logHelper.log(
                        type = LogType.INFO,
                        tag = tag,
                        message = "Generated montlyAlerts JSON"
                    )

                    cursor.addRow(arrayOf(montlyAlerts))

                } catch (e: Exception) {
                    logHelper.log(
                        type = LogType.ERROR,
                        tag = tag,
                        message =
                        "Exception in FETCH_ALERTS_BY_DATE query: ${e.message}"
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
            CLEAR_ALL_TRIPS -> {
                logHelper.log(type = LogType.INFO, tag = tag, message = "Deleting all trips")
                tripDao.deleteAllTrips()
                context?.contentResolver?.notifyChange(uri, null)
                return 1
            }

            CLEAR_ALL_ALERTS -> {
                logHelper.log(type = LogType.INFO, tag = tag, message = "Deleting all alerts")
                tripDao.deleteAllAlerts()
                context?.contentResolver?.notifyChange(uri, null)
                return 1
            }

            CLEAR_ALL_CHARGING_SESSIONS -> {
                logHelper.log(type = LogType.INFO, tag = tag, message = "Deleting all charging sessions")
                chargingReportDao.deleteAllChargingSessions()
                context?.contentResolver?.notifyChange(uri, null)
                return 1
            }

            else -> {
                logHelper.log(type = LogType.INFO, tag = tag, message = "Unknown delete URI: $uri")
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
        logHelper.log(type = LogType.INFO, tag = tag, message = "Update called for URI: $uri")
        return 0
    }

    @SuppressLint("SimpleDateFormat", "DefaultLocale")
    fun generateMonthlyDrivingSummary(tripList: List<TripReportEntity>): MonthlyDrivingSummary {
        if (tripList.isEmpty()) {
            logHelper.log(
                type = LogType.INFO,
                tag = tag,
                message = "Trip list is empty, returning empty report"
            )
            return MonthlyDrivingSummary()
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

        //var totalSpeedAlerts = 0
        //var totalIdleAlerts = 0
        //var totalHarshAlerts = 0
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
        var lastTrip = TripRecord()

        var monthStartOdometer = 0.0
        var monthEndOdometer = 0.0

        var vehicleUsage = 0

        tripList.forEachIndexed { index, trip ->
            if (index == 0) {
                lastTrip = trip.toTripRecord()
                monthEndOdometer = trip.endOdometer
            }

            if (index == tripList.lastIndex) {
                monthStartOdometer = trip.startOdometer
            }
            maxSpeed = maxOf(maxSpeed, trip.maximumSpeedKmph)
            totalSocConsumption += (trip.endSOC - trip.startSOC)
            //totalSpeedAlerts += trip.speedAlertsCount
            totalAverageSpeed += trip.averageSpeedKmph
            //totalIdleAlerts += trip.idleAlertsCount
            //totalHarshAlerts += trip.harshAlertsCount
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

            if (trip.modeStats.isNotEmpty()) {
                modeStats.clear()
                modeStats =
                    Json.decodeFromString<MutableMap<EvMode, ModeStats>>(trip.modeStats)
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

        return MonthlyDrivingSummary(
            monthStartOdometer = monthStartOdometer,
            monthEndOdometer = monthEndOdometer,
            averageSpeedKmph = averageSpeed,
            maximumSpeedKmph = maxSpeed,
            tripCount = totalTrips,
            regenCount = totalRegenCount,
            tripDistanceKm = totalDistance,
            idleDuration = totalIdleDuration,
            movingDuration = totalMovingDuration,
            tripDuration = totalTripDuration,
            tripDays = tripDaysSet.size,
            ecoDistance = ecoDistance,
            sportsDistance = sportsDistance,
            powerDistance = powerDistance,
            vehicleUsage = vehicleUsage,
            lastTrip = lastTrip
        )
    }

    @SuppressLint("SimpleDateFormat", "DefaultLocale")
    fun generateDayWiseTripReport(tripList: List<TripReportEntity>): TripReportByDay {

        if (tripList.isEmpty()) {
            logHelper.log(
                type = LogType.INFO,
                tag = tag,
                message = "Trip list is empty, returning empty report"
            )
            return TripReportByDay()
        }

        logHelper.log(
            type = LogType.INFO,
            tag = tag,
            message = "Generating DayWiseTripReport from ${tripList.size} trips"
        )

        val dateFormatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val grouped: Map<String, List<TripReportEntity>> = tripList.groupBy { trip ->
            dateFormatter.format(Date(trip.tripEndTimeDurationMs))
        }

        val dayTripDetailsList = grouped.map { (date, trips) ->
            TripsOnDay(
                tripCounts = trips.size,
                date = date,
                trips = trips.map {
                    it.toTripRecord()
                }
            )
        }

        return TripReportByDay(
            tripDays = grouped.size,
            days = dayTripDetailsList
        )

    }

    private fun generateMonthlyChargingSummary(chargingSessionList: List<ChargingSessionEntity>): MonthlyChargingSummary {
        if (chargingSessionList.isEmpty()) {
            logHelper.log(
                type = LogType.INFO,
                tag = tag,
                message = "Charging Session list is empty, returning empty report"
            )
            return MonthlyChargingSummary()
        }

        logHelper.log(
            type = LogType.INFO,
            tag = tag,
            message = "Generating MonthlyTripReport from ${chargingSessionList.size} chargingSessions"
        )

        var totalChargingHours = 0L
        var totalEnergyDeliveredWh = 0.0

        chargingSessionList.forEachIndexed { _, chargingSession ->
            totalChargingHours += (chargingSession.chargingEndTimeDurationMs - chargingSession.chargingStartTimeDurationMs)
            totalEnergyDeliveredWh += calculateEnergyConsumedInWh((chargingSession.endSOC - chargingSession.startSOC))
        }

        logHelper.log(type = LogType.INFO, tag = tag, message = "Completed trip data aggregation")

        return MonthlyChargingSummary(
            totalChargingHours = totalChargingHours,
            totalChargingCount = chargingSessionList.size,
            totalEnergyDeliveredWh = totalEnergyDeliveredWh,
            totalEnergyDeliveredKWh = totalEnergyDeliveredWh / 1000.0,
            batteryTemperature = if(chargingSessionList.isNotEmpty()) chargingSessionList[0].batteryTemperature else 0f
        )
    }

    private fun calculateEnergyConsumedInWh(
        totalConsumedPercent: Int
    ): Double {
        if (totalConsumedPercent == 0) return 0.0
        return (totalConsumedPercent / 100.0) * NOMINAL_ENERGY
    }

    private fun getUsageLevel(usageLimit: Int): Int {
        return when {
            (usageLimit in 1..19)-> 1
            (usageLimit in 20..29)-> 2
            usageLimit > 30-> 3
            else -> 0
        }
    }

    companion object {

        const val NOMINAL_ENERGY = 2960
        const val MONTHLY_TRIP_REPORT = "monthly_trip_report"
        const val DAY_WISE_TRIP_REPORT = "day_wise_trip_report"
        const val CLEAR_ALL_TRIPS = "clear_all_trips"
        const val CLEAR_ALL_ALERTS = "clear_all_alerts"
        const val MONTHLY_CHARGING_REPORT = "monthly_charging_report"
        const val DAY_WISE_CHARGING_REPORT = "day_wise_charging_report"
        const val CLEAR_ALL_CHARGING_SESSIONS = "clear_all_charging_sessions"
        const val FETCH_ALERTS_BY_DATE = "fetch_alerts_by_date"

    }

}