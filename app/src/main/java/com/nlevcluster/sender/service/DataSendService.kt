package com.nlevcluster.sender.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.nlevcluster.sender.R
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.data.constants.Constants.APPLY_VIP_URI
import com.nlevcluster.sender.data.constants.Constants.DEFAULT_IGN_OFF_DELAY
import com.nlevcluster.sender.data.constants.Constants.DEFAULT_IGN_ON_DELAY
import com.nlevcluster.sender.data.constants.Constants.MODEL_VIP_URI
import com.nlevcluster.sender.database.entity.CanParseEntity
import com.nlevcluster.sender.database.util.Util
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.Ignition
import com.nlevcluster.sender.model.VIP
import com.nlevcluster.sender.mqtt.MQTTClient
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.util.MQTTUtil
import com.nlevcluster.sender.util.NLDataStore
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

const val IGNITION_ON_DELAY = 30_000L
const val IGNITION_OFF_DELAY = 180_000L
const val INTERVAL_IN_MILLIS = 15_000L
const val NOTIFICATION_ID = 3

@AndroidEntryPoint
class DataSendService : Service() {

    private val portalMQTTClient = MQTTUtil()

    @Inject
    lateinit var nlDataStore: NLDataStore

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var logHelper: LogHelper

    val tag = "VIPManager"

    @Inject
    @Named("local")
    lateinit var localMqttClient: MQTTClient

    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var senderJob: Job? = null

    private val notificationTitle = "NL-Sender - Data Sync"
    private val notificationChannel = "DATA_SYNC_SERVICE"
    private var notificationManager: NotificationManager? = null
    private var isNotificationChannelCreated = false

