package com.nlevcluster.sender.service

import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.LocationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSendServiceDiagnosticsTest {

    @Test
    fun `CAN diagnostic includes runtime state without a location`() {
        val message = DataSendServiceDiagnostics.canFeedAccepted(
            CanFeed(
                seqNo = 42,
                currentLocation = LocationInfo(latitude = 12.9716, longitude = 77.5946),
            )
        )

        assertTrue(message.contains("seq=42"))
        assertFalse(message.contains("12.9716"))
        assertFalse(message.contains("77.5946"))
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
