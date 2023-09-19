package com.nlevcluster.sender.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nlevcluster.sender.database.entity.LogEntity

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(logEntity: LogEntity)

    @Query("SELECT * FROM log ORDER BY id DESC")
    fun observeAll(): PagingSource<Int, LogEntity>

    @Query("SELECT * FROM log ORDER BY id DESC")
    fun getAll(): List<LogEntity>

    @Query("SELECT * FROM log  WHERE created_date >= :todayDate  ORDER BY id DESC LIMIT 1000")
    fun getTodaysData(todayDate:Long): List<LogEntity>
}