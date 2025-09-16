package com.nlevcluster.sender.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TripDataInfoWrapper(
    @SerialName("500")
    val tripDataInfo: TripDataInfo,
)

@Serializable
data class TripDataInfo(
    @SerialName("501")
    val imei: String,
    @SerialName("510")
    val trip: Trip,
)

@Serializable
data class Trip(
    @SerialName("511")
    val tripId: Int,

    @SerialName("512")
    val tripStartTime: Long,

    @SerialName("513")
    val tripEndTime: Long,

    @SerialName("514")
    val idleDuration: Long,

    @SerialName("515")
    val movingDuration: Long,

    @SerialName("516")
    val startLatitude: Double,

    @SerialName("517")
    val startLongitude: Double,

    @SerialName("518")
    val endLatitude: Double,

    @SerialName("519")
    val endLongitude: Double,

    @SerialName("520")
    val startOdometer: Double,

    @SerialName("521")
    val endOdometer: Double,

    @SerialName("522")
    val startSoc: Int,

    @SerialName("523")
    val endSoc: Int,

    @SerialName("524")
    val avgSpeed: Int,

    @SerialName("525")
    val maxSpeed: Int,

    @SerialName("526")
    val driveModeStats: String,

    @SerialName("527")
    val energyConsumedWh: Double,

    @SerialName("528")
    val energyConsumedKWh: Double,

    @SerialName("529")
    val energyEfficiencyWh: Double,

    @SerialName("530")
    val energyEfficiencyKWh: Float,

    @SerialName("531")
    val regenCount: Int
)

