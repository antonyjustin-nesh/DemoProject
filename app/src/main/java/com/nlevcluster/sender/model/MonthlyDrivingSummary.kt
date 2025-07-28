package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable


@Serializable
data class MonthlyDrivingSummary(
    val monthStartOdometer: Double = 0.0,
    val monthEndOdometer: Double = 0.0,
    val averageSpeedKmph: Float = 0f,
    val maximumSpeedKmph: Float = 0f,
    val tripCount: Int = 0,
    val speedAlertsCount: Int = 0,
    val idleAlertsCount: Int = 0,
    val harshAlertsCount: Int = 0,
    val regenCount: Int = 0,
    val tripDistanceKm: Double = 0.0,
    val idleDuration: Long = 0L,
    val movingDuration: Long = 0L,
    val tripDuration: Long = 0L,
    val ecoDistance: Double = 0.0,
    val sportsDistance: Double = 0.0,
    val powerDistance: Double = 0.0,
    val tripDays: Int = 0,
    val vehicleUsage: Int = 0,
    val lastTrip: TripRecord = TripRecord()
)