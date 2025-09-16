package com.nlevcluster.sender.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChargingSessionInfoWrapper(
    @SerialName("600") val chargingInfo: ChargingSessionInfo
)

@Serializable
data class ChargingSessionInfo(
    @SerialName("601")
    val imei: String,

    @SerialName("610")
    val chargingSession: ChargingSessionData
)

@Serializable
data class ChargingSessionData(
    @SerialName("611")
    val sessionId: Int,

    @SerialName("612")
    val chargingStartTime: Long,

    @SerialName("613")
    val chargingEndTime: Long,

    @SerialName("614")
    val startLatitude: Double,

    @SerialName("615")
    val startLongitude: Double,

    @SerialName("616")
    val endLatitude: Double,

    @SerialName("617")
    val endLongitude: Double,

    @SerialName("618")
    val startSoc: Int,

    @SerialName("619")
    val endSoc: Int,

    @SerialName("620")
    val chargerType: String,

    @SerialName("621")
    val chargeRate: Double,

    @SerialName("622")
    val chargerId: String,

    @SerialName("623")
    val isAbnormalTermination: Boolean,

    @SerialName("624")
    val batteryCurrent: Double,

    @SerialName("625")
    val batteryVoltage: Double,

    @SerialName("626")
    val maxBatteryCurrent: Double,

    @SerialName("627")
    val maxBatteryVoltage: Double,

    @SerialName("628")
    val averageCurrent: Double,

    @SerialName("629")
    val averageVoltage: Double,

    @SerialName("630")
    val batteryTemp: Float,

    @SerialName("631")
    val maxBatteryTemp: Float,

    @SerialName("632")
    val minBatteryTemp: Float,

    @SerialName("633")
    val batteryTempRise: Float,

    @SerialName("634")
    val avgBatteryTemp: Float,

    @SerialName("635")
    val deliveredEnergyWh: Double,

    @SerialName("636")
    val deliveredEnergyKWh: Float
)
