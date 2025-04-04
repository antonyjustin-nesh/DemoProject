package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class Battery(
    var remainingPercent: Int = 0,
    var range: Int = 0,
    var volt: Double = 0.0,
    var current: Double = 0.0,
    val fullCapacity: Double = 0.0,
    val soc: Double = 0.0,
)