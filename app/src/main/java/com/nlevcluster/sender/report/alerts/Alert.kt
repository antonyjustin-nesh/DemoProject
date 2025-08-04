package com.nlevcluster.sender.report.alerts

import kotlinx.serialization.Serializable

@Serializable
data class Alert(
    val id: Long = 0,
    val timestamp: Long = 0L,
    val description: String = "",
    val type: AlertType = AlertType.NONE
)