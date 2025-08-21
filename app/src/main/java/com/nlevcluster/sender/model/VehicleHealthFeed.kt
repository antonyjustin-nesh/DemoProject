package com.nlevcluster.sender.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VehicleHealth(
    @SerialName("401")
    val imei: String,
    @SerialName("410")
    val softwareHealth: SoftwareHealth
)

@Serializable
data class SoftwareHealth(
    @SerialName("411")
    val osVersion: String,

    @SerialName("412")
    val sotaVersion: String,

    @SerialName("413")
    val baseBandVersion: String,

    @SerialName("414")
    val kernelVersion: String,

    @SerialName("415")
    val firmwareVersion: String,

    @SerialName("416")
    val appsVersion: List<AppVersion>
)

@Serializable
data class AppVersion(
    val name: String,
    val version: String
)

@Serializable
data class VehicleHealthWrapper(
    @SerialName("400") val vehicleHealth: VehicleHealth
)
