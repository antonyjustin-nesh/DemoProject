package com.nlevcluster.sender.model

/**
 *  External data layer representation of UsbCanFeed
 */
data class CanFeed(
    val transmission: Transmission,
    val battery: Battery,
    val odometer: Odometer,
    val indicator: Indicator,
    val ignition: Ignition,
    val ignitionStatus: IgnitionStatus,
    val stand: Stand,
    val evMode: EvMode,
    val headLight: HeadLight,
    val gps: String = "",
    var currentLocation: LocationInfo? = null,
    val usbState: UsbState?,
    val createdDate: Long,
    val obd2: OBD2
)
