package com.nlevcluster.sender

import androidx.lifecycle.ViewModel
import com.nlevcluster.sender.nllogger.LogHelper
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
    fun log(message: String) {
        logHelper.log(message = message)
        logcat(LogPriority.INFO, "MainViewModel") {
            message
        }
    }

}