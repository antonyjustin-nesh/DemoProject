package com.nlevcluster.sender.report.trip

import com.nlevcluster.sender.report.VehicleState
import kotlinx.serialization.Serializable

@Serializable
data class ChargingData(
    val vehicleState: VehicleState,
    val timestamp: Long,
    val soc: Int
)