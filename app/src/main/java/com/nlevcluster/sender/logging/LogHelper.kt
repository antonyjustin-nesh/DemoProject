package com.nlevcluster.sender.logging

import com.nlevcluster.sender.database.LogType

interface LogHelper {
    fun log(
        type: LogType = LogType.INFO,
        message: String
    )
}