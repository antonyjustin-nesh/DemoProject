package com.nlevcluster.sender.trip.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.nlevcluster.sender.R
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.data.constants.Constants.APPLY_VIP_URI
import com.nlevcluster.sender.data.constants.Constants.DEFAULT_IGN_OFF_DELAY
import com.nlevcluster.sender.data.constants.Constants.DEFAULT_IGN_ON_DELAY
import com.nlevcluster.sender.data.constants.Constants.MODEL_VIP_URI
import com.nlevcluster.sender.model.TripReport
import com.nlevcluster.sender.model.TripReportPayload
import com.nlevcluster.sender.model.VIP
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.service.INTERVAL_IN_MILLIS
import com.nlevcluster.sender.service.NOTIFICATION_ID
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportDatabase
import com.nlevcluster.sender.trip.db.TripReportEntity
import com.nlevcluster.sender.trip.db.convertToTripReports
import com.nlevcluster.sender.util.MQTTUtil
import com.nlevcluster.sender.util.NLDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.asLog
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class TripSyncService : Service() {

    @Inject
    lateinit var logHelper: LogHelper

    private val json = Json { ignoreUnknownKeys = true }

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var nlDataStore: NLDataStore

    private val tag: String = "TripSyncService"

    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationTitle = "NL-Sender - Trip Sync"
    private val notificationChannel = "TRIP_SYNC"
    private var notificationManager: NotificationManager? = null
    private var isNotificationChannelCreated = false

    private var monitorMQTTConnectionRunnable: Runnable? = null
    private val portalMQTTClient = MQTTUtil()

    private var tripSyncJob: Job? = null

    private var imei: String? = "867969796696859"

    private var handler: Handler? = null
    private var tripDao: TripReportDao? = null

    override fun onCreate() {
        super.onCreate()

        Log.d(tag, "TripSyncService - onCreate() Called()")
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNLSenderNotificationChannel()
        tripDao = TripReportDatabase.getInstance(applicationContext).tripReportDao()
        registerVIPObserver()
        startTripSync()

    }

    private fun registerVIPObserver() {
        Log.d(tag, "TripSyncService - registerVIPObserver() Called()")
        val applyVIPObserverUri = Uri.parse(APPLY_VIP_URI)
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Log.d(tag, "TripSyncService - registerVIPObserver() - onChange() Called()")
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
                            stopService(Intent(this@TripSyncService, TripSyncService::class.java))
                        }
                    }
                }
                it.close()
            }
        } catch (e: Exception) {
            logcat(tag) { "TripSyncService - Exception - ${e.message}" }
        }
    }

    private fun monitorMQTTConnection(imei: String) {
        handler = Handler(Looper.getMainLooper())
        monitorMQTTConnectionRunnable?.let { handler?.removeCallbacks(it) }

        monitorMQTTConnectionRunnable = object : Runnable {
            override fun run() {
                if (!portalMQTTClient.isConnected()) {
                    Log.d(tag, "Portal MQTT Connection lost. Attempting to reconnect...")
                    logHelper.log(
                        tag = "MQTT_CONNECTION_MONITOR",
                        message = "Portal MQTT Connection lost. Attempting to reconnect...",
                        type = LogType.ERROR
                    )
                    initPortalMQTTClient(imei)
                } else {
                    Log.d(tag, "Portal MQTT Connection active. No action required.")
                    logHelper.log(
                        tag = "MQTT_CONNECTION_MONITOR",
                        message = "Portal MQTT Connection active. No action required."
                    )
                }

                handler?.postDelayed(this, INTERVAL_IN_MILLIS)
            }
        }

        monitorMQTTConnectionRunnable?.let { handler?.post(it) }
        Log.d(tag, "MQTT Connection monitoring initiated.")
        logHelper.log(
            tag = "MQTT_CONNECTION_MONITOR",
            message = "MQTT Connection monitoring initiated."
        )
    }

    private fun initPortalMQTTClient(imei: String) {
        Log.d(tag, "TripSyncService - initPortalMQTTClient() Called()")
        externalScope.launch {
            Log.d(tag, "url - ${nlDataStore.mqttFeedUrl() ?: Constants.MQTT_PORTAL_URL} port - ${Constants.MQTT_PORTAL_PORT}")
            portalMQTTClient.connect(
                url = nlDataStore.mqttFeedUrl() ?: Constants.MQTT_PORTAL_URL,
                port = Constants.MQTT_PORTAL_PORT,
                clientId = imei + this@TripSyncService.packageName
            ) { isConnected ->
                if (isConnected) {
                    Log.d(tag, "initMQTTClient - MQTT connected to Cloud.")
                    logHelper.log(
                        tag = "Init MQTT client",
                        message = "initMQTTClient - MQTT connected to Cloud."
                    )
                    showNotification("MQTT connected to Cloud")

                } else {
                    Log.d(tag, "initMQTTClient - MQTT failed to connect to Cloud.")
                    logHelper.log(
                        tag = "Init MQTT client",
                        message = "initMQTTClient - MQTT failed to connect to Cloud.",
                        type = LogType.ERROR
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d(tag, "TripSyncService - onStartCommand() Called()")
        try {
            val initialNotification =
                createNLSenderNotification("NL-Sender ", "Data sync service started")
            startForeground(NOTIFICATION_ID, initialNotification)

            val sharedPref = getSharedPreferences("properties", MODE_PRIVATE)
            imei = intent?.getStringExtra("IMEI") ?: sharedPref.getString("imei", null)
            imei = "867969796696859"
            Log.d(tag, "TripSyncService - IMEI value - $imei")
            imei?.let { imeiValue ->
                // Check if IMEI is already stored, if not, save it
                if (sharedPref.getString("imei", null) == null) {
                    with(sharedPref.edit()) {
                        putString("imei", imeiValue)
                        commit()
                    }
                }

                logcat { "Received imei Number - $imei" }
                monitorMQTTConnection(imeiValue)
                initPortalMQTTClient(imeiValue)
                return START_REDELIVER_INTENT
            }
        } catch (e: Exception) {
            Log.d(tag, "Exception Occurred TripSyncService - ${e.message}")
            logcat { e.asLog() }
        }

        return START_STICKY
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

    private fun startTripSync() {
        Log.d(tag, "TripSyncService - startTripSync() Called()")
        tripSyncJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                Log.d(tag, "TripSyncService - startTripSync() imei - $imei")
                imei?.let { imeiNumber->
                    val unSyncedTripsEntities = tripDao?.getUnSyncedTrips()
                    unSyncedTripsEntities?.let {
                        val unSyncedTrips = convertToTripReports(it)
                        sendDataToPortal(unSyncedTrips, imeiNumber)
                    }
                } ?: run {
                    logcat { "Failed to send data to portal, IMEI is null" }
                }
                delay(5000L)
            }
        }
    }

    private fun stopTripSync() {
        tripSyncJob?.cancel()
    }

    private fun sendDataToPortal(unSyncedTrips: List<TripReport>, imei: String) {
        val feedData = createFeed(unSyncedTrips, imei)
        val mqttPacket = """
        {
            "type":"${Constants.TOPIC_TRIP_REPORT}",
            "data":${feedData}
        }
        """.trimIndent()
        logcat { "sendDataToPortal $feedData" }

        val messageSize = mqttPacket.toByteArray().size
        showNotification("Publishing message of $messageSize Bytes") //Show only important data like seq no.
        portalMQTTClient.publish(Constants.TOPIC_TRIP_REPORT, mqttPacket) { isSuccess ->
            val status = if (isSuccess) "Published" else "Failed to publish"
            if(isSuccess) {
                unSyncedTrips.map {
                    tripDao?.markTripAsSynced(it.tripId)
                }
            }
            Log.d(tag, "Send Data to portal Result - $status \n $feedData")
            showNotification("$status message with $messageSize Bytes")
            logHelper.log(
                tag = "Send Data to portal",
                message = "sendDataToPortal - $status \n $feedData"
            )
        }
    }

    private fun createFeed(unSyncedTrips: List<TripReport>, imei: String): String {

        val payload = TripReportPayload(imei = imei, trips = unSyncedTrips)
        val tripReportFeed = Json.encodeToString(payload)
        return tripReportFeed

    }

    override fun onDestroy() {

        stopTripSync()
        handler = null
        externalScope.cancel()
        logcat(TAG) { "UARTService onDestroy" }

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
        const val TAG = "UART"
    }
}