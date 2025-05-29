package com.nlevcluster.sender.trip.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.nlevcluster.data_processor.IUartCallback
import com.nlevcluster.data_processor.IUartService
import com.nlevcluster.sender.R
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.IgnitionStatus
import com.nlevcluster.sender.model.TripReport
import com.nlevcluster.sender.model.TripReportPayload
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.service.INTERVAL_IN_MILLIS
import com.nlevcluster.sender.service.NOTIFICATION_ID
import com.nlevcluster.sender.trip.TripStatus
import com.nlevcluster.sender.trip.VehicleState
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.asLog
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class TripProcessingService : Service() {

    @Inject
    lateinit var logHelper: LogHelper

    @Inject
    lateinit var nlDataStore: NLDataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val tag: String = this.javaClass.simpleName
    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationTitle = "NL-Sender - Trip Processing"
    private val notificationChannel = "TRIP_PROCESSING"
    private var notificationManager: NotificationManager? = null
    private var isNotificationChannelCreated = false
    private var handler: Handler? = null

    private var uartService: IUartService? = null
    private var serialListenerJob: Job? = null
    private var reconnectJob: Job? = null
    private var isBound = false

    private var tripStatus: TripStatus = TripStatus.NOT_STARTED
    private var tripId = 0
    private var startSoc: Int = 0
    private var endSoc: Int = 0
    private var startOdometer: Double = 0.0
    private var endOdometer: Double = 0.0
    private var tripStartTimeDurationMs: Long = 0
    private var tripEndTimeDurationMs: Long = 0
    private var isTripStarted = false
    private var isTripValid = false

    private var gpsSpeed = 0.0f
    private var tripDao: TripReportDao? = null


    private var imei: String? = null

    @Inject
    lateinit var portalMQTTClient: MQTTUtil

    private var tripSyncJob: Job? = null


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            uartService = IUartService.Stub.asInterface(binder)
            isBound = true
            logcat(TAG) { "Service connected" }
            startObservingUARTData()
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

    private lateinit var locationManager: LocationManager
    private val locationListener = LocationListener { location ->
        gpsSpeed = location.speed
    }

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
                    val vehicleState = determineVehicleState(canData)
                    processTrip(vehicleState, canData)
                }
        }
    }

    private fun determineVehicleState(canFeed: CanFeed): VehicleState {
        val canSpeed = canFeed.transmission.speed
        val ignition = canFeed.ignitionStatus

        return when (ignition) {
            IgnitionStatus.ON -> {
                if (canSpeed > 0) {
                    VehicleState.RUNNING
                } else if (canSpeed == 0) {
                    VehicleState.IDLE
                } else {
                    VehicleState.UNKNOWN
                }
            }

            IgnitionStatus.OFF -> {
                if (canSpeed == 0) {
                    VehicleState.STOP
                } else {
                    VehicleState.UNKNOWN
                }
            }
        }
    }

    private fun processTrip(vehicleState: VehicleState, canFeed: CanFeed) {

        try {
            when (vehicleState) {
                VehicleState.RUNNING -> {
                    if (!isTripStarted) {
                        startSoc = canFeed.battery.remainingPercent
                        startOdometer = canFeed.odometer.totalKm
                        tripStartTimeDurationMs = System.currentTimeMillis()
                        isTripStarted = !isTripStarted
                    }

                    if (canFeed.odometer.currentRideInKm > 0.500) {
                        tripStatus = TripStatus.IN_PROGRESS
                        if (!isTripValid) {
                            externalScope.launch(Dispatchers.IO) {
                                tripId = tripDao?.insertTrip(
                                    TripReportEntity(
                                        tripID = UUID.randomUUID().toString(),
                                        startSOC = canFeed.battery.remainingPercent,
                                        startOdometer = startOdometer,
                                        tripStartTimeDurationMs = tripStartTimeDurationMs,
                                        tripStatus = tripStatus
                                    )
                                )?.toInt() ?: 0
                                isTripValid = !isTripValid
                            }
                        }
                    }
                }

                VehicleState.IDLE -> {
                    // No Action
                }

                VehicleState.STOP -> {
                    if (tripStatus == TripStatus.IN_PROGRESS) {
                        if (isTripStarted && isTripValid) {
                            endSoc = canFeed.battery.remainingPercent
                            endOdometer = canFeed.odometer.totalKm
                            tripEndTimeDurationMs = System.currentTimeMillis()

                            tripStatus = TripStatus.COMPLETED
                            val existing = tripDao?.getTripByTripId(tripId)
                            val updated = existing?.copy(
                                endSOC = endSoc,
                                endOdometer = endOdometer,
                                tripEndTimeDurationMs = tripEndTimeDurationMs,
                                tripStatus = tripStatus
                            )
                            val updateStatus = updated?.let { tripDao?.updateTrip(it) }
                            startTripSync()

                            tripStatus = TripStatus.NOT_STARTED
                            startSoc = 0
                            startOdometer = 0.0
                            tripStartTimeDurationMs = 0
                            isTripStarted = false
                            isTripValid = false
                        }
                    }
                }

                else -> {
                    //No Action
                }

            }

        } catch (e: Exception) {
            if (isTripStarted && isTripValid) {
                val existing = tripDao?.getTripByTripId(tripId)
                val updated = existing?.copy(
                    endSOC = canFeed.battery.range,
                    endOdometer = canFeed.odometer.totalKm,
                    tripEndTimeDurationMs = System.currentTimeMillis(),
                    tripStatus = TripStatus.FAILED
                )
                val updateStatus = updated?.let { tripDao?.updateTrip(it) }
            }
        }

    }

    override fun onCreate() {
        super.onCreate()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener
            )
        } else {
            Log.e("GPS", "Location permission not granted")
        }

        tripDao = TripReportDatabase.getInstance(applicationContext).tripReportDao()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNLSenderNotificationChannel()
        bindToSerialService()
    }

    private fun initPortalMQTTClient(imei: String) {
        externalScope.launch {
            portalMQTTClient.connect(
                url = nlDataStore.mqttFeedUrl() ?: Constants.MQTT_PORTAL_URL,
                port = Constants.MQTT_PORTAL_PORT,
                clientId = imei + this@TripProcessingService.packageName
            ) { isConnected ->
                if (isConnected) {
                    logHelper.log(
                        tag = tag,
                        message = "initMQTTClient - MQTT connected to Cloud."
                    )
                    showNotification("MQTT connected to Cloud")

                } else {
                    logHelper.log(
                        tag = tag,
                        message = "initMQTTClient - MQTT failed to connect to Cloud.",
                        type = LogType.ERROR
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val initialNotification =
                createNLSenderNotification("NL-Sender ", "Trip processing service started")
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

                logcat { "Received imei Number - $imei" }
                //monitorMQTTConnection(imeiValue)
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
        tripSyncJob = CoroutineScope(Dispatchers.IO).launch {
            imei?.let { imeiNumber ->
                val unSyncedTripsEntities = tripDao?.getUnSyncedTrips()
                unSyncedTripsEntities?.let {
                    val unSyncedTrips = convertToTripReports(it)
                    sendDataToPortal(unSyncedTrips, imeiNumber)
                }
            } ?: run {
                logcat { "Failed to send data to portal, IMEI is null" }
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
            if (isSuccess) {
                unSyncedTrips.map {
                    tripDao?.markTripAsSynced(it.tripId)
                }
            }
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
        handler = null
        handler = null
        externalScope.cancel()

        logcat(TAG) { "UARTService onDestroy" }
        serialListenerJob?.cancel()
        unbindService(serviceConnection)
        stopTripSync()
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
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
        private const val RECONNECT_DELAY = 1000L // 1 second
        private const val SERIAL_SERVICE_INTENT =
            "com.nlevcluster.serialservice.BIND_UART_SERVICE"
        private const val SERIAL_SERVICE_PACKAGE_NAME = "com.nlevcluster.serialservice"
    }
}