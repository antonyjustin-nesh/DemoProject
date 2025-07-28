package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable


@Serializable
data class ChargingSessionsOnDay(
    val chargingSessionsCounts : Int = 0,
    val date: String = "",
    val chargingSessions: List<ChargingSession> = listOf()
)