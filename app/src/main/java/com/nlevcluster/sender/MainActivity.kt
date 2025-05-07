package com.nlevcluster.sender

import android.content.Intent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.service.DataSendService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    private fun startDataSendService() {
        val intent = Intent(this, DataSendService::class.java)
        startForegroundService(intent)
        viewModel.log(type = LogType.DEBUG, "Started DataSendService")
    }

    override fun onResume() {
        super.onResume()
        startDataSendService()
        finish()
    }
}