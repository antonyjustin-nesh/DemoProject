package com.nlevcluster.sender.model


data class TripReport(
    val tripId: Int,
    val distance: Double,
    val duration: Long,
    val averageSpeed: Int,
    val maximumSpeed: Int,
    val tripStartTime: String,
    val tripEndTime: String,
    val tripStartLocation: String,
    val tripEndLocation: String
)