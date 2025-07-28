package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class Odometer(
    val totalKm: Double = 0.0,
    val currentRideInKm: Double = 0.0,
)
