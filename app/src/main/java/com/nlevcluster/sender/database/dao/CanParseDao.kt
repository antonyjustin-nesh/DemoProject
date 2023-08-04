package com.nlevcluster.sender.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nlevcluster.sender.database.entity.CanParseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CanParseDao {

    @Query(value = "SELECT * FROM can_parse ORDER BY id DESC LIMIT 1")
    fun getLastCanParseEntity(): Flow<CanParseEntity?>

    @Query(value = "SELECT * FROM can_parse")
    fun observeAll(): Flow<List<CanParseEntity>>

    @Upsert
    suspend fun upsertCanParseData(canParseEntity: CanParseEntity)
}