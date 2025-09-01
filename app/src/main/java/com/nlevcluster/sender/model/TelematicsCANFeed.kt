package com.nlevcluster.sender.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VehicleInfo(
    @SerialName("02")
    val imei: String,
    @SerialName("03")
    val vehicleIdentificationNumber: String,
    @SerialName("04")
    val batteryIdentificationNumber: String,
    @SerialName("05")
    val motorIdentificationNumber: String,
    @SerialName("06")
    val bmsHardwareVersion: String,
    @SerialName("07")
    val bmsSoftwareVersion: String,
    @SerialName("08")
    val mcuHardwareVersion: String,
    @SerialName("09")
    val mcuSoftwareVersion: String,
)

@Serializable
data class VehicleInfoWrapper(
    @SerialName("01") val data: VehicleInfo
)

@Serializable
data class VehicleCANFeed(
    @SerialName("51")
    val epochTimeStamp: String,
    @SerialName("52")
    val imei: String,
    @SerialName("53")
    val ignition: Int,
    @SerialName("54")
    val vehicleStatus: String,
    @SerialName("100")
    val gpsFeed: GPSFeed,
    @SerialName("150")
    val icFeed: ICFeed,
    @SerialName("200")
    val batteryFeed: BatteryFeed,
    @SerialName("300")
    val motorFeed: MotorFeed,
)

@Serializable
data class GPSFeed(
    @SerialName("101")
    val latitude: Double,
    @SerialName("102")
    val longitude: Double,
    @SerialName("103")
    val altitude: Double,
    @SerialName("104")
    val odometer: Double,
    @SerialName("105")
    val gpsSpeed: Float,
)

@Serializable
data class ICFeed(
    @SerialName("151")
    val odometer: Double,
    @SerialName("152")
    val speed: Float,
)

@Serializable
data class BatteryFeed(
    @SerialName("201")
    val current: Float,
    @SerialName("202")
    val voltage: Float,
    @SerialName("203")
    val soc: Float,
    @SerialName("204")
    val status: String,
    @SerialName("205")
    val remainingCapacity: Float,
    @SerialName("206")
    val fullCapacity: Float,
    @SerialName("207")
    val energy: Float,
    @SerialName("208")
    val cycleCount: Int,
)

@Serializable
data class MotorFeed(
    @SerialName("301")
    val current: Float,
    @SerialName("302")
    val voltage: Float,
    @SerialName("303")
    val speed: Double,
    @SerialName("304")
    val odometer: Double,
    @SerialName("305")
    val rpm: Double,
    @SerialName("306")
    val motorTemperature: Float,
    @SerialName("307")
    val gearPosition: Int,
    @SerialName("308")
    val breakPosition: String,
    @SerialName("309")
    val controllerTemperature: Float,
    @SerialName("310")
    val torque: Float,
    @SerialName("311")
    val direction: Int,
    @SerialName("312")
    val fault: Int,
)

@Serializable
data class VehicleFeedWrapper(
    @SerialName("50") val data: VehicleCANFeed
)

















