package com.nlevcluster.sender.report.charge.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ChargingReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChargingSession(chargingReportEntity: ChargingSessionEntity): Long

    @Query("SELECT * FROM charging_session WHERE CHARGING_SESSION_ID = :chargingSessionId LIMIT 1")
    fun getChargingSessionBySessionId(chargingSessionId: Int): ChargingSessionEntity?

    @Query("SELECT * FROM charging_session WHERE END_TIME BETWEEN :startTime AND :endTime ORDER BY END_TIME DESC")
    fun getChargingSessionsByEndTimeRange(
        startTime: Long,
        endTime: Long
    ): List<ChargingSessionEntity>

    @Update
    fun updateChargingSession(chargingSessionEntity: ChargingSessionEntity): Int

    @Query("SELECT * FROM charging_session WHERE IS_SYNCED = 0 AND END_TIME != 0")
    fun getUnSyncedChargingSessions(): List<ChargingSessionEntity>

    @Query("UPDATE charging_session SET IS_SYNCED = 1 WHERE CHARGING_SESSION_ID = :chargingSessionId AND END_TIME != 0")
    fun markChargingSessionAsSynced(chargingSessionId: Int)

    @Query("SELECT * FROM charging_session")
    fun getAllChargingSessions(): List<ChargingSessionEntity>

    @Query("DELETE FROM charging_session")
    fun deleteAllChargingSessions()

}