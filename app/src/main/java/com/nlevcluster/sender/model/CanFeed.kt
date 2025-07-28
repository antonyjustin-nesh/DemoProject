package com.nlevcluster.sender.model
import kotlinx.serialization.Serializable


@Serializable
data class CanFeed(
    val seqNo: Int = -1,
    val transmission: Transmission = Transmission(),
    var battery: Battery = Battery(),
    val odometer: Odometer = Odometer(),
    val indicator: Indicator = Indicator(),
    val ignition: Ignition = Ignition.PARK,
    val ignitionStatus: IgnitionStatus = IgnitionStatus.ON,
    val stand: Stand = Stand.ON,
    val evMode: EvMode = EvMode.ECO,
    val headLight: HeadLight = HeadLight(),
    var currentLocation: LocationInfo? = null,
    val createdDate: Long = 0L,
    val isSimulated: Boolean = false,
)
