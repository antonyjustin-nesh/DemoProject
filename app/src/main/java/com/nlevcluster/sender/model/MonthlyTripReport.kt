package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable


@Serializable
data class MonthlyTripReport(
    val monthStartOdometer: Double = 0.0,
    val monthEndOdometer: Double = 0.0,
    val averageSpeedKmph: Float = 0f,
    val maximumSpeedKmph: Float = 0f,
    val tripCount: Int = 0,
    val speedAlertsCount: Int = 0,
    val idleAlertsCount: Int = 0,
    val harshAlertsCount: Int = 0,
    val regenCount: Int = 0,
    val tripDistanceKm: String = "",
    val idleDuration: String = "",
    val movingDuration: String = "",
    val tripDuration: String = "",
    val ecoDistance: Int = 0,
    val sportsDistance: Int = 0,
    val powerDistance: Int = 0,
    val tripDays: Int = 0,
    val vehicleUsage: Int = 0,
    val lastTrip: TripDetails = TripDetails()
)