package com.nlevcluster.sender.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nlevcluster.sender.model.CanFeed
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Entity(tableName = "can_parse")
data class CanParseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val soc: Double,
    val evMode: String,
    @ColumnInfo(name = "created_date")
    val createdDate: Instant,
)

fun CanFeed.asEntity() = CanParseEntity(
    soc = battery.remainingPercent.toDouble(),
    evMode = evMode.mode,
    createdDate = Clock.System.now()
)

