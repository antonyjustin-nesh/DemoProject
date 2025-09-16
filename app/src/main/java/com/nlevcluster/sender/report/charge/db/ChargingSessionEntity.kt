package com.nlevcluster.sender.report.charge.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nlevcluster.sender.report.charge.ChargerType
import com.nlevcluster.sender.model.ChargeTelemetryData
import com.nlevcluster.sender.report.trip.ChargingSessionStatus
import com.nlevcluster.sender.report.trip.TripStatus


@Entity(tableName = "charging_session")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "CHARGING_SESSION_ID")
    val chargingSessionId: Int = 0,
    @ColumnInfo(name = "START_TIME")
    val chargingStartTimeDurationMs: Long = 0,
    @ColumnInfo(name = "END_TIME")
    val chargingEndTimeDurationMs: Long = 0,
    @ColumnInfo(name = "START_LATITUDE")
    val startChargingLocationLat: Double = 0.0,
    @ColumnInfo(name = "START_LONGITUDE")
    val startChargingLocationLon: Double = 0.0,
    @ColumnInfo(name = "END_LATITUDE")
    val endChargingLocationLat: Double = 0.0,
    @ColumnInfo(name = "END_LONGITUDE")
    val endChargingLocationLon: Double = 0.0,
    @ColumnInfo(name = "START_SOC")
    val startSOC: Int = 0,
    @ColumnInfo(name = "END_SOC")
    val endSOC: Int = 0,
    @ColumnInfo(name = "CHARGER_TYPE")
    val chargerType: ChargerType = ChargerType.AC_L1,
    @ColumnInfo(name = "CHARGE_RATE")
    val chargeRate: Double = 0.0,
    @ColumnInfo(name = "CHARGER_ID")
    val chargerID: String = "",
    @ColumnInfo(name = "IS_ABNORMAL_TERMINATION")
    val isAbnormalTermination: Boolean = false,
    @ColumnInfo(name = "BATTERY_CURRENT")
    val batteryCurrent: Double = 0.0,
    @ColumnInfo(name = "BATTERY_VOLTAGE")
    val batteryVoltage: Double = 0.0,
    @ColumnInfo(name = "AVERAGE_CURRENT")
    val averageCurrent: Double = 0.0,
    @ColumnInfo(name = "AVERAGE_VOLTAGE")
    val averageVoltage: Double = 0.0,
    @ColumnInfo(name = "MAX_BATTERY_CURRENT")
    val maxBatteryCurrent: Double = 0.0,
    @ColumnInfo(name = "MAX_BATTERY_VOLTAGE")
    val maxBatteryVoltage: Double = 0.0,
    @ColumnInfo(name = "BATTERY_TEMPERATURE")
    val batteryTemperature: Float = 0f,
    @ColumnInfo(name = "MAX_BATTERY_TEMPERATURE")
    val maxBatteryTemperature: Float = 0f,
    @ColumnInfo(name = "MIN_BATTERY_TEMPERATURE")
    val minBatteryTemperature: Float = 0f,
    @ColumnInfo(name = "BATTERY_TEMPERATURE_RISE")
    val batteryTemperatureRise: Float = 0f,
    @ColumnInfo(name = "AVERAGE_BATTERY_TEMPERATURE")
    val averageBatteryTemperature: Float = 0f,
    @ColumnInfo(name = "CHARGING_SESSION_STATUS")
    val sessionStatus: ChargingSessionStatus = ChargingSessionStatus.NOT_STARTED,
    @ColumnInfo(name = "IS_SYNCED")
    val isSynced: Int = 0,
)


