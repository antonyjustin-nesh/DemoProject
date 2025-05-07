package com.nlevcluster.sender.trip.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

@Dao
interface TripReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrip(tripReportEntity: TripReportEntity): Long

    @Update
    suspend fun updateTrip(summary: TripReportEntity): Int


}