    private var handler: Handler? = null
    private var monitorMQTTConnectionRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNLSenderNotificationChannel()
        registerVIPObserver()
    }

    private fun registerVIPObserver() {
        val applyVIPObserverUri = Uri.parse(APPLY_VIP_URI)
        val contentObserver = object : ContentObserver(android.os.Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                configureSenderService()
            }
        }
        contentResolver?.registerContentObserver(applyVIPObserverUri, true, contentObserver)
    }

    private fun configureSenderService() {
        var vip: VIP? = null
        try {
            val vipURI = Uri.parse(MODEL_VIP_URI)
            val cursor: Cursor? = contentResolver?.query(vipURI, null, null, null, null)
            cursor?.let {
                while (it.moveToNext()) {
                    val modeVip = it.getColumnIndex("model_vip")
                    val modeVipData = it.getString(modeVip)
                    vip = gson.fromJson(modeVipData, VIP::class.java)

                    val ignOnDelay = vip?.appConfig?.feed?.ignOnDelay ?: DEFAULT_IGN_ON_DELAY
                    val ignOffDelay = vip?.appConfig?.feed?.ignOnDelay ?: DEFAULT_IGN_OFF_DELAY
                    val mqttFeedUrl = vip?.appConfig?.feed?.mqttUrl

                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        nlDataStore.setIgnitionOnDelay(ignOnDelay)
                        nlDataStore.setIgnitionOffDelay(ignOffDelay)

                        mqttFeedUrl?.let { url ->
                            nlDataStore.setMqttFeedUrl(url)
                            stopService(Intent(this@DataSendService, DataSendService::class.java))
                        }
                    }
                }
                it.close()
            }
        } catch (e: Exception) {
            logcat(tag) { "NLVIPHelper - Exception - ${e.message}" }
        }
    }

    private fun monitorMQTTConnection(imei: String) {
        handler = Handler(Looper.getMainLooper())
        monitorMQTTConnectionRunnable?.let { handler?.removeCallbacks(it) }

        monitorMQTTConnectionRunnable = object : Runnable {
            override fun run() {
                if (!portalMQTTClient.isConnected()) {
                    logHelper.log(
                        tag = "MQTT_CONNECTION_MONITOR",
                        message = "Portal MQTT Connection lost. Attempting to reconnect..."
                    )
                    initPortalMQTTClient(imei)
                } else {
                    logHelper.log(
                        tag = "MQTT_CONNECTION_MONITOR",
                        message = "Portal MQTT Connection active. No action required."
                    )
                }

                if (!localMqttClient.isConnected()) {
                    logHelper.log(
                        tag = "MQTT_CONNECTION_MONITOR",
                        message = "Local MQTT Connection lost. Attempting to reconnect..."
                    )
                    initLocalMQTTClient(imei)
                } else {
                    logHelper.log(
                        tag = "MQTT_CONNECTION_MONITOR",
                        message = "Local MQTT Connection active. No action required."
                    )
                }

                handler?.postDelayed(this, INTERVAL_IN_MILLIS)
            }
        }

        monitorMQTTConnectionRunnable?.let { handler?.post(it) }
        logHelper.log(
            tag = "MQTT_CONNECTION_MONITOR",
            message = "MQTT Connection monitoring initiated."
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val initialNotification =
                createNLSenderNotification("NL-Sender ", "Data sync service started")
            startForeground(NOTIFICATION_ID, initialNotification)

            val sharedPref = getSharedPreferences("properties", MODE_PRIVATE)
            val imei: String? = intent?.getStringExtra("IMEI") ?: sharedPref.getString("imei", null)
            imei?.let { imeiValue ->
                // Check if IMEI is already stored, if not, save it
                if (sharedPref.getString("imei", null) == null) {
                    with(sharedPref.edit()) {
                        putString("imei", imeiValue)
                        commit()
                    }
                }

                if (senderJob == null) {
                    logcat { "Received imei Number - $imei" }
                    monitorMQTTConnection(imei)
                    initPortalMQTTClient(imei)
                    return START_REDELIVER_INTENT
                }
            }
        } catch (e: Exception) {
            logcat { e.asLog() }
        }

        return START_STICKY
    }

    private fun initPortalMQTTClient(imei: String) {
        externalScope.launch {
            portalMQTTClient.connect(
                url = nlDataStore.mqttFeedUrl() ?: Constants.MQTT_PORTAL_URL,
                port = Constants.MQTT_PORTAL_PORT,
                clientId = imei + this@DataSendService.packageName
            ) { isConnected ->
                if (isConnected) {
                    logHelper.log(
                        tag = "Init MQTT client",
                        message = "initMQTTClient - MQTT connected to Cloud."
                    )
                    showNotification("MQTT connected to Cloud")
                } else {
                    logHelper.log(
                        tag = "Init MQTT client",
                        message = "initMQTTClient - MQTT failed to connect to Cloud."
                    )
                }
            }
        }
    }

    private fun initLocalMQTTClient(imei: String) {
        senderJob?.cancel()
        senderJob = null

        senderJob = externalScope.launch(Dispatchers.IO) {
            localMqttClient.canParsedEntityFlow.conflate().onEach { canParseEntity ->
                logcat("MQTT_FILE_TRANSFER") { "Received $canParseEntity" }

                val dataPushDelay =
                    if (canParseEntity.ignitionStatus == "ON") nlDataStore.ignitionOnDelay()
                        ?: IGNITION_ON_DELAY
                    else nlDataStore.ignitionOffDelay() ?: IGNITION_OFF_DELAY

                sendDataToPortal(canParseEntity, imei)

                dataPushDelay?.toLong()?.let { delay(it) }
            }.collect()
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
        notificationManager?.notify(NOTIFICATION_ID, mqttNotification)
    }

    private fun sendDataToPortal(canFeed: CanParseEntity, imei: String) {
        val feedData = createFeed(canFeed, imei)
        val mqttPacket = """
        {
            "type":"${Constants.TOPIC_RAW_FEED}",
            "data":${feedData}
        }
        """.trimIndent()
        logcat { "sendDataToPortal $feedData" }

        val messageSize = mqttPacket.toByteArray().size
        showNotification("Publishing message of $messageSize Bytes") //Show only important data like seq no.
        portalMQTTClient.publish(Constants.TOPIC_RAW_FEED, mqttPacket) { isSuccess ->
            val status = if (isSuccess) "Published" else "Failed to publish"
            showNotification("$status message with $messageSize Bytes")
            logHelper.log(
                tag = "Send Data to portal",
                message = "sendDataToPortal - $status \n $feedData"
            )
        }
    }

    private fun createFeed(canFeedEntity: CanParseEntity, imei: String): String {
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
            val moving = if (speed == 0) 0 else 1

            return """
            {
              "imei": "$imei",
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
              "ignitionStatus": "${getIgnitionStatus(ignition)}",
              "movementStatus": "$moving",
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

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNLSenderNotification(title: String, content: String): Notification {
        if (!isNotificationChannelCreated) {
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
            isNotificationChannelCreated = true
            logHelper.log(message = "createNLSenderNotificationChannel - Created notification channel")
        }
    }

    override fun onDestroy() {
        monitorMQTTConnectionRunnable?.let { handler?.removeCallbacks(it) }
        monitorMQTTConnectionRunnable = null
        handler = null

        senderJob?.cancel()
        senderJob = null
        externalScope.cancel()
        portalMQTTClient.disconnect()
        super.onDestroy()
    }
}