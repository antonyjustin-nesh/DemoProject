package com.nlevcluster.sender.trip.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nlevcluster.sender.trip.TripStatus

@Dao
interface TripReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrip(tripReportEntity: TripReportEntity): Long

    @Query("UPDATE trip_report SET TRIP_STATUS = :status WHERE ID = :tripId")
    fun updateTripStatus(tripId: Int, status: TripStatus): Int

    @Update
    fun updateTrip(tripReportEntity: TripReportEntity): Int

    @Query("SELECT * FROM trip_report WHERE ID = :tripId LIMIT 1")
    fun getTripByTripId(tripId: Int): TripReportEntity?

    @Query("SELECT * FROM trip_report WHERE END_TIME BETWEEN :startTime AND :endTime ORDER BY END_TIME DESC")
    fun getTripsByEndTimeRange(
        startTime: Long,
        endTime: Long
    ): List<TripReportEntity>

    @Query("SELECT * FROM trip_report WHERE IS_SYNCED = 0 AND END_TIME != 0")
    fun getUnSyncedTrips(): List<TripReportEntity>

    @Query("UPDATE trip_report SET IS_SYNCED = 1 WHERE ID = :tripId AND END_TIME != 0")
    fun markTripAsSynced(tripId: Int)

    @Query("SELECT * FROM trip_report")
    suspend fun getAllTrips(): List<TripReportEntity>


}