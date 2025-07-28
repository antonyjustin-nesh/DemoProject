package com.nlevcluster.sender.model

import com.nlevcluster.sender.report.trip.TripStatus
import kotlinx.serialization.Serializable


@Serializable
data class TripTelemetryData(
    val tripId: Int = 0,
    val averageSpeedKmph: Float = 0f,
    val maximumSpeedKmph: Float = 0f,
    val idleDurationMs: Long = 0,
    val startSOC: Int = 0,
    val endSOC: Int = 0,
    val speedAlertsCount: Int = 0,
    val idleAlertsCount: Int = 0,
    val harshAlertsCount: Int = 0,
    val modeStats: String = "",
    val regenCount: Int = 0,
    val startOdometer: Double = 0.0,
    val endOdometer: Double = 0.0,
    val startTripLocationLat: Double = 0.0,
    val startTripLocationLon: Double = 0.0,
    val endTripLocationLat: Double = 0.0,
    val endTripLocationLon: Double = 0.0,
    val startEnergy: Int = 0,
    val endEnergy: Int = 0,
    val tripStartTimeDurationMs: Long = 0,
    val tripEndTimeDurationMs: Long = 0,
    val tripStatus: TripStatus = TripStatus.NOT_STARTED,
)

@Serializable
data class TripReportPayload(
    val imei: String,
    val trip: TripTelemetryData
)
