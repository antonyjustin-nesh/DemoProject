package com.nlevcluster.sender

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.service.DataSendService
import com.nlevcluster.sender.trip.TripStatus
import com.nlevcluster.sender.trip.service.TripProcessingService
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportDatabase
import com.nlevcluster.sender.trip.db.TripReportEntity
import com.nlevcluster.sender.util.MQTTUtil
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    private fun startDataSendService() {
        val intent = Intent(this, DataSendService::class.java)
        startForegroundService(intent)
        viewModel.log("Started DataSendService")
    }

    private fun startTripProcessingService() {
        val intent = Intent(this, TripProcessingService::class.java)
        startForegroundService(intent)
        viewModel.log("Started TripProcessingService")
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