package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class Battery(
    val remainingPercent: Int = 0,
    val range: Int = 0,
    val volt: Double = 0.0,
    val current: Double = 0.0,
    val fullCapacity: Double = 0.0,
    val soc: Double = 0.0,
)