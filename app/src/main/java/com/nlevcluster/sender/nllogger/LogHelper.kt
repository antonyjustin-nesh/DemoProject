package com.nlevcluster.sender.nllogger

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