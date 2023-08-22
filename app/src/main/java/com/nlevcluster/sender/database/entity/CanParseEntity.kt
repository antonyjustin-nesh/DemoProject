package com.nlevcluster.sender.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.Ignition
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * entity class for parsed can data
 */
@Entity(tableName = "can_parse")
data class CanParseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val soc: Double,
    val range: Int,
    val speed: Int,
    val odometer: Double,
    val evMode: String,
    val rpm: Double,
    val ignition: String,
    val ignitionStatus: String,
    val vehicleStatus: String,
    val batteryVoltage: Double,
    val batteryCurrent: Double,
    val batteryCapacityRemaining: Double,
    val batteryTotalCapacity: Double,
    @ColumnInfo(name = "gps_location")
    val gpsLocation: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val altitude: Double,
    val gpsSpeed: Double,
    @ColumnInfo(name = "created_date")
    val createdDate: Long,
)

fun CanFeed.asEntity() = CanParseEntity(
    soc = battery.remainingPercent.toDouble(),
    rpm = transmission.rpm,
    range = battery.range,
    speed = transmission.speed,
    odometer = odometer.totalKm,
    evMode = evMode.mode,
    gpsLocation = gps,
    latitude = currentLocation?.latitude ?: 0.0,
    longitude = currentLocation?.longitude ?: 0.0,
    accuracy = currentLocation?.accuracy ?: 0.0,
    gpsSpeed = currentLocation?.speed ?: 0.0,
    createdDate = createdDate,
    altitude = currentLocation?.altitude ?: 0.0,
    ignitionStatus = ignitionStatus.name,
    vehicleStatus = ignition.name,
    batteryCurrent = battery.current,
    batteryVoltage = battery.volt,
    batteryCapacityRemaining = battery.remainingPercent.toDouble(),
    batteryTotalCapacity = battery.fullCapacity,
    ignition = ignition.name
)

