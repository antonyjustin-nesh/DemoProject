package com.nlevcluster.sender.model


data class Log(
    val id: Int,
    val timeStamp: String,
    val message: String,
    val logType: String,
)
