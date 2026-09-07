package com.nlevcluster.sender.service

import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.LocationInfo

/** Safe, compact diagnostics for logs that are exported outside the Sender app. */
internal object DataSendServiceDiagnostics {

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

internal class SenderCanDiagnosticsSummary {
    private var accepted = 0
    private var submitted = 0
    private var invalid = 0
    private var decodeFailures = 0
    private var handoffFailures = 0
    private var ignitionOn: Boolean? = null

    @Synchronized fun recordAccepted(feed: CanFeed) { accepted++; ignitionOn = feed.ignitionStatus.status == "ON" }
    @Synchronized fun recordSubmitted(feed: CanFeed) { submitted++; ignitionOn = feed.ignitionStatus.status == "ON" }
    @Synchronized fun recordInvalidPayload() { invalid++ }
    @Synchronized fun recordDecodeFailure() { decodeFailures++ }
    @Synchronized fun recordHandoffFailure() { handoffFailures++ }

    @Synchronized fun snapshotAndReset(): String? {
        if (accepted + submitted + invalid + decodeFailures + handoffFailures == 0) return null
        val result = "CAN summary 60s: accepted=$accepted, submitted=$submitted, invalid=$invalid, decodeFailures=$decodeFailures, handoffFailures=$handoffFailures, ignitionOn=${ignitionOn ?: "unknown"}"
        accepted = 0; submitted = 0; invalid = 0; decodeFailures = 0; handoffFailures = 0; ignitionOn = null
        return result
    }
}
