package com.nlevcluster.sender

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.database.dao.CanParseDao
import com.nlevcluster.sender.database.dao.LogDao
import com.nlevcluster.sender.database.entity.CanParseEntity
import com.nlevcluster.sender.database.util.Util
import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.Ignition
import com.nlevcluster.sender.service.DataSendService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.io.File
import java.io.PrintWriter
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val logHelper: LogHelper,
    private val logDao: LogDao,
) : ViewModel() {

    /**
     * Logging the message to the database
     *
     * @param message
     */
    fun log(message: String) {
        logHelper.log(message = message)
        logcat(LogPriority.INFO, "MainViewModel") {
            message
        }
    }







    /**
     * Getting the logs from the database and writing on the file
     *
     * @param file
     */
    fun shareWriteableFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val logDate = LocalDate.now()
            val startOfDate = logDate.atStartOfDay()
            val dateLong = startOfDate.atZone(ZoneId.systemDefault()).toInstant().epochSecond
            val logToday = logDao.getTodaysData(dateLong)
            file.printWriter().use { out ->
                logToday.forEach {
                    out.println(it.toString())
                }
            }
        }
    }


}