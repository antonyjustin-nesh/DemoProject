package com.nlevcluster.sender.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.room.ColumnInfo
import com.google.gson.Gson
import com.nlevcluster.data_processor.IUartCallback
import com.nlevcluster.data_processor.IUartService
import com.nlevcluster.sender.R
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.data.constants.Constants.APPLY_VIP_URI
import com.nlevcluster.sender.data.constants.Constants.DEFAULT_IGN_OFF_DELAY
import com.nlevcluster.sender.data.constants.Constants.DEFAULT_IGN_ON_DELAY
import com.nlevcluster.sender.data.constants.Constants.MODEL_VIP_URI
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportDatabase
import com.nlevcluster.sender.trip.db.TripReportEntity
import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.Ignition
import com.nlevcluster.sender.model.IgnitionStatus
import com.nlevcluster.sender.model.VIP
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.trip.VehicleState
import com.nlevcluster.sender.util.MQTTUtil
import com.nlevcluster.sender.util.NLDataStore
import com.nlevcluster.sender.util.Util
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.asLog
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

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

    private val json = Json { ignoreUnknownKeys = true }

    private val tag: String = this.javaClass.simpleName

    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var senderJob: Job? = null

    private val notificationTitle = "NL-Sender - Data Sync"
    private val notificationChannel = "DATA_SYNC_SERVICE"
    private var notificationManager: NotificationManager? = null
    private var isNotificationChannelCreated = false

    private var handler: Handler? = null
    private var monitorMQTTConnectionRunnable: Runnable? = null

    private var uartService: IUartService? = null
    private var serialListenerJob: Job? = null
    private var reconnectJob: Job? = null
    private var isBound = false

    private var imei: String? = null


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            uartService = IUartService.Stub.asInterface(binder)
            isBound = true
            logcat(TAG) { "Service connected" }
            startObservingUARTData() // Start collecting data only after binding
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            logcat(TAG) { "Service onBindingDied" }
            uartService = null
            isBound = false
            scheduleReconnect()
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            logcat(TAG) { "Service onNullBinding" }
            uartService = null
            isBound = false
            scheduleReconnect()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            uartService = null
            logcat(TAG) { "Service disconnected" }
            uartService = null
            isBound = false
            scheduleReconnect()
        }
    }

    private val serialServiceDataFlow = callbackFlow {
        val callback = object : IUartCallback.Stub() {

            override fun onRawFeedReceived(data: String?) {
                // NO-OP
            }

            override fun onProcessedFeedReceived(data: String?) {
                logcat { "RX $data" }
                if (data != null) {
                    if (data.isValidJson()) {
                        try {
                            val serialCANFeed = json.decodeFromString<CanFeed>(data)
                            trySend(serialCANFeed)
                        } catch (ex: Exception) {
                            logHelper.log(LogType.ERROR, "Exception parsing UART data: $ex")
                        }
                    } else {
                        logHelper.log(
                            LogType.ERROR,
                            "Invalid JSON received from UART Feed \n $data"
                        )
                    }
                }
            }
        }

        uartService?.registerCallback(callback)

        awaitClose {
            uartService?.unregisterCallback(callback)
        }
    }.catch { logHelper.log(LogType.ERROR, "UART data error: $it") }


    private fun bindToSerialService() {
        val serviceIntent = Intent(SERIAL_SERVICE_INTENT).apply {
            setPackage(SERIAL_SERVICE_PACKAGE_NAME)
        }

        val success = bindService(
            serviceIntent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        if (!success) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = externalScope.launch {
            delay(RECONNECT_DELAY)
            if (!isBound) {
                logcat(TAG) { "Attempting to reconnect to UART service" }
                bindToSerialService()
            }
        }
    }

    private fun startObservingUARTData() {
        serialListenerJob = externalScope.launch(Dispatchers.IO) {
            serialServiceDataFlow
                .catch {
                    logHelper.log(
                        tag = "startObservingUARTData",
                        type = LogType.ERROR,
                        message = "UART data error: $it"
                    )
                }
                .conflate()
                .collect { canData ->
                    val dataPushDelay =
                        if (canData.ignitionStatus.name == "ON") nlDataStore.ignitionOnDelay()
                            ?: IGNITION_ON_DELAY
                        else nlDataStore.ignitionOffDelay() ?: IGNITION_OFF_DELAY

                    imei?.let { sendDataToPortal(canData, it) } ?: run {
                        logcat { "Failed to send data to portal, IMEI is null" }
                    }

                    delay(dataPushDelay.toLong())
                }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNLSenderNotificationChannel()
        registerVIPObserver()
        bindToSerialService()
    }

    private fun registerVIPObserver() {
        val applyVIPObserverUri = Uri.parse(APPLY_VIP_URI)
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                configureSenderService()
            }
        }
        contentResolver?.registerContentObserver(applyVIPObserverUri, true, contentObserver)
    }

    private fun configureSenderService() {
        var vip: VIP?
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
                        message = "Portal MQTT Connection lost. Attempting to reconnect...",
                        type = LogType.ERROR
                    )
                    initPortalMQTTClient(imei)
                } else {
                    logHelper.log(
                        tag = "MQTT_CONNECTION_MONITOR",
                        message = "Portal MQTT Connection active. No action required."
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
            imei = intent?.getStringExtra("IMEI") ?: sharedPref.getString("imei", null)
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
                    monitorMQTTConnection(imeiValue)
                    initPortalMQTTClient(imeiValue)
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
                        message = "initMQTTClient - MQTT failed to connect to Cloud.",
                        type = LogType.ERROR
                    )
                }
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
        notificationManager?.notify(NOTIFICATION_ID, mqttNotification)
    }

    private fun sendDataToPortal(canFeed: CanFeed, imei: String) {
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

    private fun createFeed(processedCANFeed: CanFeed, imei: String): String {
        logcat("canFeedEntity") { "canFeedEntity.evMode - ${processedCANFeed.evMode}" }
        val gearPos = when (processedCANFeed.evMode) {
            EvMode.SPORTS -> "1"
            EvMode.POWER -> "2"
            else -> "0"
        }
        logcat("canFeedEntity") { "gearPos - $gearPos" }

        //Vehicle Mode P/N/D/R
        val vehicleStatus = when (processedCANFeed.ignition) {
            Ignition.REVERSE -> "1"
            Ignition.DRIVE -> "4"
            Ignition.NEUTRAL -> "2"
            else -> "0"
        }

        val moving = if (processedCANFeed.transmission.speed == 0) 0 else 1

        return """
            {
              "imei": "$imei",
              "sequenceNumber": "${processedCANFeed.seqNo}",
              "latitude": "${processedCANFeed.currentLocation?.latitude}",
              "longitude": "${processedCANFeed.currentLocation?.longitude}",
              "altitude": "${processedCANFeed.currentLocation?.altitude}",
              "time": "${Util.getTimeFromLong(processedCANFeed.createdDate)}",
              "date": "${Util.getDateFromLong(processedCANFeed.createdDate)}",
              "speed": "${processedCANFeed.transmission.speed}",
              "heading": "34",
              "noOfSat": "5",
              "mainPowerStatus": "1",
              "ignitionStatus": "${getIgnitionStatus(processedCANFeed.ignition)}",
              "movementStatus": "$moving",
              "vehicleStatus": "${returnVehicleStatus(processedCANFeed.ignition)}",
              "interBatteryVoltage": "3.9",
              "interBatteryPercentage": "${processedCANFeed.battery.remainingPercent}",
              "gpsOdometer": "${processedCANFeed.odometer.totalKm}",
              "gpsDataValidityStatus": "1",
              "packetLiveOrStoredStatus": "1",
              "batteryVoltage": "${processedCANFeed.battery.volt}",
              "batteryCurrent": "${processedCANFeed.battery.current}",
              "batterySoC": "${processedCANFeed.battery.remainingPercent}",
              "batteryCapacityRemaining": "32",
              "vehicleSpeed": "${processedCANFeed.transmission.speed}",
              "vehicleOdometer": "${processedCANFeed.odometer.totalKm}",
              "motorRPM": "${processedCANFeed.transmission.rpm}",
              "vehicleMode": "$vehicleStatus",
              "gearPosition": "$gearPos",
              "customerCode": "100"
            }
        """.trimIndent()
    }

    private fun returnVehicleStatus(status: Ignition): String {
        return when (status) {
            Ignition.PARK -> "0"
            Ignition.DRIVE -> "1"
            Ignition.NEUTRAL -> "0"
            Ignition.REVERSE -> "1"
        }
    }

    private fun getIgnitionStatus(status: Ignition): String {
        return when (status) {
            Ignition.PARK -> "0"
            Ignition.DRIVE, Ignition.NEUTRAL, Ignition.REVERSE -> "1"
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

        logcat(TAG) { "UARTService onDestroy" }
        serialListenerJob?.cancel()
        unbindService(serviceConnection)
        super.onDestroy()
    }

    fun String?.isValidJson(): Boolean {
        if (this.isNullOrBlank()) return false
        return try {
            JSONObject(this)
            true
        } catch (e: Exception) {
            try {
                JSONArray(this)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    companion object {
        const val DATA = "DATA"
        const val TAG = "UART"
        private const val RECONNECT_DELAY = 1000L // 1 second
        private const val SERIAL_SERVICE_INTENT = "com.nlevcluster.serialservice.BIND_UART_SERVICE"
        private const val SERIAL_SERVICE_PACKAGE_NAME = "com.nlevcluster.serialservice"
    }
}