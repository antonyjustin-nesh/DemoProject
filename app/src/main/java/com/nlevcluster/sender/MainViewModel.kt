package com.nlevcluster.sender

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.mqtt.MQTTClient
import logcat.LogPriority
import logcat.logcat
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val logHelper: LogHelper,
    private val mqttClient: MQTTClient
) : ViewModel() {

    fun log(message: String) {
        logHelper.log(message = message)
        logcat(LogPriority.INFO, "MainViewModel") {
            message
        }
    }




}