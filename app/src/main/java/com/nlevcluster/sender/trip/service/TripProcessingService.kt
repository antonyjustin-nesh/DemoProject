package com.nlevcluster.sender.trip.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nlevcluster.data_processor.IUartCallback
import com.nlevcluster.data_processor.IUartService
import com.nlevcluster.sender.R
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.IgnitionStatus
import com.nlevcluster.sender.model.LocationInfo
import com.nlevcluster.sender.model.ModeStats
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.asLog
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class TripProcessingService : Service() {

    @Inject
    lateinit var logHelper: LogHelper

    @Inject
    @ApplicationContext
    lateinit var context: Context

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
    private var currentIdleTime: Long = 0L
    private var startingIdleTime: Long = 0L
    private var endingIdleTime: Long = 0L
    private var startSoc: Int = 0
    private var endSoc: Int = 0
    private var startOdometer: Double = 0.0
    private var endOdometer: Double = 0.0
    private var tripStartTimeDurationMs: Long = 0
    private var tripEndTimeDurationMs: Long = 0

    private var totalSpeed = 0
    private var sampleCount = 0
    private var averageSpeed = 0
    private var harshAccelerationPreviousSpeed = 0
    private var harshBrakingPreviousSpeed = 0
    private var lastTimestampHarshAcceleration: Long = 0
    private var lastTimestampHarshBraking: Long = 0
    private val speedDeltaThreshold = 10
    private val speedDeltaDurationThreshold = 3000
    private var harshAccelerationAlertCount = 0
    private var harshBrakingAlertCount = 0

    private var SPEED_LIMIT = 50
    private var SPEED_LIMIT_ALERT_THRESHOLD = 20_000
    private var isSpeedOverLimit = false
    private var lastOverSpeedTime = 0L
    private var lastAlertTime = 0L
    private var speedAlertCount = 0

    private val IDLE_THRESHOLD = 10_000
    private var idleAlertCount = 0
    private var idleAlertCheckFlag = false

    private val modeStats: MutableMap<EvMode, ModeStats> = enumValues<EvMode>()
        .associateWith { ModeStats() }
        .toMutableMap()
    private var lastUpdateTime: Long = 0L
    private var lastOdometer: Double = 0.0
    private var previousMode: EvMode? = null
    private var modeStatsJson = ""

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

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    init {
        //setupTripProcessing()
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

    @SuppressLint("MissingPermission")
    val currentLocation: Flow<Location> = callbackFlow {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            logHelper.log(LogType.INFO, "GPS provider disabled", tag)
            close()
            return@callbackFlow
        }

        val listener = LocationListener { location ->
            logHelper.log(LogType.DEBUG, "Location update success $location", tag)
            trySend(location).isSuccess
        }

        withContext(Dispatchers.Main) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                listener
            )
        }

        awaitClose {
            locationManager.removeUpdates(listener)
            logHelper.log(LogType.INFO, "Location update stopped", tag)
        }
    }.catch { e ->
        logHelper.log(LogType.ERROR, "Location updates Failed ${e.asLog()}", tag)
    }.stateIn(
        externalScope,
        SharingStarted.Lazily,
        Location(null)
    )

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

