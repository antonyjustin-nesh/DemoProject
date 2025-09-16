package com.nlevcluster.sender.report.trip.mapper

import com.nlevcluster.sender.model.Trip
import com.nlevcluster.sender.model.TripDataInfo
import com.nlevcluster.sender.model.TripDataInfoWrapper
import com.nlevcluster.sender.model.TripRecord
import com.nlevcluster.sender.report.content_provider.ReportContentProvider.Companion.NOMINAL_ENERGY
import com.nlevcluster.sender.report.trip.db.TripReportEntity

fun TripReportEntity.toTripRecord(): TripRecord {
    val consumedEnergyPercentage = startSOC - endSOC
    val energyWh = calculateEnergyConsumedInWh(consumedEnergyPercentage)
    val energyKWh = energyWh / 1000.0
    val distanceKm = endOdometer - startOdometer

    return TripRecord(
        tripId = id,
        startTimeMillis = tripStartTimeDurationMs,
        endTimeMillis = tripEndTimeDurationMs,
        idleDurationMillis = idleDurationMs,
        movingDurationMillis = tripEndTimeDurationMs - tripStartTimeDurationMs - idleDurationMs,
        startLat = startTripLocationLat,
        startLng = startTripLocationLon,
        endLat = endTripLocationLat,
        endLng = endTripLocationLon,
        startOdometerKm = startOdometer,
        endOdometerKm = endOdometer,
        startSOCPercent = startSOC,
        endSOCPercent = endSOC,
        avgSpeedKmph = averageSpeedKmph,
        maxSpeedKmph = maximumSpeedKmph,
        driveModeStats = modeStats,
        totalAlertCount = 0,
        energyConsumedInWh = energyWh,
        energyConsumedInKWh = energyKWh,
        energyEfficiencyWhPerKm = calculateEnergyEfficiencyKmPerWh(
            (startSOC - endSOC),
            distanceKm.toInt()
        ),
        energyEfficiencyUnitsPerKm = calculateEnergyEfficiencyKmPerUnit(
            (startSOC - endSOC),
            (endOdometer.toInt() - startOdometer.toInt())
        ),
        regenerationEventCount = regenCount
    )
}

fun convertToTripReports(
    imei: String,
    entities: List<TripReportEntity>
): List<TripDataInfoWrapper> {
    return entities.map { entity ->
        val consumedEnergyPercentage = entity.startSOC - entity.endSOC
        val energyWh = calculateEnergyConsumedInWh(consumedEnergyPercentage)
        val energyKWh = energyWh / 1000.0
        val distanceKm = entity.endOdometer - entity.startOdometer

        TripDataInfoWrapper(
            TripDataInfo(
                imei = imei,
                trip = Trip(
                    tripId = entity.id,
                    tripStartTime = entity.tripStartTimeDurationMs,
                    tripEndTime = entity.tripEndTimeDurationMs,
                    idleDuration = entity.idleDurationMs,
                    movingDuration = (entity.tripEndTimeDurationMs - entity.tripStartTimeDurationMs) - entity.idleDurationMs,
                    startLatitude = entity.startTripLocationLat,
                    startLongitude = entity.startTripLocationLon,
                    endLatitude = entity.endTripLocationLat,
                    endLongitude = entity.endTripLocationLon,
                    startOdometer = entity.startOdometer,
                    endOdometer = entity.endOdometer,
                    startSoc = entity.startSOC,
                    endSoc = entity.endSOC,
                    avgSpeed = entity.averageSpeedKmph.toInt(),
                    maxSpeed = entity.maximumSpeedKmph.toInt(),
                    driveModeStats = entity.modeStats,
                    energyConsumedWh = energyWh,
                    energyConsumedKWh = energyKWh,
                    energyEfficiencyWh = calculateEnergyEfficiencyKmPerWh(
                        consumedEnergyPercentage,
                        distanceKm.toInt()
                    ),
                    energyEfficiencyKWh = calculateEnergyEfficiencyKmPerUnit(
                        consumedEnergyPercentage,
                        distanceKm.toInt())
                    ,
                    regenCount = entity.regenCount
                )
            )
        )
    }
}

private fun calculateEnergyConsumedInWh(
    totalConsumedPercent: Int
): Double {
    if (totalConsumedPercent == 0) return 0.0
    return (totalConsumedPercent / 100.0) * NOMINAL_ENERGY
}

private fun calculateEnergyEfficiencyKmPerWh(
    totalConsumedPercent: Int,
    totalTripDistanceKm: Int
): Double {
    if (totalConsumedPercent == 0 || totalTripDistanceKm == 0) return 0.0
    val totalConsumedEnergyWh = calculateEnergyConsumedInWh(totalConsumedPercent)
    return (totalConsumedEnergyWh / totalTripDistanceKm)
}

private fun calculateEnergyEfficiencyKmPerUnit(
    totalConsumedPercent: Int,
    totalTripDistanceKm: Int
): Float {
    if (totalConsumedPercent == 0 || totalTripDistanceKm == 0) return 0f
    val totalConsumedEnergyWh = calculateEnergyConsumedInWh(totalConsumedPercent)
    val totalConsumedEnergyKWh = totalConsumedEnergyWh / 1000f
    return (totalConsumedEnergyKWh / totalTripDistanceKm).toFloat()
}