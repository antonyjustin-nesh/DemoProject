package com.nlevcluster.sender.report.charge.mapper

import com.nlevcluster.sender.model.ChargingSessionData
import com.nlevcluster.sender.model.ChargingSessionInfo
import com.nlevcluster.sender.model.ChargingSessionInfoWrapper
import com.nlevcluster.sender.report.charge.ChargerType
import com.nlevcluster.sender.report.charge.db.ChargingSessionEntity
import com.nlevcluster.sender.report.content_provider.ReportContentProvider.Companion.NOMINAL_ENERGY

fun convertToChargingTelemetryData(
    imei: String,
    entities: List<ChargingSessionEntity>
): List<ChargingSessionInfoWrapper> {
    return entities.map { entity ->

        val energyWh = calculateEnergyDeliveredInWh(entity.endSOC - entity.startSOC)
        val energyKWh = energyWh / 1000.0

        ChargingSessionInfoWrapper(
            ChargingSessionInfo(
                imei = imei,
                chargingSession = ChargingSessionData(
                    sessionId = entity.chargingSessionId,
                    chargingStartTime = entity.chargingStartTimeDurationMs,
                    chargingEndTime = entity.chargingEndTimeDurationMs,
                    startLatitude = entity.startChargingLocationLat,
                    startLongitude = entity.startChargingLocationLon,
                    endLatitude = entity.endChargingLocationLat,
                    endLongitude = entity.endChargingLocationLon,
                    startSoc = entity.startSOC,
                    endSoc = entity.endSOC,
                    chargerType = getChargerType(entity.chargerType),
                    chargeRate = entity.chargeRate,
                    chargerId = entity.chargerID,
                    isAbnormalTermination = entity.isAbnormalTermination,
                    batteryCurrent = entity.batteryCurrent,
                    batteryVoltage = entity.batteryVoltage,
                    maxBatteryCurrent = entity.maxBatteryCurrent,
                    maxBatteryVoltage = entity.maxBatteryVoltage,
                    averageCurrent = entity.averageCurrent,
                    averageVoltage = entity.averageVoltage,
                    batteryTemp = entity.batteryTemperature,
                    maxBatteryTemp = entity.maxBatteryTemperature,
                    minBatteryTemp = entity.minBatteryTemperature,
                    batteryTempRise = entity.batteryTemperatureRise,
                    avgBatteryTemp = entity.averageBatteryTemperature,
                    deliveredEnergyWh = energyWh,
                    deliveredEnergyKWh = energyKWh.toFloat()
                )
            )
        )
    }
}

private fun calculateEnergyDeliveredInWh(
    totalConsumedPercent: Int
): Double {
    if (totalConsumedPercent == 0) return 0.0
    return (totalConsumedPercent / 100.0) * NOMINAL_ENERGY
}

private fun getChargerType(chargerType: ChargerType): String {
    return when (chargerType) {
        ChargerType.AC_L1 -> "AC_L1"
        ChargerType.AC_L2 -> "AC_L2"
        ChargerType.DC_FAST -> "DC_FAST"
        ChargerType.WIRELESS -> "WIRELESS"
    }
}