package com.nlevcluster.sender.model


data class MonthlyTripReport(
    val averageSpeedKmph: Float = 0f,
    val maximumSpeedKmph: Float = 0f,
    val tripCount: Int = 0,
    val totalSocConsumption: Int = 0,
    val speedAlertsCount: Int = 0,
    val idleAlertsCount: Int = 0,
    val harshAlertsCount: Int = 0,
    val regenCount: Int = 0,
    val tripDistanceKm: String = "",
    val idleDurationMs: Long = 0,
    val movingDurationMs: Long = 0,
    val tripDuration: String = "",
    val tripDays: Int = 0,
)