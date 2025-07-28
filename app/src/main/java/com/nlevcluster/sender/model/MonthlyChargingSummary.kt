package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable


@Serializable
data class MonthlyChargingSummary(
    val totalChargingHours: Long = 0L,
    val totalEnergyDeliveredWh: Double = 0.0,
    val totalEnergyDeliveredKWh: Double = 0.0,
    val totalChargingCount: Int = 0,
    val maxBatteryCurrent: Double = 0.0,
    val minBatteryCurrent: Double = 0.0,
    val averageBatteryCurrent: Double = 0.0,
    val maxBatteryVoltage: Double = 0.0,
    val minBatteryVoltage: Double = 0.0,
    val averageBatteryVoltage: Double = 0.0,
    val maxBatteryTemperature: Float = 0F,
    val minBatteryTemperature: Float = 0F,
    val batteryTemperatureRise: Float = 0F,
)