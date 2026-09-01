package com.nlevcluster.sender

import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.toTelematicsValue
import com.nlevcluster.sender.report.VehicleState
import com.nlevcluster.sender.util.determineVehicleState
import live.nesh.cluster.telematics.model.BatteryFeed
import live.nesh.cluster.telematics.model.CoreVehicleFeed
import live.nesh.cluster.telematics.model.GpsFeed
import live.nesh.cluster.telematics.model.IcFeed
import live.nesh.cluster.telematics.model.MotorFeed
import live.nesh.cluster.telematics.model.VehicleFeedRoot

fun CanFeed.toVehicleFeedRoot(
    imei: String = "",
    epoch: Long = createdDate
): VehicleFeedRoot {

    val vehicleState = processedVehicleState()
    return VehicleFeedRoot(
        coreVehicleFeed = CoreVehicleFeed(
            epoch = epoch,
            imei = imei,
            ignition = ignitionStatus.toTelematicsValue(),
            vehicleStatus = vehicleState,
            mainPowerStatus = ignitionStatus.toTelematicsValue(),
            packetStatus = 0,
            externalBatteryVoltage = battery.volt.toFloat()
        ),
        gpsFeed = currentLocation?.let { location ->
            GpsFeed(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude.toDouble(),
                odometer = odometer.totalKm,
                speed = location.speed.toDouble(),
                gpsDataValidityStatus = 0,
                heading = 0,
                numberOfSatellites = 0,
            )
        },
        icFeed = IcFeed(
            odometer = odometer.totalKm,
            speed = transmission.speed.toDouble()
        ),
        batteryFeed = BatteryFeed(
            current = battery.current,
            voltage = battery.volt,
            soc = battery.remainingPercent.toDouble(),
            status = if (vehicleState == "CHARGING") "CHARGING" else "DISCHARGING",
            remainingCapacity = battery.fullCapacity * battery.remainingPercent / 100.0,
            fullCapacity = battery.fullCapacity,
            energy = 0.0,
            cycleCount = 0,
        ),
        motorFeed = MotorFeed(
            current = 0.0,
            voltage = 0.0,
            speed = transmission.rpm.toInt(),
            odometer = odometer.totalKm,
            rpm = transmission.rpm.toInt(),
            motorTemperature = 0.0,
            gearPosition = when (evMode) {
                EvMode.SPORTS -> 1
                EvMode.POWER -> 2
                EvMode.ECO -> 0
            },
            brakePosition = "FRONT_RIGHT",
            controllerTemperature = 0.0,
            torque = 0.0,
            direction = 0,
            fault = 0,
        ),
    )
}

private fun CanFeed.processedVehicleState(): String = when (determineVehicleState(this)) {
    VehicleState.RUNNING -> "MOVING"
    VehicleState.IDLE, VehicleState.STOP -> "STOP"
    VehicleState.CHARGING -> "CHARGING"
    VehicleState.OFFLINE -> "OFFLINE"
    VehicleState.TOWING -> "TOWING"
    VehicleState.UNKNOWN -> "UNKNOWN"
}
