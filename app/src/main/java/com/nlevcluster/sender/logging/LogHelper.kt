package com.nlevcluster.sender.logging

import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.nllogger.LogCategory
import com.nlevcluster.sender.nllogger.LogModule
import com.nlevcluster.sender.nllogger.LogOwner

interface LogHelper {

    fun log(
        type: LogType = LogType.INFO,
        message: String,
        tag: String? = "NL_LOG",
        logOwner: LogOwner = LogOwner.DEV,
        category: LogCategory = LogCategory.DEBUG,
        module: LogModule = LogModule.SENDER,
    )
}