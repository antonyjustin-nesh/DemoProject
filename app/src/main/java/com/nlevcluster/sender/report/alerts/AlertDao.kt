package com.nlevcluster.sender.report.alerts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Query("SELECT * FROM alerts ORDER BY TIME_STAMP DESC")
    suspend fun getAllAlerts(): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE ALERT_TYPE = :type ORDER BY TIME_STAMP DESC")
    suspend fun getAlertsByType(type: AlertType): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE TIME_STAMP BETWEEN :startTime AND :endTime ORDER BY TIME_STAMP DESC")
    fun getAlertsByTimeRange(
        startTime: Long,
        endTime: Long
    ): List<AlertEntity>

    @Query("DELETE FROM alerts")
    suspend fun clearAllAlerts()
}
