package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class TripReportByDay(
    val tripDays: Int = 0,
    val days: List<TripsOnDay> = listOf()
)
