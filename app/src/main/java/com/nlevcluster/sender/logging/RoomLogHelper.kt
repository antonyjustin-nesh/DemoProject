package com.nlevcluster.sender.logging

import com.nlevcluster.sender.database.entity.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.database.dao.LogDao
import logcat.logcat
import javax.inject.Inject

class RoomLogHelper @Inject constructor(
    private val logDao: LogDao,
) : LogHelper {

    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob())

    override fun log(type: LogType, message: String) {
        logcat { message }
        externalScope.launch {
            logDao.insertLog(
                LogEntity(
                    logType = type,
                    message = message,
                    createdDate = Clock.System.now()
                )
            )
        }
    }
}