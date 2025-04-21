package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class Odometer(
    var totalKm: Double = 0.0,
    var currentRideInKm: Double = 0.0,
)
