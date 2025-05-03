package com.nlevcluster.sender

import androidx.lifecycle.ViewModel
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import dagger.hilt.android.lifecycle.HiltViewModel
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val logHelper: LogHelper,
) : ViewModel() {

    /**
     * Logging the message to the database
     *
     * @param message
     */
    fun log(type: LogType, message: String) {
        logHelper.log(type = type, message = message)
        logcat(LogPriority.INFO, "MainViewModel") {
            message
        }
    }

}