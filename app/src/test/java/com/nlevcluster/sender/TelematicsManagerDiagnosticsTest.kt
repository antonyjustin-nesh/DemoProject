package com.nlevcluster.sender

import live.nesh.cluster.telematics.FeedConfig
import live.nesh.cluster.telematics.logging.TelematicsLog
import live.nesh.cluster.telematics.logging.TelematicsLogLevel
import com.nlevcluster.sender.nllogger.LogType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelematicsManagerDiagnosticsTest {

    @Test
    fun `feed configuration diagnostic excludes identity and broker values`() {
        val message = TelematicsManagerDiagnostics.feedConfiguration(
            TelematicsRuntimeConfig(
                brokerUrl = "wss://broker.example.test/secret",
                clientId = "imei-123456789012345",
                vehicleFeed = FeedConfig(true, 30_000L, 60_000L, "CLUSTER_DATA"),
                instrumentationFeed = FeedConfig(true, 300_000L, 600_000L, "INSTRUMENTATION"),
            )
        )

        assertTrue(message.contains("intervalsMs=30000/60000"))
        assertFalse(message.contains("imei-123456789012345"))
        assertFalse(message.contains("broker.example.test"))
        assertFalse(message.contains("CLUSTER_DATA"))
    }

    @Test
    fun `SDK callback diagnostics preserve each level and original message`() {
        val expectedTypes = mapOf(
            TelematicsLogLevel.DEBUG to LogType.DEBUG,
            TelematicsLogLevel.INFO to LogType.INFO,
            TelematicsLogLevel.ERROR to LogType.ERROR,
        )

        expectedTypes.forEach { (level, expectedType) ->
            val message = TelematicsManagerDiagnostics.sdkLog(
                TelematicsLog(
                    commonTag = "TELEMATICS",
                    className = "TelemetryPublisher",
                    functionName = "publish",
                    message = "published packet for diagnostic testing",
                    level = level,
                )
            )

            assertEquals(expectedType, level.toSenderLogType())
            assertTrue(message.contains(level.name))
            assertTrue(message.contains("TelemetryPublisher.publish"))
            assertTrue(message.contains("published packet for diagnostic testing"))
        }
    }
}
