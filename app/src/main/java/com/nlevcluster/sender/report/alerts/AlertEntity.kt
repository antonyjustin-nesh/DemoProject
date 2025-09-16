package com.nlevcluster.sender.report.alerts

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID")
    val id: Long = 0,
    @ColumnInfo(name = "TIME_STAMP")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "DESCRIPTION")
    val description: String = "",
    @ColumnInfo(name = "ALERT_TYPE")
    val type: AlertType = AlertType.NONE
)

fun convertToAlerts(entities: List<AlertEntity>): List<Alert> {
    return entities.map { entity ->
        Alert(
            id = entity.id,
            timestamp = entity.timestamp,
            description = entity.description,
            type = entity.type
        )
    }
}