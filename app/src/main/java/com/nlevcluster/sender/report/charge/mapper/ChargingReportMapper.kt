package com.nlevcluster.sender.report.charge.mapper

import com.nlevcluster.sender.model.ChargeTelemetryData
import com.nlevcluster.sender.report.charge.db.ChargingSessionEntity

fun convertToChargingTelemetryData(entities: List<ChargingSessionEntity>): List<ChargeTelemetryData> {
    return entities.map { entity ->
        ChargeTelemetryData(
            chargingSessionId = entity.chargingSessionId,
            startSOC = entity.startSOC,
            endSOC = entity.endSOC,
            startChargingLocationLat = entity.startChargingLocationLat,
            startChargingLocationLon = entity.startChargingLocationLon,
            endChargingLocationLat = entity.endChargingLocationLat,
            endChargingLocationLon = entity.endChargingLocationLon,
            chargingStartTimeDurationMs = entity.chargingStartTimeDurationMs,
            chargingEndTimeDurationMs = entity.chargingEndTimeDurationMs,
            chargingType = entity.chargerType,
            chargerID = entity.chargerID,
            isAbnormalTermination = entity.isAbnormalTermination,
            averageVoltage = entity.averageVoltage,
            maxBatteryCurrent = entity.maxBatteryCurrent,
            maxBatteryTemperature = entity.maxBatteryTemperature,
            batteryTemperatureRise = entity.batteryTemperatureRise,
            averageBatteryTemperature = entity.averageBatteryTemperature,
            isSynced = entity.isSynced
        )
    }
}