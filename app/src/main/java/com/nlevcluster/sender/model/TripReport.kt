package com.nlevcluster.sender.model

import java.util.UUID


data class TripReport(
    val id: Int = 0,
    val tripId: String = UUID.randomUUID().toString(),
    val averageSpeedKmph: Float = 0f,
    val maximumSpeedKmph: Float = 0f,
    val tripCount: Int = 0,
    val startSoc: Int = 0,
    val endSoc: Int = 0,
    val speedAlertsCount: Int = 0,
    val idleAlertsCount: Int = 0,
    val harshAlertsCount: Int = 0,
    val modeDuration: String = "",
    val regenCount: Int = 0,
    val startOdometer: Double = 0.0,
    val endOdometer: Double = 0.0,
    val tripDistanceKm: Double = 0.0,
    val startTripLocationLat: Double = 0.0,
    val startTripLocationLon: Double = 0.0,
    val endTripLocationLat: Double = 0.0,
    val endTripLocationLon: Double = 0.0,
    val startEnergy: Int = 0,
    val endEnergy: Int = 0,
    val idleDurationMs: Long = 0,
    val movingDurationMs: Long = 0,
    val tripDurationMs: Long = 0,
    val tripDays: Int = 0,
    val tripStartTimeDurationMs: Long = 0,
    val tripEndTimeDurationMs: Long = 0,
)