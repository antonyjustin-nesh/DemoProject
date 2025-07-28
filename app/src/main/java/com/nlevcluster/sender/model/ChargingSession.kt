package com.nlevcluster.sender.model

import com.nlevcluster.sender.report.charge.ChargerType
import kotlinx.serialization.Serializable

@Serializable
data class ChargingSession(
    var chargingSessionId: Int = 0,
    val chargingStartTimeDurationMs: Long = 0L,
    val chargingEndTimeDurationMs: Long = 0L,
    val startChargingLocationLat: Double = 0.0,
    val startChargingLocationLon: Double = 0.0,
    val endChargingLocationLat: Double = 0.0,
    val endChargingLocationLon: Double = 0.0,
    val startSOC: Int = 0,
    val endSOC: Int = 0,
    val chargerType: ChargerType = ChargerType.AC_L1,
    val chargeRate: Double = 0.0,
    val chargerID: String = "",
    val isAbnormalTermination: Boolean = false,
    val batteryCurrent: Double = 0.0,
    val batteryVoltage: Double = 0.0,
    val averageCurrent: Double = 0.0,
    val averageVoltage: Double = 0.0,
    val maxBatteryCurrent: Double = 0.0,
    val maxBatteryVoltage: Double = 0.0,
    val batteryTemperature: Float = 0f,
    val maxBatteryTemperature: Float = 0f,
    val minBatteryTemperature: Float = 0f,
    val batteryTemperatureRise: Float = 0f,
    val averageBatteryTemperature: Float = 0f,
    var energyDeliveredInWh: Double = 0.0,
    var energyDeliveredInKWh: Double = 0.0,
)