package com.nlevcluster.sender.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nlevcluster.sender.model.Log
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import com.nlevcluster.sender.database.LogType
import java.time.format.DateTimeFormatter

@Entity(tableName = "log")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val logType: LogType,
    val message: String,
    @ColumnInfo(name = "created_date")
    val createdDate: kotlinx.datetime.Instant,
)

fun LogEntity.asExternalModel() = Log(
    id = id,
    timeStamp = createdDate
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd-MMMM-yyyy hh:mm:ss a")),
    message = message,
    logType = logType.toString(),
)