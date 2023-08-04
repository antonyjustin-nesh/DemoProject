package com.nlevcluster.sender

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.nlevcluster.sender.mqtt.MQTTClient
import dagger.hilt.android.AndroidEntryPoint
import logcat.logcat
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var mqttClient: MQTTClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        connectMqttClient()
        viewModel.sendData()
    }

    /**
     * Connecting MQTT client
     *
     */
    fun connectMqttClient() {
        mqttClient.connect("", "", object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                logcat { "MQTT client connection succeeded" }
                topicSubscription(SUBSCRIBE_PROCESSOR_TOPIC)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                logcat { "MQTT client connection failure with exception $exception" }
            }
        })
    }

    fun topicSubscription(topic: String) {
        try {
            mqttClient.subscribe(
                mqttClient.mqttAndroidClient,
                topic,
                1,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        logcat { "MQTT subscription successful with topic is the $topic" }
                    }

                    override fun onFailure(
                        asyncActionToken: IMqttToken?,
                        exception: Throwable?
                    ) {
                        logcat { "MQTT subscription unsuccessful with topic $topic with exception $exception" }
                    }

                })
        } catch (ex: Exception) {
            logcat { "Exception while subscribing to topic $topic. Detailed exception $ex" }
        }
    }

    override fun onDestroy() {
        mqttClient.disconnect()
        super.onDestroy()
    }

    companion object {
        const val SUBSCRIBE_TOPIC = "collector/cloud"
        const val SUBSCRIBE_PROCESSOR_TOPIC = "processor/cloud"
    }
}