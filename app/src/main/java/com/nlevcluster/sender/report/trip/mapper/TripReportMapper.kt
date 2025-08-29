package com.nlevcluster.sender.report.trip.mapper

import com.nlevcluster.sender.model.TripRecord
import com.nlevcluster.sender.model.TripTelemetryData
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

fun convertToTripReports(entities: List<TripReportEntity>): List<TripTelemetryData> {
    return entities.map { entity ->
        TripTelemetryData(
            tripId = entity.id,
            averageSpeedKmph = entity.averageSpeedKmph,
            maximumSpeedKmph = entity.maximumSpeedKmph,
            idleDurationMs = entity.idleDurationMs,
            startSOC = entity.startSOC,
            endSOC = entity.endSOC,
            speedAlertsCount = 0,
            idleAlertsCount = 0,
            harshAlertsCount = 0,
            modeStats = entity.modeStats,
            regenCount = entity.regenCount,
            startOdometer = entity.startOdometer,
            endOdometer = entity.endOdometer,
            startTripLocationLat = entity.startTripLocationLat,
            startTripLocationLon = entity.startTripLocationLon,
            endTripLocationLat = entity.endTripLocationLat,
            endTripLocationLon = entity.endTripLocationLon,
            tripStartTimeDurationMs = entity.tripStartTimeDurationMs,
            tripEndTimeDurationMs = entity.tripEndTimeDurationMs,
            tripStatus = entity.tripStatus,
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