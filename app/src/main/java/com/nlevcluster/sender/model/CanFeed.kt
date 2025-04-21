package com.nlevcluster.sender.model
import kotlinx.serialization.Serializable


@Serializable
data class CanFeed(
    var seqNo: Int = -1,
    val transmission: Transmission = Transmission(),
    val battery: Battery = Battery(),
    val odometer: Odometer = Odometer(),
    val indicator: Indicator = Indicator(),
    var ignition: Ignition = Ignition.PARK,
    var ignitionStatus: IgnitionStatus = IgnitionStatus.ON,
    var stand: Stand = Stand.ON,
    var evMode: EvMode = EvMode.ECO,
    val headLight: HeadLight = HeadLight(),
    var currentLocation: LocationInfo? = null,
    val createdDate: Long = 0L,
    var isSimulated: Boolean = false,
)
