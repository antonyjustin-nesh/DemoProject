package com.nlevcluster.sender.trip.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nlevcluster.sender.model.TripReport
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter


@Entity(tableName = "trip_report")
data class TripReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "TRIP_ID")
    val tripId: Int = 0,
    @ColumnInfo(name = "DISTANCE")
    val distance: Double,
    @ColumnInfo(name = "DURATION")
    val duration: Long,
    @ColumnInfo(name = "AVERAGE_SPEED")
    val averageSpeed: Int,
    @ColumnInfo(name = "MAXIMUM_SPEED")
    val maximumSpeed: Int,
    @ColumnInfo(name = "SPEED_ALERTS")
    val speedAlerts: Int,
    @ColumnInfo(name = "IDLE_ALERTS")
    val idleAlerts: Int,
    @ColumnInfo(name = "HARSH_ALERTS")
    val harshAlerts: Int,
    @ColumnInfo(name = "TRIP_START_LOCATION")
    val tripStartLocation: String,
    @ColumnInfo(name = "TRIP_END_LOCATION")
    val tripEndLocation: String,
    @ColumnInfo(name = "TRIP_START_TIME")
    val tripStartTime: Long,
    @ColumnInfo(name = "TRIP_END_TIME")
    val tripEndTime: Long,
)


fun TripReportEntity.asExternalModel() = TripReport(
    tripId = tripId,
    distance = distance,
    duration = duration,
    averageSpeed = averageSpeed,
    maximumSpeed = maximumSpeed,
    tripStartLocation = tripStartLocation,
    tripEndLocation = tripEndLocation,
    tripStartTime = Instant.fromEpochMilliseconds(tripStartTime)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss")),
    tripEndTime = Instant.fromEpochMilliseconds(tripEndTime)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss")),
)