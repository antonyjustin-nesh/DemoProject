package com.nlevcluster.sender.service

import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.LocationInfo

/** Safe, compact diagnostics for logs that are exported outside the Sender app. */
internal object DataSendServiceDiagnostics {

    fun canFeedAccepted(feed: CanFeed): String =
        "CAN feed accepted: seq=${feed.seqNo}, ignition=${feed.ignition}, " +
            "ignitionStatus=${feed.ignitionStatus.status}, mode=${feed.evMode}, " +
            "simulated=${feed.isSimulated}, speedKph=${feed.transmission.speed}, " +
            "rpm=${feed.transmission.rpm.toInt()}, batteryPercent=${feed.battery.remainingPercent}, " +
            "batteryVoltage=${feed.battery.volt}, batteryCurrent=${feed.battery.current}, " +
            "odometerKm=${feed.odometer.totalKm}"

    fun canFeedSubmitted(feed: CanFeed): String =
        "CAN vehicle feed submitted: seq=${feed.seqNo}, ignitionOn=${feed.ignitionStatus.status == "ON"}"

    fun gpsUpdateAccepted(location: LocationInfo): String =
        "GPS update accepted: accuracyM=${location.accuracy}, speedMps=${location.speed}, altitudeM=${location.altitude}"

    fun runtimeConfig(
        brokerSource: String,
        vehicleOnDelayMs: Long,
        vehicleOffDelayMs: Long,
        instrumentationOnDelayMs: Long,
        instrumentationOffDelayMs: Long,
    ): String =
        "Telemetry config resolved: brokerSource=$brokerSource, " +
            "vehicleIntervalsMs=$vehicleOnDelayMs/$vehicleOffDelayMs, " +
            "instrumentationIntervalsMs=$instrumentationOnDelayMs/$instrumentationOffDelayMs, " +
            "topics=$VEHICLE_TOPIC_NAME/$INSTRUMENTATION_TOPIC_NAME"

    private const val VEHICLE_TOPIC_NAME = "CLUSTER_DATA"
    private const val INSTRUMENTATION_TOPIC_NAME = "INSTRUMENTATION"
}
