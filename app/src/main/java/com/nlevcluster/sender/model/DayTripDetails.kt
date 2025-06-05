package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable


@Serializable
data class DayTripDetails(
    val tripDate: String = "",
    val tripDistance: String = "",
    val socConsumption: Int = 0,
    val speedAlerts: List<Int> = listOf(),
)