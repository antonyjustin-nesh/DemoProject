package com.nlevcluster.sender.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.nlevcluster.sender.R
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.database.entity.CanParseEntity
import com.nlevcluster.sender.database.util.Util
import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.Ignition
import com.nlevcluster.sender.mqtt.MQTTClient
import com.nlevcluster.sender.util.MQTTUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import javax.inject.Named


@AndroidEntryPoint
class DataSendService : Service() {

    @Inject
    lateinit var logHelper: LogHelper

    private val portalMQTTClient = MQTTUtil()

    @Inject
    @Named("local")
    lateinit var localMqttClient: MQTTClient

    private var ignitionOnDelay = 5_000L
    private var ignitionOffDelay = 60_000L

    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var senderJob: Job? = null

    private val notificationTitle = "NL-Sender - Data Sync"
    private val notificationId = 3
    private val notificationChannel = "DATA_SYNC_SERVICE"
    private var notificationManager: NotificationManager? = null
    private var isNotificationChanelCreated = false

    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private val monitorIntervalMillis: Long = 10_000

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNLSenderNotificationChannel()

        monitorMQTTConnection()
    }

    private fun monitorMQTTConnection() {
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                if (!portalMQTTClient.isConnected()) {
                    logcat("MQTT_CONNECTION_MONITOR") { "MQTT Connection lost. Attempting to reconnect..." }
                    initMQTTClient()
                } else {
                    logcat("MQTT_CONNECTION_MONITOR") { "MQTT Connection active. No action required." }
                }
                handler.postDelayed(this, monitorIntervalMillis)
            }
        }

        handler.post(runnable)
        logcat("MQTT_CONNECTION_MONITOR") { "MQTT Connection monitoring initiated." }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (senderJob == null) {
                val initialNotification =
                    createNLSenderNotification("NL-Sender ", "Data sync service started")
                startForeground(notificationId, initialNotification)
                initMQTTClient()
            }
        } catch (e: Exception) {
            logcat { e.asLog() }
        }
        return START_STICKY
    }

    private fun initMQTTClient() {
        portalMQTTClient.connect(
            url = Constants.MQTT_PORTAL_URL,
            port = Constants.MQTT_PORTAL_PORT,
            clientId = Constants.TEST_IMEI
        ) { isConnected ->
            if (isConnected) {
                logcat { "MQTT connected to Cloud" }
                sendData()
                showNotification("MQTT connected to Cloud")
            } else {
                logcat { "MQTT failed to connect to Cloud" }
            }
        }
    }

    private fun showNotification(content: String, title: String = notificationTitle) {
        val mqttNotification = createNLSenderNotification(
            "$title - " + if (portalMQTTClient.isConnected()) {
                "Connected"
            } else {
                "Disconnected"
            }, content
        )
        notificationManager?.notify(notificationId, mqttNotification)
    }

    private fun sendData() {
        senderJob = externalScope.launch(Dispatchers.IO) {
            localMqttClient.canParsedEntityFlow.conflate().onEach { canParseEntity ->
                logcat("MQTT_FILE_TRANSFER") { "Received $canParseEntity" }

                val dataPushDelay = if (canParseEntity.ignitionStatus == "ON") ignitionOnDelay
                else ignitionOffDelay

                sendDataToPortal(canParseEntity)

                delay(dataPushDelay)
                logcat("MQTT_FILE_TRANSFER") { "Sending data to portal" }
            }.collect()
        }
    }

    private fun sendDataToPortal(canFeed: CanParseEntity) {
        val feedData = createFeed(canFeed)
        val mqttPacket = """
        {
            "type":"${Constants.TOPIC_RAW_FEED}",
            "data":${feedData}
        }
        """.trimIndent()
        logcat { "sendDataToPortal $feedData" }
        val messageSize = mqttPacket.toByteArray().size
        showNotification("Publishing message of $messageSize Bytes") //@todo show only important data like seq no.
        portalMQTTClient.publish(Constants.TOPIC_RAW_FEED, mqttPacket) { isSuccess ->
            val status = if (isSuccess) "Published" else "Failed to publish"
            showNotification("$status message with $messageSize Bytes")
        }
    }

    private fun createFeed(canFeedEntity: CanParseEntity): String {
        val gearPos = when (canFeedEntity.evMode) {
            EvMode.SPORTS.mode -> "1"
            EvMode.POWER.mode -> "2"
            else -> "0"
        }
        //Vehicle Mode P/N/D/R
        val vehicleStatus = when (canFeedEntity.ignition) {
            Ignition.REVERSE.name -> "1"
            Ignition.DRIVE.name -> "4"
            Ignition.NEUTRAL.name -> "2"
            else -> "0"
        }
        canFeedEntity.apply {
            return """
            {
              "imei": "359214420438966",
              "sequenceNumber": "$id",
              "latitude": "$latitude",
              "longitude": "$longitude",
              "altitude": "$altitude",
              "time": "${Util.getTimeFromLong(createdDate)}",
              "date": "${Util.getDateFromLong(createdDate)}",
              "speed": "$speed",
              "heading": "34",
              "noOfSat": "5",
              "mainPowerStatus": "1",
              "ignitionStatus": "${getIgnitionStatus(ignitionStatus)}",
              "movementStatus": "1",
              "vehicleStatus": "${returnVehicleStatus(ignitionStatus)}",
              "interBatteryVoltage": "3.9",
              "interBatteryPercentage": "$soc",
              "gpsOdometer": "$odometer",
              "gpsDataValidityStatus": "1",
              "packetLiveOrStoredStatus": "1",
              "batteryVoltage": "$batteryVoltage",
              "batteryCurrent": "$batteryCurrent",
              "batterySoC": "$soc",
              "batteryCapacityRemaining": "32",
              "vehicleSpeed": "$speed",
              "vehicleOdometer": "$odometer",
              "motorRPM": "$rpm",
              "vehicleMode": "$vehicleStatus",
              "gearPosition": "$gearPos",
              "customerCode": "100"
            }
        """.trimIndent()
        }
    }

    private fun returnVehicleStatus(status: String): String {
        return when (status) {
            Ignition.PARK.name -> "0"
            Ignition.DRIVE.name -> "1"
            Ignition.NEUTRAL.name -> "0"
            Ignition.REVERSE.name -> "1"
            else -> "0"
        }
    }

    private fun getIgnitionStatus(status: String): String {
        return when (status) {
            Ignition.PARK.name -> "0"
            Ignition.DRIVE.name, Ignition.NEUTRAL.name, Ignition.REVERSE.name -> "1"
            else -> "0"
        }
    }

    private fun log(message: String) {
        if (::logHelper.isInitialized) {
            logHelper.log(LogType.INFO, message)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNLSenderNotification(title: String, content: String): Notification {
        if (!isNotificationChanelCreated) {
            createNLSenderNotificationChannel()
        }

        return NotificationCompat.Builder(
            this,
            notificationChannel,
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createNLSenderNotificationChannel() {
        val channel = NotificationChannel(
            notificationChannel,
            notificationChannel,
            NotificationManager.IMPORTANCE_NONE,
        ).apply {
            description = "For syncing the feed data to the cloud"
        }

        // Register the channel with the system
        notificationManager?.let { nm ->
            nm.createNotificationChannel(channel)
            isNotificationChanelCreated = true
        }
    }


    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        senderJob?.cancel()
        senderJob = null
        externalScope.cancel()
        portalMQTTClient.disconnect()
        super.onDestroy()
    }
}