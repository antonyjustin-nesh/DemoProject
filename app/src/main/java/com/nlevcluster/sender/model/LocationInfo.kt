package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationInfo(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Double = 0.0,
    val speed: Float = 0f,
    val altitude: Float = 0f,
)
