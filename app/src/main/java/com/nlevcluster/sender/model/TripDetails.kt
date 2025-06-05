package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class TripDetails(
    var tripDate: String = "",
    var tripStartTime: String = "",
    var tripEndTime: String = "",
    var tripDistance: Double = 0.0,
    var startLatitude: Double = 0.0,
    var startLongitude: Double = 0.0,
    var endLatitude: Double = 0.0,
    var endLongitude: Double = 0.0,
    var tripStartOdometer: Double = 0.0,
    var tripEndOdometer: Double = 0.0,
    var tripDuration: String = "",
    var socConsumption: Int = 0,
    var tripStartSOC: Int = 0,
    var tripEndSOC: Int = 0,
    var averageSpeedKmph: Double = 0.0,
    var modeStat: String = "",
    var batteryEfficiency: Float = 0f,
)