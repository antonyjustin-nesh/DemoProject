package com.nlevcluster.sender.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nlevcluster.sender.database.dao.CanParseDao
import com.nlevcluster.sender.database.entity.LogEntity
import com.nlevcluster.sender.database.util.InstantConverter
import com.nlevcluster.sender.database.util.LogConverter
import com.nlevcluster.sender.database.dao.LogDao
import com.nlevcluster.sender.database.entity.CanParseEntity

@Database(
    entities = [
        LogEntity::class,
        CanParseEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(
    InstantConverter::class,
    LogConverter::class,
)
abstract class EvDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun canParseDao(): CanParseDao
}