package com.nlevcluster.sender.model

data class Battery(
    val remainingPercent: Int,
    val range: Int,
    val volt: Double,
    val current: Double,
    val fullCapacity: Double,
    val soc: Double,
)
