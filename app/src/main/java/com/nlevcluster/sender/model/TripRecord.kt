package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class TripRecord(
    val tripId: Int = 0,
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val idleDurationMillis: Long = 0L,
    val movingDurationMillis: Long = 0L,
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    val startOdometerKm: Double = 0.0,
    val endOdometerKm: Double = 0.0,
    val startSOCPercent: Int = 0,
    val endSOCPercent: Int = 0,
    val avgSpeedKmph: Float = 0f,
    val maxSpeedKmph: Float = 0f,
    val driveModeStats: String = "",
    val totalAlertCount: Int = 0,
    val energyConsumedInWh: Double = 0.0,
    val energyConsumedInKWh: Double = 0.0,
    val energyEfficiencyWhPerKm: Double = 0.0,
    val energyEfficiencyUnitsPerKm: Float = 0f,
    val regenerationEventCount: Int = 0,
)