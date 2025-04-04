package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class Transmission(
    val speed: Int = 0,
    val averageSpeed: Double = 0.0,
    var rpm: Double = 0.0,
)