package com.nlevcluster.sender.mqtt

import android.content.Context
import com.google.gson.Gson
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.database.entity.CanParseEntity
import com.nlevcluster.sender.logging.LogHelper
import info.mqtt.android.service.MqttAndroidClient
import info.mqtt.android.service.QoS
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import logcat.asLog
import logcat.logcat
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import javax.inject.Inject

class MQTTClient @Inject constructor(
    context: Context,
    private val gson: Gson,
    private val logHelper: LogHelper,
    serverUri: String,
    clientId: String,
    private val feedTopic: String,
) {

    private val persistence = MqttDefaultFilePersistence(context.filesDir.absolutePath)
    private val disconnectedBufferOptions = DisconnectedBufferOptions().apply {
        isBufferEnabled = true
        isPersistBuffer = true
        isDeleteOldestMessages = true
        bufferSize = 1_000_000
    }

    private val mqttAndroidClient = MqttAsyncClient(serverUri, clientId, persistence).apply {
        setBufferOpts(disconnectedBufferOptions)
    }

    private var defaultMqttCallback: MqttCallback? = null

    val canParsedEntityFlow: Flow<CanParseEntity> = callbackFlow {
        defaultMqttCallback = object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                logHelper.log(LogType.INFO, "MQTT connection lost")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (feedTopic == topic) {
                    try {
                        val canParseEntity = gson.fromJson(
                            message.toString(),
                            CanParseEntity::class.java
                        )
                        trySend(canParseEntity)
                    } catch (e: Exception) {
                        logcat { e.asLog() }
                        logHelper.log(
                            LogType.EXCEPTION,
                            e.message
                                ?: "Error converting message ${message.toString()} to CanParseEntity"
                        )
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                logHelper.log(LogType.INFO, "MQTT message delivery complete")
            }
        }

        connect()

        awaitClose {
            logHelper.log(message = "Listening to mqtt feed stopped. Disconnecting")
            disconnect()
        }
    }.distinctUntilChanged()

    /**
     * Default Mqtt android client connect callback
     */
    private val defaultMqttConnect = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            logHelper.log(LogType.INFO, "MQTT connection success with token $asyncActionToken")
            topicSubscription(feedTopic)
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
     * @param username
     * @param password
     * @param mqttConnect
     * @param mqttCallback
     */
    private fun connect(
        username: String = "",
        password: String = "",
        mqttConnect: IMqttActionListener = defaultMqttConnect,
        mqttCallback: MqttCallback? = defaultMqttCallback
    ) {
        if (mqttCallback != null) {
            mqttAndroidClient.setCallback(mqttCallback)
        }
        val options = MqttConnectOptions()
        options.userName = username
        options.password = password.toCharArray()
        options.isAutomaticReconnect = true
        options.isCleanSession = false

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
    private fun subscribe(
        mqttClient: IMqttAsyncClient,
        topic: String,
        qos: Int = QoS.AtLeastOnce.value,
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
        mqttClient: IMqttAsyncClient = mqttAndroidClient,
        topic: String = feedTopic,
        retained: Boolean = true,
        mqttPublish: IMqttActionListener = defaultMqttPublish,
        qos: Int = QoS.ExactlyOnce.value,
        msg: String,
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
    private fun disconnect(
        mqttClient: IMqttAsyncClient = mqttAndroidClient,
        mqttDisconnect: IMqttActionListener = defaultMqttDisconnect
    ) {
        try {
            mqttClient.disconnect(null, mqttDisconnect)
        } catch (e: MqttException) {
            logHelper.log(LogType.EXCEPTION, "Exception on disconnecting the mqtt client $e")
        }
    }

    /**
     * Connecting MQTT client
     *
     */
    fun connectMqttClient() {
        logHelper.log(message = "Connecting MQTT client to the server")
        connect("", "", object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                logHelper.log(message = "MQTT client connection succeeded")
                topicSubscription(feedTopic)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                connectMqttClient()
                logHelper.log(message = "MQTT client connection failure with exception $exception")
            }
        })
    }

    fun topicSubscription(topic: String) {
        try {
            subscribe(
                mqttAndroidClient,
                topic,
                QoS.AtLeastOnce.value,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        logHelper.log(message = "MQTT subscription successful with topic is the $topic")
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
}