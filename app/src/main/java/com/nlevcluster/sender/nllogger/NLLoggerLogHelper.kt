package com.nlevcluster.sender.nllogger

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.logging.LogHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

class NLLoggerLogHelper @Inject constructor(
    @ApplicationContext val context: Context
) : LogHelper {

    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var contentResolver: ContentResolver? = null

    init {
        contentResolver = context.contentResolver
    }

    override fun log(
        type: LogType,
        message: String,
        tag: String?,
        logOwner: LogOwner,
        category: LogCategory,
        module: LogModule
    ) {
        externalScope.launch(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(NLLoggerConstants.LOG_TYPE, type.name)
                put(NLLoggerConstants.TAG, tag)
                put(NLLoggerConstants.OWNER, logOwner.name)
                put(NLLoggerConstants.CATEGORY, category.name)
                put(NLLoggerConstants.MODULE, module.name)
                put(NLLoggerConstants.MESSAGE, message)
                put(NLLoggerConstants.CREATED_DATE, Clock.System.now().toEpochMilliseconds())
            }
            runCatching {
                contentResolver?.insert(NLLoggerConstants.CONTENT_URI, values)
            }.onFailure {
                logcat { it.asLog() }
            }
        }
    }
}