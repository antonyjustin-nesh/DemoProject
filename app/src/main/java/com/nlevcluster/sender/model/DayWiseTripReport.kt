package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable


@Serializable
data class DayWiseTripReport(
    val tripCount: Int = 0,
    val speedAlertsCount: Int = 0,
    val idleAlertsCount: Int = 0,
    val harshAlertsCount: Int = 0,
    val tripDistanceKm: String = "",
    val idleDuration: String = "",
    val movingDuration: String = "",
    val tripDuration: String = "",
    val tripDays: Int = 0,
    val days: List<DayTripDetails> = listOf()
)