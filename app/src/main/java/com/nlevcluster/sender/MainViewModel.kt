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


    /**
     * Connecting MQTT client with singleton and wrapper class
     *
     */
    fun connectMQTTClient() {
        try {
            logcat(
                LogPriority.INFO,
                "MQTT CONNECT"
            ) { "MQTT Connection establishment" }
            mqttClient.connect(mqttClient.mqttAndroidClient, "", "", object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    log("MQTT client connection succeeded")
                    subscribeTopic("Sender")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    log("MQTT client connection failure with exception $exception")
                }

            })
        } catch (ex: Exception) {
            log("Exception while trying to connect MQTT client $ex")
        }
    }

    /**
     * Disconnecting MQTT client with singleton and wrapper class
     *
     */
    fun disconnectMQTTClient() {
        try {
            log("MQTT disconnection")
            mqttClient.disconnect(mqttClient.mqttAndroidClient)
        } catch (ex: Exception) {
            log("Exception while trying to disconnect MQTT client $ex")
        }
    }

    /**
     * Subscribe MQTT client with topic "Sender"
     *
     */
    private fun subscribeTopic(topic: String) {
        try {
            mqttClient.subscribe(
                mqttClient.mqttAndroidClient,
                topic,
                1,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        log("MQTT subscription successful with topic $topic")
                    }

                    override fun onFailure(
                        asyncActionToken: IMqttToken?,
                        exception: Throwable?
                    ) {
                        log("MQTT subscription unsuccessful with topic $topic with exception $exception")
                    }

                })
        } catch (ex: Exception) {
            log("Exception while subscribing to topic $topic. Detailed exception $ex")
        }
    }


}