//    private fun startObservingUARTData() {
//        serialListenerJob = externalScope.launch(Dispatchers.IO) {
//            serialServiceDataFlow
//                .catch {
//                    logHelper.log(
//                        tag = "startObservingUARTData",
//                        type = LogType.ERROR,
//                        message = "UART data error: $it"
//                    )
//                }
//                .conflate()
//                .combine(currentLocation) { canFeed, location ->
//                    canFeed.currentLocation = LocationInfo(
//                        latitude = location.latitude,
//                        longitude = location.longitude,
//                        accuracy = location.accuracy.toDouble(),
//                        speed = location.speed,
//                        altitude = location.altitude.toFloat()
//                    )
//                    canFeed
//                }
//                .collect { canData ->
//                    updateVehicleState(determineVehicleState(canData))
//                    updateCanFeed(canData)
//                }
//        }
//    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startObservingUARTData() {
        serialListenerJob = externalScope.launch(Dispatchers.IO) {
            serialServiceDataFlow
                .catch {
                    logHelper.log(
                        tag = "startObservingUARTData",
                        type = LogType.ERROR,
                        message = "UART data error: $it" // need to log stack trace
                    )
                }
                .conflate()
                .combine(currentLocation) { canFeed, location ->
                    canFeed.currentLocation = LocationInfo(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy.toDouble(),
                        speed = location.speed,
                        altitude = location.altitude.toFloat()
                    )
                    canFeed
                }.flatMapLatest { canData ->
                    val vehicleState = flowOf(determineVehicleState(canData))
                    combine(
                        vehicleState,
                        flowOf(canData)
                    ) { state, feed ->
                        processTrip(state, feed)
                    }
                }.collect()
        }
    }

    private fun updateVehicleState(newState: VehicleState) {
        _vehicleState.value = newState
    }

    private fun updateCanFeed(newCanFeed: CanFeed) {
        _canFeed.value = newCanFeed
    }

    private fun setupTripProcessing() {
        // Combine vehicle state and canFeed to process trip logic

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
            updateModeStats(canFeed.evMode, canFeed.odometer.currentRideInKm)
            when (vehicleState) {
                VehicleState.RUNNING -> handleRunningState(canFeed)
                VehicleState.IDLE -> handleIdleState(canFeed)
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
        if (isIdleValid) {
            handleIdleTime()
        }
        detectSpeedAlert(canFeed.transmission.speed)
        if (detectHarshAccelerationAlert(canFeed.transmission.speed)) {
            harshAccelerationAlertCount += 1
        }
        handleMaxSpeed(canFeed)
        updateRunningAverage(canFeed.transmission.speed)
        if (canFeed.odometer.currentRideInKm > 0.500) {
            _tripStatus.value = TripStatus.IN_PROGRESS
            if (!isTripValid) {
                insertTripToDatabase(canFeed)
            }
        }

    }

    private fun handleIdleState(canFeed: CanFeed) {
        if (canFeed.odometer.currentRideInKm > 0) {
            if (!isIdleValid) {
                startingIdleTime = System.currentTimeMillis()
                isIdleValid = !isIdleValid
            }

            val currentTime = System.currentTimeMillis()
            val currentIdleTime = currentTime - startingIdleTime
            if (!idleAlertCheckFlag) {
                if (currentIdleTime > IDLE_THRESHOLD) {
                    idleAlertCount += 1
                    idleAlertCheckFlag = true
                }
            }
        }
    }

    private fun handleStopState(canFeed: CanFeed) {
        if (_tripStatus.value == TripStatus.IN_PROGRESS && isTripStarted && isTripValid) {
            completeTrip(canFeed)
        } else {
            // Reset trip state
            resetTripState()
        }
    }

    private fun handleTripException(canFeed: CanFeed, exception: java.lang.Exception) {
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

    private fun initializeTrip(canFeed: CanFeed) {
        startSoc = canFeed.battery.remainingPercent
        startOdometer = canFeed.odometer.totalKm
        tripStartTimeDurationMs = System.currentTimeMillis()
        isTripStarted = true
    }

    private fun handleMaxSpeed(canFeed: CanFeed) {
        if (canFeed.transmission.speed > maxCanSpeed) {
            maxCanSpeed = canFeed.transmission.speed
        }
    }

    private fun updateRunningAverage(newSpeed: Int) {
        if (newSpeed > 0) {
            totalSpeed += newSpeed
            sampleCount++
        }
    }

    private fun getAverageSpeed(): Int {
        return if (sampleCount > 0) totalSpeed / sampleCount else 0
    }

    private fun handleIdleTime() {
        endingIdleTime = System.currentTimeMillis()
        currentIdleTime = (endingIdleTime - startingIdleTime)
        totalIdleTime += currentIdleTime
        idleAlertCheckFlag = false
        isIdleValid = !isIdleValid
    }

    private fun detectSpeedAlert(currentSpeed: Int) {

        val currentTime = System.currentTimeMillis()

        if (currentSpeed > SPEED_LIMIT) {
            if (!isSpeedOverLimit) {
                isSpeedOverLimit = true
                lastOverSpeedTime = currentTime
                lastAlertTime = currentTime
                speedAlertCount++
            } else {
                val elapsed = currentTime - lastAlertTime
                if (elapsed >= SPEED_LIMIT_ALERT_THRESHOLD) {
                    lastAlertTime = currentTime
                    speedAlertCount++
                }
            }
        } else {
            isSpeedOverLimit = false
            lastOverSpeedTime = 0L
            lastAlertTime = 0L
        }

    }

    private fun detectHarshAccelerationAlert(currentSpeed: Int): Boolean {

        val now = System.currentTimeMillis()
        val deltaTime = now - lastTimestampHarshAcceleration
        val deltaSpeed = currentSpeed - harshAccelerationPreviousSpeed
        harshAccelerationPreviousSpeed = currentSpeed

        return if (deltaSpeed >= speedDeltaThreshold && deltaTime >= speedDeltaDurationThreshold) {
            lastTimestampHarshAcceleration = now
            true
        } else {
            false
        }

    }

    private fun updateModeStats(currentMode: EvMode, currentOdometer: Double) {

        val currentTime = System.currentTimeMillis()

        if (lastUpdateTime == 0L) {
            lastUpdateTime = currentTime
            lastOdometer = currentOdometer
            previousMode = currentMode
            return
        }

        val duration = currentTime - lastUpdateTime
        val distance = currentOdometer - lastOdometer

        if (duration >= 0 && distance >= 0) {
            // Always update the previous mode (even if same)
            previousMode?.let { mode ->
                val stats = modeStats.getOrDefault(mode, ModeStats())
                stats.durationMs += duration
                stats.distanceKm += distance
                modeStats[mode] = stats
            }
        }

        // Prepare for next update
        lastUpdateTime = currentTime
        lastOdometer = currentOdometer
        previousMode = currentMode

    }


    private suspend fun insertTripToDatabase(canFeed: CanFeed) {
        externalScope.launch(Dispatchers.IO) {
            tripId = tripDao?.insertTrip(
                TripReportEntity(
                    startSOC = canFeed.battery.remainingPercent,
                    startOdometer = startOdometer,
                    tripStartTimeDurationMs = tripStartTimeDurationMs,
                    tripStatus = _tripStatus.value,
                    startTripLocationLat = canFeed.currentLocation?.latitude?:0.0,
                    startTripLocationLon = canFeed.currentLocation?.longitude?: 0.0
                )
            )?.toInt() ?: 0

            isTripValid = true
        }.join()
    }

    private fun completeTrip(canFeed: CanFeed) {
        endSoc = canFeed.battery.remainingPercent
        endOdometer = canFeed.odometer.totalKm
        tripEndTimeDurationMs = System.currentTimeMillis()
        averageSpeed = getAverageSpeed()
        val harshAlertsCount = harshAccelerationAlertCount + harshBrakingAlertCount
        modeStatsJson = Json.encodeToString(modeStats)
        if (isIdleValid) {
            endingIdleTime = System.currentTimeMillis()
            totalIdleTime += (endingIdleTime - startingIdleTime)
            idleAlertCheckFlag = false
        }

        _tripStatus.value = TripStatus.COMPLETED

        externalScope.launch(Dispatchers.IO) {
            val existing = tripDao?.getTripByTripId(tripId)
            val updated = existing?.copy(
                endSOC = endSoc,
                endOdometer = endOdometer,
                tripEndTimeDurationMs = tripEndTimeDurationMs,
                tripStatus = _tripStatus.value,
                maximumSpeedKmph = maxCanSpeed.toFloat(),
                idleDurationMs = totalIdleTime,
                idleAlertsCount = idleAlertCount,
                averageSpeedKmph = averageSpeed.toFloat(),
                speedAlertsCount = speedAlertCount,
                harshAlertsCount = harshAlertsCount,
                modeDuration = modeStatsJson,
                endTripLocationLat = canFeed.currentLocation?.latitude?:0.0,
                endTripLocationLon = canFeed.currentLocation?.longitude?: 0.0
            )
            updated?.let { tripDao?.updateTrip(it) }
            startTripSync()
            resetTripState()
        }
    }

    private fun resetTripState() {
        _tripStatus.value = TripStatus.NOT_STARTED
        startSoc = 0
        startOdometer = 0.0
        maxCanSpeed = 0
        tripStartTimeDurationMs = 0
        startingIdleTime = 0
        endingIdleTime = 0
        totalIdleTime = 0
        averageSpeed = 0

        totalSpeed = 0
        sampleCount = 0
        averageSpeed = 0
        harshBrakingAlertCount = 0
        harshAccelerationPreviousSpeed = 0
        lastTimestampHarshAcceleration = 0
        lastTimestampHarshBraking = 0
        harshAccelerationAlertCount = 0
        harshBrakingAlertCount = 0

        //Reset SpeedAlert
        speedAlertCount = 0
        isSpeedOverLimit = false
        lastOverSpeedTime = 0L
        lastAlertTime = 0L

        idleAlertCount = 0
        idleAlertCheckFlag = false

        //Reset ModeStats
        lastUpdateTime = 0L
        lastOdometer = 0.0
        modeStatsJson = ""
        previousMode = null
        clearModeDuration()

        isTripStarted = false
        isTripValid = false
        isIdleValid = false
    }

    private fun clearModeDuration() {
        modeStats.forEach { (_, stats) ->
            stats.durationMs = 0L
            stats.distanceKm = 0.0
        }
    }

    override fun onCreate() {
        super.onCreate()

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