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
import com.nlevcluster.sender.service.NOTIFICATION_ID
import com.nlevcluster.sender.trip.TripStatus
import com.nlevcluster.sender.trip.VehicleState
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportDatabase
import com.nlevcluster.sender.trip.db.TripReportEntity
import com.nlevcluster.sender.trip.db.convertToTripReports
import com.nlevcluster.sender.util.MQTTUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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
    lateinit var portalMQTTClient: MQTTUtil

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

    private var tripId = 0
    private var maxCanSpeed: Int = 0
    private var totalIdleTime: Long = 0L
    private var startingIdleTime: Long = 0L
    private var endingIdleTime: Long = 0L
    private var startSoc: Int = 0
    private var endSoc: Int = 0
    private var startOdometer: Double = 0.0
    private var endOdometer: Double = 0.0
    private var tripStartTimeDurationMs: Long = 0
    private var tripEndTimeDurationMs: Long = 0
    private var isTripStarted = false
    private var isTripValid = false
    private var isIdleValid = false

    private var gpsSpeed = 0.0f
    private var tripDao: TripReportDao? = null
    private var imei: String? = null
    private var tripSyncJob: Job? = null

    // StateFlows for reactive state management
    private val _vehicleState = MutableStateFlow(VehicleState.UNKNOWN)
    private val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()

    private val _canFeed = MutableStateFlow<CanFeed?>(null)
    private val canFeed: StateFlow<CanFeed?> = _canFeed.asStateFlow()

    private val _tripStatus = MutableStateFlow(TripStatus.NOT_STARTED)

    private lateinit var locationManager: LocationManager
    private val locationListener = LocationListener { location ->
        gpsSpeed = location.speed
    }

    init {
        setupTripProcessing()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            uartService = IUartService.Stub.asInterface(binder)
            isBound = true
            logcat(tag) { "Service connected" }
            startObservingUARTData()
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            logcat(tag) { "Service onBindingDied" }
            uartService = null
            isBound = false
            scheduleReconnect()
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            logcat(tag) { "Service onNullBinding" }
            uartService = null
            isBound = false
            scheduleReconnect()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            uartService = null
            logcat(tag) { "Service disconnected" }
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
                logcat(tag) { "Attempting to reconnect to UART service" }
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
                .collect { canData ->
                    updateVehicleState(determineVehicleState(canData))
                    updateCanFeed(canData)
                }
        }
    }

    // Public methods to update state
    private fun updateVehicleState(newState: VehicleState) {
        _vehicleState.value = newState
    }

    private fun updateCanFeed(newCanFeed: CanFeed) {
        _canFeed.value = newCanFeed
    }

    private fun setupTripProcessing() {
        // Combine vehicle state and canFeed to process trip logic
        externalScope.launch {
            combine(
                vehicleState,
                canFeed.filterNotNull().distinctUntilChanged()
            ) { state, feed ->
                Pair(state, feed)
            }.collect { (state, feed) ->
                processTrip(state, feed)
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

    private suspend fun processTrip(vehicleState: VehicleState, canFeed: CanFeed) {
        try {
            when (vehicleState) {
                VehicleState.RUNNING -> handleRunningState(canFeed)
                VehicleState.IDLE -> handleIdleState()
                VehicleState.STOP -> handleStopState(canFeed)
                else -> {
                    // No action for other states
                }
            }
        } catch (e: Exception) {
            //handleTripException(canFeed, e)
        }
    }

    private suspend fun handleRunningState(canFeed: CanFeed) {
        if (!isTripStarted) {
            initializeTrip(canFeed)
        }

        if (canFeed.odometer.currentRideInKm > 0.500) {
            _tripStatus.value = TripStatus.IN_PROGRESS

            if (!isTripValid) {
                insertTripToDatabase(canFeed)
            }
        }
    }

    private fun handleIdleState() {
        // No action required for idle state
    }

    private fun handleStopState(canFeed: CanFeed) {
        if (_tripStatus.value == TripStatus.IN_PROGRESS && isTripStarted && isTripValid) {
            completeTrip(canFeed)
        }
    }

    private fun initializeTrip(canFeed: CanFeed) {
        startSoc = canFeed.battery.remainingPercent
        startOdometer = canFeed.odometer.totalKm
        tripStartTimeDurationMs = System.currentTimeMillis()
        isTripStarted = true
    }

    private suspend fun insertTripToDatabase(canFeed: CanFeed) {
        externalScope.launch(Dispatchers.IO) {
            tripId = tripDao?.insertTrip(
                TripReportEntity(
                    tripID = UUID.randomUUID().toString(),
                    startSOC = canFeed.battery.remainingPercent,
                    startOdometer = startOdometer,
                    tripStartTimeDurationMs = tripStartTimeDurationMs,
                    tripStatus = _tripStatus.value
                )
            )?.toInt() ?: 0

            isTripValid = true
        }.join() // Wait for database operation to complete
    }

    private fun completeTrip(canFeed: CanFeed) {
        endSoc = canFeed.battery.remainingPercent
        endOdometer = canFeed.odometer.totalKm
        tripEndTimeDurationMs = System.currentTimeMillis()

        _tripStatus.value = TripStatus.COMPLETED

        externalScope.launch(Dispatchers.IO) {
            val existing = tripDao?.getTripByTripId(tripId)
            val updated = existing?.copy(
                endSOC = endSoc,
                endOdometer = endOdometer,
                tripEndTimeDurationMs = tripEndTimeDurationMs,
                tripStatus = _tripStatus.value
            )
            updated?.let { tripDao?.updateTrip(it) }

            // Start trip sync after database update
            startTripSync()

            // Reset trip state
            resetTripState()
        }
    }

    private fun resetTripState() {
        _tripStatus.value = TripStatus.NOT_STARTED
        startSoc = 0
        startOdometer = 0.0
        tripStartTimeDurationMs = 0
        isTripStarted = false
        isTripValid = false
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

        tripSyncJob = externalScope.launch {
            imei?.let { imeiNumber ->
                val unSyncedTripsEntities = tripDao?.getUnSyncedTrips()
                unSyncedTripsEntities?.let {
                    val unSyncedTrips = convertToTripReports(it)
                    unSyncedTrips.forEach { trip ->
                        sendDataToPortal(trip, imeiNumber)
                    }
                }
            } ?: run {
                logcat { "Failed to send data to portal, IMEI is null" }
            }
        }
    }

    private fun stopTripSync() {
        tripSyncJob?.cancel()
    }

    private fun sendDataToPortal(trip: TripReport, imei: String) {
        val feedData = createFeed(trip, imei)
        val mqttPacket = """
        {
            "type":"${Constants.TOPIC_TRIP_REPORT}",
            "data":${feedData}
        }
        """.trimIndent()
        logcat { "sendDataToPortal $mqttPacket" }

        val messageSize = mqttPacket.toByteArray().size
        showNotification("Publishing message of $messageSize Bytes") //Show only important data like seq no.
        portalMQTTClient.publish(Constants.TOPIC_TRIP_REPORT, mqttPacket) { isSuccess ->
            val status = if (isSuccess) "Published" else "Failed to publish"
            if (isSuccess) {
                tripDao?.markTripAsSynced(trip.tripId)
            }
            showNotification("$status message with $messageSize Bytes")
            logHelper.log(
                tag = "Send Data to portal",
                message = "sendDataToPortal - $status \n $feedData"
            )
        }
    }

    private fun createFeed(trip: TripReport, imei: String): String {
        val payload = TripReportPayload(imei = imei, trip = trip)
        val tripReportFeed = Json.encodeToString(payload)
        return tripReportFeed
    }

    override fun onDestroy() {
        handler = null
        handler = null
        externalScope.cancel()

        logcat(tag) { "UARTService onDestroy" }
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
        private const val RECONNECT_DELAY = 1000L // 1 second
        private const val SERIAL_SERVICE_INTENT =
            "com.nlevcluster.serialservice.BIND_UART_SERVICE"
        private const val SERIAL_SERVICE_PACKAGE_NAME = "com.nlevcluster.serialservice"
    }
}