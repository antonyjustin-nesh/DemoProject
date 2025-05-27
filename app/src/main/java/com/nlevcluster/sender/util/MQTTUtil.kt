package com.nlevcluster.sender.util

import android.annotation.SuppressLint
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.lifecycle.MqttClientAutoReconnect
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import java.util.concurrent.TimeUnit

private const val MIN_RECONNECT_DELAY = 15L
private const val MAX_RECONNECT_DELAY = 60L
private const val KEEP_ALIVE = 3600
private const val TAG = "MQTT_UTIL"
/**
 *
 * */
@SuppressLint("LogNotTimber")
class MQTTUtil {
    private var client: Mqtt5AsyncClient? = null

    fun isConnected(): Boolean = client?.state?.isConnected ?: false

    /**
     * Connects to the broker using given [url], [port], [clientId]
     * */
    fun connect(
        url: String,
        port: Int,
        clientId: String,
        onConnectionStatusChange: (Boolean) -> Unit
    ) {
        if (isConnected()) {
            logcat(TAG) { "connect: The client is already connected. Ignoring the connection request." }
            return
        }

        if(client == null) {
            client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(clientId)
                .serverHost(url)
                .serverPort(port)
                //.automaticReconnect(MqttClientAutoReconnect.builder()
                  //  .initialDelay(MIN_RECONNECT_DELAY, TimeUnit.MILLISECONDS)
                    //.maxDelay(MAX_RECONNECT_DELAY, TimeUnit.MILLISECONDS) .build())
//              .sslWithDefaultConfig()
                .buildAsync()
        }

        client?.connectWith()?.cleanStart(false)?.keepAlive(KEEP_ALIVE)
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(
                        TAG,
                        "connect: failed to connect to the broker" +
                                " using $url:$port with clientId - $clientId",
                        throwable
                    )
                    onConnectionStatusChange(false)
                } else {
                    Log.d(TAG, "Connection successful")
                    onConnectionStatusChange(true)
                }
            }
    }

    fun disconnect() {
        client?.disconnectWith()
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    // handle failure
                    Log.e(TAG, "disconnect: failed to disconnect the broker", throwable)
                } else {
                    Log.d(TAG, "Disconnected successfully")
                    client = null
                }
            }
    }

    @Suppress("UNUSED")
    fun checkConnection() {
        if (!isConnected()) {
            logcat(LogPriority.WARN) { "Client needs to be connected before it can be used" }
            logcat { "Attempting to reconnect" }
            client?.connect()
        }
    }

    fun publish(
        topic: String,
        message: String,
        qos: MqttQos = MqttQos.EXACTLY_ONCE,
        onComplete: (isSuccess: Boolean) -> Unit = {}
    ) {
        logcat(TAG) { "Publishing to topic $topic with message $message" }

        //checkConnection()

        client?.publishWith()
            ?.topic(topic)
            ?.payload(message.toByteArray())
            ?.qos(qos)
            ?.retain(true)
            ?.send()
            ?.whenComplete { ack, throwable ->
                if (throwable != null) {
                    logcat(TAG) { "Publish failed: ${throwable.message}" }
                    onComplete(false)
                } else {
                    logcat(TAG) { "Publish successful: ${ack}" }
                    onComplete(true)
                }
            }
    }
}