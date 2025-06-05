package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class ModeStats(
    var durationMs: Long = 0L,
    var distanceKm: Double = 0.0
)


