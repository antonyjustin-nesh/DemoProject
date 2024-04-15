package com.nlevcluster.sender.util

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.logcat


/**
 *
 * */
class MQTTUtil {
    private val TAG = "MQTT_UTIL"
    private var client: Mqtt5AsyncClient? = null

    var isConnected = false
        private set

    private var _message: MutableStateFlow<MQTTMessage>? = null
    val message get() = _message?.asStateFlow()

    /**
     * Connects to the broker using given [url], [port], [clientId]
     * */
    fun connect(
        url: String,
        port: Int,
        clientId: String,
        onConnectionStatusChange: (Boolean) -> Unit
    ) {
        if (client != null) {
            Log.i(TAG, "connect: The client is already connected. Ignoring the connection request.")
            return
        }

        client = MqttClient.builder()
            .useMqttVersion5()
            .identifier(clientId)
            .serverHost(url)
            .serverPort(port)
//            .sslWithDefaultConfig()
            .buildAsync()

        client?.connectWith()
            ?.send()
            ?.whenComplete { connAck, throwable ->
                if (throwable != null) {
                    Log.e(
                        TAG,
                        "connect: failed to connect to the broker" +
                                " using $url:$port with clientId - $clientId",
                        throwable
                    )
                    isConnected = false
                    onConnectionStatusChange(isConnected)
                } else {
                    Log.d(TAG, "Connection successful")
                    isConnected = true
                    onConnectionStatusChange(isConnected)
                }
            }
    }

    fun disconnect() {
        client?.disconnectWith()
            ?.send()
            ?.whenComplete { connAck, throwable ->
                if (throwable != null) {
                    // handle failure
                    Log.e(TAG, "disconnect: failed to disconnect the broker", throwable)
                } else {
                    Log.d(TAG, "Disconnected successfully")
                    client = null
                }
            }
    }

    /**
     * Subscribes to the [topic] with provided [qos]. If QoS is not given [MqttQos.EXACTLY_ONCE]
     * is used by default
     * @throws IllegalAccessException
     * */
    fun subscribe(topic: String, qos: MqttQos = MqttQos.EXACTLY_ONCE) {
        Log.d(TAG, "subscribe: Subscribing to topic $topic with QoS ${qos.name}")

        requireNotNull(client) { "Client needs to be connected before it can be used to subscribe." }

        client?.subscribeWith()
            ?.topicFilter(topic)
            ?.qos(qos)
            ?.callback { publish ->
                val data = publish.payloadAsBytes
                Log.d(
                    TAG,
                    "subscribe: Received message on $topic, Message - ${data.decodeToString()}"
                )

                _message?.value = MQTTMessage(
                    message = data.decodeToString(),
                    type = "TEXT",
                    qos = qos.code,
                    topic = publish.topic.toString()
                )

            }
            ?.send()
            ?.whenComplete { subAck, throwable ->
                if (throwable != null) {
                    Log.e(
                        TAG,
                        "subscribe: failed to subscribe to topic - $topic with QoS - $qos",
                        throwable
                    )
                } else {
                    Log.d(TAG, "subscribe: Subscription successful")
                }
            }
    }

    fun publish(topic: String, message: ByteArray, qos: MqttQos = MqttQos.EXACTLY_ONCE) {
        Log.d(
            TAG, "publish: Publishing to topic $topic " +
                    "with message ${message.decodeToString()}"
        )

        requireNotNull(client) {
            "Client needs to be connected " +
                    "before it can be used to subscribe."
        }

        client?.publishWith()
            ?.topic(topic)
            ?.payload(message)
            ?.qos(qos)
            ?.send()
            ?.whenComplete { publish: Mqtt5PublishResult?, throwable: Throwable? ->
                if (throwable != null) {
                    Log.e(
                        TAG,
                        "publish: failed to publish to topic -  $topic, message - $message",
                        throwable
                    )
                } else {
                    logcat(TAG) { "Published successfully" }
                    // handle successful publish, e.g. logging or incrementing a metric
                }
            }
    }
}

data class MQTTMessage(
    val message: String,
    val type: String,
    val qos: Int,
    val topic: String,
)