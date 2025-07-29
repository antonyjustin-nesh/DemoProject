package com.nlevcluster.sender.util

import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.IgnitionStatus
import com.nlevcluster.sender.report.VehicleState

fun determineVehicleState(canFeed: CanFeed): VehicleState {
    val canSpeed = canFeed.transmission.speed
    val ignition = canFeed.ignitionStatus
    val batteryCurrent = canFeed.battery.current

    return when (ignition) {
        IgnitionStatus.ON -> {
            if (canSpeed > 0) {
                VehicleState.RUNNING
            } else if (canSpeed == 0) {
                if (batteryCurrent > 0) {
                    VehicleState.CHARGING
                } else {
                    VehicleState.IDLE
                }
            } else {
                VehicleState.UNKNOWN
            }
        }

        IgnitionStatus.OFF -> {
            if (canSpeed == 0) {
                VehicleState.STOP
            } else {
                VehicleState.UNKNOWN
            }
        }
    }
}