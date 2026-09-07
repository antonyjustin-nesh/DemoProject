package com.nlevcluster.sender.service

import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.LocationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSendServiceDiagnosticsTest {

    @Test
    fun `CAN summary is compact and resets after emission`() {
        val summary = SenderCanDiagnosticsSummary()
        summary.recordAccepted(CanFeed(seqNo = 42))
        summary.recordSubmitted(CanFeed(seqNo = 42))
        val message = summary.snapshotAndReset().orEmpty()

        assertTrue(message.contains("accepted=1"))
        assertTrue(message.contains("submitted=1"))
        assertFalse(message.contains("12.9716"))
        assertTrue(summary.snapshotAndReset() == null)
    }

    @Test
    fun `GPS diagnostic omits coordinates`() {
        val message = DataSendServiceDiagnostics.gpsUpdateAccepted(
            LocationInfo(latitude = 12.9716, longitude = 77.5946, accuracy = 4.5, speed = 8f),
        )

        assertTrue(message.contains("accuracyM=4.5"))
        assertFalse(message.contains("12.9716"))
        assertFalse(message.contains("77.5946"))
    }
}
