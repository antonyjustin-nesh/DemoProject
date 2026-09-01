package com.nlevcluster.sender

import com.nlevcluster.sender.model.Battery
import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.IgnitionStatus
import com.nlevcluster.sender.model.LocationInfo
import com.nlevcluster.sender.model.Odometer
import com.nlevcluster.sender.model.Transmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleFeedMapperTest {

    @Test
    fun mapsVehicleBatteryAndMotorSections() {
        val feed = CanFeed(
            transmission = Transmission(speed = 42, rpm = 3_200.0),
            battery = Battery(
                remainingPercent = 75,
                volt = 51.2,
                current = 8.0,
                fullCapacity = 100.0,
                soc = 75.0,
            ),
            odometer = Odometer(totalKm = 1_234.5),
            evMode = EvMode.SPORTS,
            ignitionStatus = IgnitionStatus.ON,
            currentLocation = LocationInfo(
                latitude = 12.9716,
                longitude = 77.5946,
                altitude = 900f,
                speed = 11.5f,
            ),
        )

        val result = feed.toVehicleFeedRoot(imei = "device-1", epoch = 123L)

        assertEquals("device-1", result.coreVehicleFeed?.imei)
        assertEquals(123L, result.coreVehicleFeed?.epoch)
        assertEquals("MOVING", result.coreVehicleFeed?.vehicleStatus)
        assertEquals(42.0, result.icFeed?.speed ?: 0.0, 0.0)
        assertEquals(12.9716, result.gpsFeed?.latitude ?: 0.0, 0.0)
        assertEquals(8.0, result.batteryFeed?.current ?: 0.0, 0.0)
        assertEquals(51.2, result.batteryFeed?.voltage ?: 0.0, 0.0)
        assertEquals(75.0, result.batteryFeed?.soc ?: 0.0, 0.0)
        assertEquals("DISCHARGING", result.batteryFeed?.status)
        assertEquals(75.0, result.batteryFeed?.remainingCapacity ?: 0.0, 0.0)
        assertEquals(100.0, result.batteryFeed?.fullCapacity ?: 0.0, 0.0)
        assertEquals(3_200, result.motorFeed?.speed)
        assertEquals(1_234.5, result.motorFeed?.odometer ?: 0.0, 0.0)
        assertEquals(3_200, result.motorFeed?.rpm)
        assertEquals(1, result.motorFeed?.gearPosition)
        assertEquals("FRONT_RIGHT", result.motorFeed?.brakePosition)
    }

    @Test
    fun omitsGpsWhenNoRealLocationHasArrived() {
        val result = CanFeed(currentLocation = null).toVehicleFeedRoot(imei = "device-1")

        assertNull(result.gpsFeed)
    }

    @Test
    fun createsCoreOnlyHeartbeatWhenUartDataIsUnavailable() {
        val result = coreOnlyVehicleHeartbeat(imei = "device-1", epoch = 123L)

        assertEquals("device-1", result.coreVehicleFeed?.imei)
        assertEquals(123L, result.coreVehicleFeed?.epoch)
        assertEquals("UNKNOWN", result.coreVehicleFeed?.vehicleStatus)
        assertEquals(0, result.coreVehicleFeed?.ignition)
        assertEquals(0, result.coreVehicleFeed?.mainPowerStatus)
        assertEquals(0f, result.coreVehicleFeed?.externalBatteryVoltage)
        assertNull(result.gpsFeed)
        assertNull(result.icFeed)
        assertNull(result.batteryFeed)
        assertNull(result.motorFeed)
    }

    @Test
    fun addsGpsToHeartbeatWhenCanDataIsUnavailable() {
        val result = coreOnlyVehicleHeartbeat(
            imei = "device-1",
            epoch = 123L,
            location = LocationInfo(
                latitude = 12.9716,
                longitude = 77.5946,
                altitude = 900f,
                speed = 11.5f,
            ),
        )

        assertEquals(12.9716, result.gpsFeed?.latitude ?: 0.0, 0.0)
        assertEquals(77.5946, result.gpsFeed?.longitude ?: 0.0, 0.0)
        assertEquals(0.0, result.gpsFeed?.odometer ?: -1.0, 0.0)
        assertNull(result.icFeed)
    }

    @Test
    fun usesCanOdometerWhenMergingLatestGps() {
        val result = CanFeed(odometer = Odometer(totalKm = 1_234.5))
            .toVehicleFeedRoot(imei = "device-1")
            .withLocation(
                LocationInfo(
                    latitude = 12.9716,
                    longitude = 77.5946,
                    altitude = 900f,
                    speed = 11.5f,
                )
            )

        assertEquals(1_234.5, result.gpsFeed?.odometer ?: 0.0, 0.0)
        assertEquals(12.9716, result.gpsFeed?.latitude ?: 0.0, 0.0)
    }
}
