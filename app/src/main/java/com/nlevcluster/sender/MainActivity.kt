package com.nlevcluster.sender

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.service.DataSendService
import com.nlevcluster.sender.report.service.ReportProcessingService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    private fun startDataSendService() {
        val intent = Intent(this, DataSendService::class.java)
        startForegroundService(intent)
        viewModel.log(type = LogType.DEBUG, "Started DataSendService")
    }

    private fun startTripProcessingService() {
        val intent = Intent(this, ReportProcessingService::class.java)
        startForegroundService(intent)
        viewModel.log(type = LogType.INFO, message = "Started TripProcessingService")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }

    override fun onResume() {
        super.onResume()
        startDataSendService()
        startTripProcessingService()
        finish()
    }

}