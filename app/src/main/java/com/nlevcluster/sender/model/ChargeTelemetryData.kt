package com.nlevcluster.sender.model

import com.nlevcluster.sender.report.charge.ChargerType
import kotlinx.serialization.Serializable


@Serializable
data class ChargeTelemetryData(
    val chargingSessionId: Int = 0,
    val startSOC: Int = 0,
    val endSOC: Int = 0,
    val startChargingLocationLat: Double = 0.0,
    val startChargingLocationLon: Double = 0.0,
    val endChargingLocationLat: Double = 0.0,
    val endChargingLocationLon: Double = 0.0,
    val chargingStartTimeDurationMs: Long = 0,
    val chargingEndTimeDurationMs: Long = 0,
    val chargingType: ChargerType = ChargerType.AC_L1,
    val chargerID: String = "",
    val isAbnormalTermination: Boolean = false,
    val averageVoltage: Double = 0.0,
    val maxBatteryCurrent: Double = 0.0,
    val maxBatteryTemperature: Float = 0f,
    val batteryTemperatureRise: Float = 0f,
    val averageBatteryTemperature: Float = 0f,
    val isSynced: Int = 0,
)


@Serializable
data class ChargingSessionPayload(
    val imei: String,
    val chargingSession: ChargeTelemetryData
)
