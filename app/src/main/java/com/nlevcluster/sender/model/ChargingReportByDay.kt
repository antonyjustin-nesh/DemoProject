package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class ChargingReportByDay(
    val chargingSessionDays: Int = 0,
    val days: List<ChargingSessionsOnDay> = listOf()
)
