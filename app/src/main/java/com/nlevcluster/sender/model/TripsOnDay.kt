package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable


@Serializable
data class TripsOnDay(
    val tripCounts : Int = 0,
    val date: String = "",
    val trips: List<TripRecord> = listOf()
)