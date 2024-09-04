package com.nlevcluster.sender.logging

import com.nlevcluster.sender.nllogger.LogCategory
import com.nlevcluster.sender.nllogger.LogModule
import com.nlevcluster.sender.nllogger.LogOwner
import com.nlevcluster.sender.nllogger.LogType

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