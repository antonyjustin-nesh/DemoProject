package com.nlevcluster.sender.trip.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nlevcluster.sender.model.TripReport
import com.nlevcluster.sender.trip.TripStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID


@Entity(tableName = "trip_report")
data class TripReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID")
    val id: Int = 0,
    @ColumnInfo(name = "TRIP_ID")
    val tripID: String = "",
    @ColumnInfo(name = "IDLE_HOURS")
    val idleDurationMs: Long = 0,
    @ColumnInfo(name = "AVERAGE_SPEED")
    val averageSpeedKmph: Float = 0f,
    @ColumnInfo(name = "MAXIMUM_SPEED")
    val maximumSpeedKmph: Float = 0f,
    @ColumnInfo(name = "START_SOC")
    val startSOC: Int = 0,
    @ColumnInfo(name = "END_SOC")
    val endSOC: Int = 0,
    @ColumnInfo(name = "SPEED_ALERTS")
    val speedAlertsCount: Int = 0,
    @ColumnInfo(name = "IDLE_ALERTS")
    val idleAlertsCount: Int = 0,
    @ColumnInfo(name = "HARSH_ALERTS")
    val harshAlertsCount: Int = 0,
    @ColumnInfo(name = "MODE_DURATION")
    val modeDuration: String = "",
    @ColumnInfo(name = "REGEN_COUNT")
    val regenCount: Int = 0,
    @ColumnInfo(name = "START_ODOMETER")
    val startOdometer: Double = 0.0,
    @ColumnInfo(name = "END_ODOMETER")
    val endOdometer: Double = 0.0,
    @ColumnInfo(name = "START_LATITUDE")
    val startTripLocationLat: Double = 0.0,
    @ColumnInfo(name = "START_LONGITUDE")
    val startTripLocationLon: Double = 0.0,
    @ColumnInfo(name = "END_LATITUDE")
    val endTripLocationLat: Double = 0.0,
    @ColumnInfo(name = "END_LONGITUDE")
    val endTripLocationLon: Double = 0.0,
    @ColumnInfo(name = "START_ENERGY")
    val startEnergy: Int = 0,
    @ColumnInfo(name = "END_ENERGY")
    val endEnergy: Int = 0,
    @ColumnInfo(name = "START_TIME")
    val tripStartTimeDurationMs: Long = 0,
    @ColumnInfo(name = "END_TIME")
    val tripEndTimeDurationMs: Long = 0,
    @ColumnInfo(name = "TRIP_STATUS")
    val tripStatus: TripStatus = TripStatus.NOT_STARTED
)


fun TripReportEntity.asExternalModel() = TripReport(
    tripId = tripID,
    idleDurationMs = idleDurationMs,
    averageSpeedKmph = averageSpeedKmph,
    maximumSpeedKmph = maximumSpeedKmph,
    startSoc = startSOC,
    endSoc = endSOC,
    speedAlertsCount = speedAlertsCount,
    idleAlertsCount = idleAlertsCount,
    harshAlertsCount = harshAlertsCount,
    modeDuration = modeDuration,
    regenCount = regenCount,
    startOdometer = startOdometer,
    endOdometer = endOdometer,
    startEnergy = startEnergy,
    endEnergy = endEnergy,
    startTripLocationLat = startTripLocationLat,
    startTripLocationLon = startTripLocationLon,
    endTripLocationLat = endTripLocationLat,
    endTripLocationLon = endTripLocationLon,
    tripStartTimeDurationMs = tripStartTimeDurationMs,
    tripEndTimeDurationMs = tripEndTimeDurationMs,
    tripCount = 0,
    tripDistanceKm = 0.0,
    tripDurationMs = tripEndTimeDurationMs - tripStartTimeDurationMs,
    movingDurationMs = 0,
    tripDays = 0,
)