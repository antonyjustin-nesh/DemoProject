package com.nlevcluster.sender

import live.nesh.cluster.telematics.logging.TelematicsLog
import live.nesh.cluster.telematics.logging.TelematicsLogLevel
import com.nlevcluster.sender.nllogger.LogType

/** Compact diagnostics for logs exported outside the Sender app. */
internal object TelematicsManagerDiagnostics {

    fun feedConfiguration(config: TelematicsRuntimeConfig): String =
        "Telemetry feeds configured: vehicle=${feedSummary(config.vehicleFeed)}, " +
            "instrumentation=${feedSummary(config.instrumentationFeed)}"

    fun ignitionState(isOn: Boolean): String = if (isOn) "on" else "off"

    fun sdkLog(log: TelematicsLog): String =
        "${log.commonTag} ${log.level} ${log.className}.${log.functionName}: ${log.message}"

    private fun feedSummary(feed: live.nesh.cluster.telematics.FeedConfig): String =
        "enabled=${feed.enabled}, intervalsMs=${feed.ignitionOnIntervalMs}/${feed.ignitionOffIntervalMs}, " +
            "topicConfigured=${feed.topic.isNotBlank()}"
}

internal fun TelematicsLogLevel.toSenderLogType(): LogType = when (this) {
    TelematicsLogLevel.DEBUG -> LogType.DEBUG
    TelematicsLogLevel.INFO -> LogType.INFO
    TelematicsLogLevel.ERROR -> LogType.ERROR
}
