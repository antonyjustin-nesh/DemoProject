package com.nlevcluster.sender.nllogger

import android.net.Uri

object NLLoggerConstants {
    private const val AUTHORITY = "com.nlevcluster.nl_logger.provider"
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/logs")

    const val LOG_TYPE = "LOG_TYPE"
    const val TAG = "TAG"
    const val OWNER = "OWNER"
    const val CATEGORY = "CATEGORY"
    const val MODULE = "MODULE"
    const val MESSAGE = "MESSAGE"
    const val CREATED_DATE = "CREATED_DATE"
}