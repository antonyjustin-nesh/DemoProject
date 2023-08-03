package com.nlevcluster.sender.model

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val speed: Double,
) {
    override fun toString(): String =
        "$latitude|$longitude|$accuracy|$speed"
}

fun emptyLocationInfo(): LocationInfo = LocationInfo(
    latitude = 0.0,
    longitude = 0.0,
    accuracy = 0.0,
    speed = 0.0,
)
