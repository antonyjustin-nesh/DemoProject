package com.nlevcluster.sender.mqtt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.nlevcluster.sender.BuildConfig
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.database.dao.CanParseDao
import com.nlevcluster.sender.database.entity.CanParseEntity
import com.nlevcluster.sender.logging.LogHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import info.mqtt.android.service.Ack
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MQTTClient @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
    private val canParseDao: CanParseDao,
) {

    @Inject
    lateinit var logHelper: LogHelper

    private val TAG = "MQTT CLIENT"
    val mqttAndroidClient = MqttAndroidClient(context, SERVER_URI, CLIENT_ID, Ack.AUTO_ACK)
    var handler = Handler(Looper.getMainLooper())
    val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        //variable for MQTT android client for publish and subscription
        private var data: String? = null
        const val SERVER_URI = BuildConfig.SERVER_URI
        const val CLIENT_ID = BuildConfig.CLIENT_ID
    }

    /**
     * Default Mqtt android client callback for connections
     */
    private val defaultMqttCallback = object : MqttCallback {
        override fun connectionLost(cause: Throwable?) {
            /**
             * Try to connect again with MQTTAndroidClient
             */
            connect()
            logHelper.log(LogType.INFO, "MQTT connection lost")
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            handler.postDelayed(object : Runnable {
                override fun run() {
                    try {
                        data = message?.toString()
                        logHelper.log(LogType.INFO, "Received data $data")
                        /**
                         * Fetching the processed data from the processor application to transmit to backend as packets
                         */
                        externalScope.launch {
                            if (topic != null && topic.equals(
                                    "processor/cloud",
                                    true
                                ) && data != null
                            ) {
                                try {
                                    val canParseEntity =
                                        gson.fromJson<CanParseEntity>(
                                            data,
                                            CanParseEntity::class.java
                                        )
                                    canParseDao.upsertCanParseData(canParseEntity)
                                } catch (ex: Exception) {
                                    Log.d(
                                        "Exception",
                                        "Exception on inserting the CanParseEntity to the database $ex"
                                    )
                                }
                            }
                        }
                        logHelper.log(LogType.INFO, "$message arrived from topic $topic")
                    } catch (ex: Exception) {
                        logHelper.log(
                            LogType.EXCEPTION,
                            "Exception on converting message to string $ex"
                        )
                    }
                }

            }, 10000)

        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {
            logHelper.log(LogType.INFO, "MQTT message delivery complete")
        }
    }


    /**
     * Default Mqtt android client connect callback
     */
    private val defaultMqttConnect = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            logHelper.log(LogType.INFO, "MQTT connection success with token $asyncActionToken")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            logHelper.log(LogType.EXCEPTION, "MQTT connection failure with exception $exception")
        }
    }

    /**
     * Default Mqtt android client subscribe callback
     */
    private val defaultMqttSubscribe = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            logHelper.log(LogType.INFO, "MQTT subscribe success with token $asyncActionToken")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            logHelper.log(LogType.EXCEPTION, "MQTT subscribe failure with exception $exception")
        }
    }

    /**
     * Default Mqtt android client unsubscribe callback
     */
    private val defaultMqttUnsubscribe = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            logHelper.log(LogType.INFO, "MQTT unsubscribe success with token $asyncActionToken")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            logHelper.log(LogType.EXCEPTION, "MQTT unsubscribe failure with exception $exception")
        }
    }

    /**
     * Default Mqtt android client publish callback
     */
    private val defaultMqttPublish = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            logHelper.log(LogType.INFO, "MQTT publish success with token $asyncActionToken")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            logHelper.log(LogType.EXCEPTION, "MQTT publish failure with exception $exception")
        }
    }

    /**
     * Default Mqtt android client disconnect callback
     */
    private val defaultMqttDisconnect = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            logHelper.log(LogType.INFO, "MQTT disconnect success with token $asyncActionToken")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            logHelper.log(LogType.EXCEPTION, "MQTT disconnect failure with exception $exception")
        }
    }


    /**
     * MQTT client connection establishment
     *
     * @param mqttClient
     * @param username
     * @param password
     * @param mqttConnect
     * @param mqttCallback
     */
    fun connect(
        username: String = "",
        password: String = "",
        mqttConnect: IMqttActionListener = defaultMqttConnect,
        mqttCallback: MqttCallback = defaultMqttCallback
    ) {
        mqttAndroidClient.setCallback(mqttCallback)
        val options = MqttConnectOptions()
        options.userName = username
        options.password = password.toCharArray()

        try {
            mqttAndroidClient.connect(options, null, mqttConnect)
        } catch (e: MqttException) {
            logHelper.log(LogType.EXCEPTION, "Exception on connecting the mqtt client $e")
        }
    }

    /**
     * Check whether the mqtt client is connected
     *
     * @param mqttClient
     * @return mqttconnectivitystatus
     */
    fun isConnected(mqttClient: MqttAndroidClient): Boolean {
        return mqttClient.isConnected
    }

    /**
     * MQTT client subscribe call
     *
     * @param mqttClient
     * @param topic
     * @param qos
     * @param mqttSubscribe
     */
    fun subscribe(
        mqttClient: MqttAndroidClient,
        topic: String,
        qos: Int = 1,
        mqttSubscribe: IMqttActionListener = defaultMqttSubscribe
    ) {
        try {
            mqttClient.subscribe(topic, qos, null, mqttSubscribe)
        } catch (e: MqttException) {
            logHelper.log(
                LogType.EXCEPTION,
                "Exception on subscribing the topic $topic with exception $e"
            )
        }
    }

    /**
     * MQTT client unsubscribe call
     *
     * @param mqttClient
     * @param topic
     * @param mqttUnsubscribe
     */
    fun unsubscribe(
        mqttClient: MqttAndroidClient,
        topic: String,
        mqttUnsubscribe: IMqttActionListener = defaultMqttUnsubscribe
    ) {
        try {
            mqttClient.unsubscribe(topic, null, mqttUnsubscribe)
        } catch (e: MqttException) {
            logHelper.log(
                LogType.EXCEPTION,
                "Exception on unsubscribing the topic $topic with exception $e"
            )
        }
    }

    /**
     * MQTT client publish call
     *
     * @param mqttClient
     * @param topic
     * @param msg
     * @param qos
     * @param retained
     * @param mqttPublish
     */
    fun publish(
        mqttClient: MqttAndroidClient,
        topic: String,
        msg: String,
        qos: Int = 0,
        retained: Boolean = false,
        mqttPublish: IMqttActionListener = defaultMqttPublish
    ) {
        try {
            val message = MqttMessage()
            message.payload = msg.toByteArray()
            message.qos = qos
            message.isRetained = retained
            mqttClient.publish(topic, message, null, mqttPublish)
        } catch (e: MqttException) {
            logHelper.log(
                LogType.EXCEPTION,
                "Exception on publishing the message $msg to the topic $topic with exception $e"
            )
        }
    }

    /**
     * MQTT client disconnection call
     *
     * @param mqttClient
     * @param mqttDisconnect
     */
    fun disconnect(
        mqttClient: MqttAndroidClient = mqttAndroidClient,
        mqttDisconnect: IMqttActionListener = defaultMqttDisconnect
    ) {
        try {
            mqttClient.disconnect(null, mqttDisconnect)
        } catch (e: MqttException) {
            logHelper.log(LogType.EXCEPTION, "Exception on disconnecting the mqtt client $e")
        }
    }


}