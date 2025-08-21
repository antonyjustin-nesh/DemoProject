package com.nlevcluster.sender.report.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.ContentObserver
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.nlevcluster.aidl.IProcessedFeedCallback
import com.nlevcluster.aidl.IUartService
import com.nlevcluster.sender.R
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.ChargeTelemetryData
import com.nlevcluster.sender.model.ChargingSessionInfoWrapper
import com.nlevcluster.sender.model.ChargingSessionPayload
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.LocationInfo
import com.nlevcluster.sender.model.ModeStats
import com.nlevcluster.sender.model.TripDataInfoWrapper
import com.nlevcluster.sender.model.TripRecordsPayload
import com.nlevcluster.sender.model.TripTelemetryData
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.report.VehicleState
import com.nlevcluster.sender.report.charge.db.ChargingReportDao
import com.nlevcluster.sender.report.charge.db.ChargingReportDatabase
import com.nlevcluster.sender.report.charge.db.ChargingSessionEntity
import com.nlevcluster.sender.report.charge.mapper.convertToChargingTelemetryData
import com.nlevcluster.sender.report.trip.ChargingData
import com.nlevcluster.sender.report.trip.TripStatus
import com.nlevcluster.sender.report.trip.db.TripReportDao
import com.nlevcluster.sender.report.trip.db.TripReportDatabase
import com.nlevcluster.sender.report.trip.db.TripReportEntity
import com.nlevcluster.sender.report.trip.mapper.convertToTripReports
import com.nlevcluster.sender.service.NOTIFICATION_ID
import com.nlevcluster.sender.util.MQTTUtil
import com.nlevcluster.sender.util.determineVehicleState
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
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class ReportProcessingService : Service() {

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
    private var lastTimestampHarshAcceleration: Long = 0
    private var lastTimestampHarshBraking: Long = 0
    private val speedDeltaThreshold = 10
    private val speedDeltaDurationThreshold = 3000
    private var harshAccelerationAlertCount = 0
    private var harshBrakingAlertCount = 0

    private var SPEED_LIMIT = 50
    private var isSpeedOverLimit = false
    private var lastOverSpeedTime = 0L
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


    private val isChargingValid = AtomicBoolean(false)

    private var chargingSessionId = 0
    private var endingChargingTimeMillis: Long = 0L
    private var chargingEndSoc: Int = 0
    private var totalVoltage = 0
    private var sampleVoltageCount = 0
    private var averageVoltage = 0.0
    private var totalCurrent = 0
    private var sampleCurrentCount = 0
    private var averageCurrent = 0.0
    private var maxBatteryCurrent = 0.0
    private var maxBatteryVoltage = 0.0
    private var lastKnownSoc: Int? = null
    private var currentSoc: Int = 0

    private var tripDao: TripReportDao? = null
    private var chargingDao: ChargingReportDao? = null
    private var imei: String? = null
    private var tripSyncJob: Job? = null
    private var chargingSyncJob: Job? = null

    private val _tripStatus = MutableStateFlow(TripStatus.NOT_STARTED)
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
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
        val callback = object : IProcessedFeedCallback.Stub() {
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

        uartService?.registerProcessedFeedCallback(callback)

        awaitClose {
            uartService?.unregisterProcessedFeedCallback(callback)
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
                }
                .flatMapLatest { canData ->
                    val vehicleState = flowOf(determineVehicleState(canData))
                    combine(
                        vehicleState,
                        flowOf(canData)
                    ) { state, feed ->
                        logHelper.log(
                            tag = "vehicleState",
                            type = LogType.DEBUG,
                            message = "vehicleState - $state"
                        )
                        processTrip(state, feed)
                    }
                }.collect()
        }
    }

    private suspend fun processTrip(vehicleState: VehicleState, canFeed: CanFeed) {
        try {
            currentSoc = canFeed.battery.remainingPercent
            if (lastKnownSoc != null && lastKnownSoc != currentSoc) {
                val chargingData = json.encodeToString(
                    ChargingData(
                        vehicleState,
                        System.currentTimeMillis(),
                        canFeed.battery.remainingPercent
                    )
                )
                sendChargingData(chargingData)
            }
            lastKnownSoc = currentSoc

            logHelper.log(
                tag = tag,
                type = LogType.DEBUG,
                message = "canFeed - $canFeed"
            )

            updateModeStats(canFeed.evMode, canFeed.odometer.currentRideInKm)
            when (vehicleState) {
                VehicleState.RUNNING -> handleRunningState(canFeed)
                VehicleState.IDLE -> handleIdleState(canFeed)
                VehicleState.STOP -> handleStopState(canFeed)
                VehicleState.CHARGING -> handleChargingState(canFeed)
                else -> {
                    // No action for other states
                }
            }
        } catch (e: Exception) {
            handleTripException(canFeed, e)
        }
    }

    private fun handleChargingState(canFeed: CanFeed) {
        externalScope.launch {
            if (isChargingValid.compareAndSet(false, true)) {
                entryStartChargeSession(canFeed)
                val chargingData = json.encodeToString(
                    ChargingData(
                        VehicleState.CHARGING,
                        System.currentTimeMillis(),
                        canFeed.battery.remainingPercent
                    )
                )
                sendChargingData(chargingData)
            }
        }
        handleMaxBatteryCurrent(canFeed)
        handleMaxBatteryVoltage(canFeed)
    }

    private suspend fun handleRunningState(canFeed: CanFeed) {

        if (!isTripStarted) {
            initializeTrip(canFeed)
        }
        if (isIdleValid) {
            handleIdleTime()
        }

        if (isChargingValid.get()) {
            isChargingValid.set(false)
            handleChargingDuration(canFeed)
            val chargingData = json.encodeToString(
                ChargingData(
                    VehicleState.RUNNING,
                    System.currentTimeMillis(),
                    canFeed.battery.remainingPercent
                )
            )
            sendChargingData(chargingData)
        }

        detectSpeedAlert(canFeed.transmission.speed)
        if (detectHarshAccelerationAlert(canFeed.transmission.speed)) {
            harshAccelerationAlertCount += 1
        }
        handleMaxSpeed(canFeed.transmission.speed)
        updateRunningAverage(canFeed.transmission.speed)
        if (canFeed.odometer.currentRideInKm > TRIP_MIN_DISTANCE) {
            _tripStatus.value = TripStatus.IN_PROGRESS
            if (!isTripValid) {
                insertTripToDatabase(canFeed)
            }
        }

    }

    private fun handleIdleState(canFeed: CanFeed) {

        if (!isTripStarted) {
            initializeTrip(canFeed)
        }

        if (!isIdleValid) {
            startingIdleTime = System.currentTimeMillis()
            isIdleValid = !isIdleValid
        }

        if (isChargingValid.get()) {
            isChargingValid.set(false)
            handleChargingDuration(canFeed)
            val chargingData = json.encodeToString(
                ChargingData(
                    VehicleState.IDLE,
                    System.currentTimeMillis(),
                    canFeed.battery.remainingPercent
                )
            )
            sendChargingData(chargingData)
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

    private fun handleStopState(canFeed: CanFeed) {

        if (isChargingValid.get()) {
            isChargingValid.set(false)
            handleChargingDuration(canFeed)
            val chargingData = json.encodeToString(
                ChargingData(
                    VehicleState.STOP,
                    System.currentTimeMillis(),
                    canFeed.battery.remainingPercent
                )
            )
            sendChargingData(chargingData)
        }

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
            logHelper.log(
                type = LogType.ERROR,
                message = "Exception occurred when handling trip - ${exception.asLog()} updateStatus - $updateStatus"
            )
        }
    }

    private fun initializeTrip(canFeed: CanFeed) {
        startSoc = canFeed.battery.remainingPercent
        startOdometer = canFeed.odometer.totalKm
        tripStartTimeDurationMs = System.currentTimeMillis()
        isTripStarted = true
    }

    private fun handleMaxSpeed(speed: Int) {
        if (speed > maxCanSpeed) {
            maxCanSpeed = speed
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


    private fun handleChargingDuration(canFeed: CanFeed) {
        entryEndChargingSession(canFeed = canFeed)
    }

    private fun detectSpeedAlert(currentSpeed: Int) {

        if (currentSpeed > SPEED_LIMIT) {
            if (!isSpeedOverLimit) {
                isSpeedOverLimit = true
                speedAlertCount++
            }
        } else {
            isSpeedOverLimit = false
            lastOverSpeedTime = 0L
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

    private fun getAverageCurrent(): Double {
        return if (sampleCurrentCount > 0) (totalCurrent / sampleCurrentCount).toDouble() else 0.0
    }

    private fun getAverageVoltage(): Double {
        return if (sampleVoltageCount > 0) (totalVoltage / sampleVoltageCount).toDouble() else 0.0
    }


    private fun handleMaxBatteryCurrent(canFeed: CanFeed) {
        if (abs(canFeed.battery.current) > maxBatteryCurrent) {
            maxBatteryCurrent = abs(canFeed.battery.current)
        }
    }

    private fun handleMaxBatteryVoltage(canFeed: CanFeed) {
        if (canFeed.battery.volt > maxBatteryVoltage) {
            maxBatteryVoltage = canFeed.battery.volt
        }
    }


    private suspend fun insertTripToDatabase(canFeed: CanFeed) {
        externalScope.launch(Dispatchers.IO) {
            tripId = tripDao?.insertTrip(
                TripReportEntity(
                    startSOC = canFeed.battery.remainingPercent,
                    startOdometer = startOdometer,
                    tripStartTimeDurationMs = tripStartTimeDurationMs,
                    tripStatus = _tripStatus.value,
                    startTripLocationLat = canFeed.currentLocation?.latitude ?: 0.0,
                    startTripLocationLon = canFeed.currentLocation?.longitude ?: 0.0
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
                modeStats = modeStatsJson,
                endTripLocationLat = canFeed.currentLocation?.latitude ?: 0.0,
                endTripLocationLon = canFeed.currentLocation?.longitude ?: 0.0
            )
            updated?.let { tripDao?.updateTrip(it) }
            startTripSync()
            resetTripState()
        }
    }

    private fun entryStartChargeSession(canFeed: CanFeed) {
        externalScope.launch(Dispatchers.IO) {
            val startChargingTimeMs = System.currentTimeMillis()
            val startSOC = canFeed.battery.remainingPercent

            chargingSessionId = chargingDao?.insertChargingSession(
                ChargingSessionEntity(
                    startSOC = startSOC,
                    batteryCurrent = canFeed.battery.current,
                    chargingStartTimeDurationMs = startChargingTimeMs,
                    startChargingLocationLat = canFeed.currentLocation?.latitude ?: 0.0,
                    startChargingLocationLon = canFeed.currentLocation?.longitude ?: 0.0,
                )
            )?.toInt() ?: 0
        }
    }

    private fun entryEndChargingSession(canFeed: CanFeed) {
        endingChargingTimeMillis = System.currentTimeMillis()
        chargingEndSoc = canFeed.battery.remainingPercent
        val chargeRate = (canFeed.battery.volt * canFeed.battery.current) / 1000
        averageVoltage = getAverageVoltage()
        averageCurrent = getAverageCurrent()
        externalScope.launch(Dispatchers.IO) {
            val existing = chargingDao?.getChargingSessionBySessionId(chargingSessionId)
            val updated = existing?.copy(
                //batteryCurrent = canFeed.battery.current,
                batteryVoltage = canFeed.battery.volt,
                endSOC = chargingEndSoc,
                chargeRate = chargeRate,
                batteryTemperature = canFeed.battery.temperature.toFloat(),
                maxBatteryCurrent = maxBatteryCurrent,
                averageCurrent = getAverageCurrent(),
                maxBatteryVoltage = maxBatteryVoltage,
                averageVoltage = averageVoltage,
                chargingEndTimeDurationMs = endingChargingTimeMillis,
                endChargingLocationLat = canFeed.currentLocation?.latitude ?: 0.0,
                endChargingLocationLon = canFeed.currentLocation?.longitude ?: 0.0,
            )
            updated?.let { chargingDao?.updateChargingSession(it) }
            startUnSyncedChargingSessionsUpload()
            resetChargingState()
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

    private fun resetChargingState() {
        chargingEndSoc = 0
        endingChargingTimeMillis = 0L
        totalVoltage = 0
        sampleVoltageCount = 0
        averageVoltage = 0.0
        totalCurrent = 0
        sampleCurrentCount = 0
        averageCurrent = 0.0
        maxBatteryCurrent = 0.0
        maxBatteryVoltage = 0.0
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
        chargingDao = ChargingReportDatabase.getInstance(applicationContext).chargingReportDao()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNLSenderNotificationChannel()
        bindToSerialService()
        observeClearVIPChanges()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {

            val initialNotification =
                createNLSenderNotification("NL-Sender ", "Trip processing service started")
            startForeground(NOTIFICATION_ID, initialNotification)

            val sharedPref = getSharedPreferences("properties", MODE_PRIVATE)
            imei = intent?.getStringExtra("IMEI") ?: sharedPref.getString("imei", null)

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
                    val unSyncedTrips = convertToTripReports(imeiNumber, it)
                    unSyncedTrips.forEach { trip ->
                        sendDataToPortal(
                            createTripFeed(trip),
                            Constants.TOPIC_TRIP_REPORT
                        ) { isSuccess, messageSize, feedData ->
                            val status = if (isSuccess) "Published" else "Failed to publish"
                            if (isSuccess) {
                                tripDao?.markTripAsSynced(tripId)
                            }
                            showNotification("$status message with $messageSize Bytes")
                            logHelper.log(
                                tag = "Send Trip Data to portal",
                                message = "sendDataToPortal - $status \n $feedData"
                            )
                        }
                    }
                }
            } ?: run {
                logcat { "" }
                logHelper.log(
                    type = LogType.ERROR,
                    tag = tag,
                    message = "Failed to send trip data to portal, IMEI is null"
                )
            }
        }
    }

    private fun stopTripSync() {
        tripSyncJob?.cancel()
    }

    private fun startUnSyncedChargingSessionsUpload() {
        chargingSyncJob = externalScope.launch {
            imei?.let { imeiNumber ->
                val unSyncedChargingSessionsEntities = chargingDao?.getUnSyncedChargingSessions()
                unSyncedChargingSessionsEntities?.let {
                    val unSyncedChargingSessions = convertToChargingTelemetryData(imeiNumber, it)
                    unSyncedChargingSessions.forEach { chargingSession ->
                        sendDataToPortal(
                            createChargingSessionsFeed(chargingSession),
                            Constants.TOPIC_CHARGING_REPORT
                        ) { isSuccess, messageSize, feedData ->
                            val status = if (isSuccess) "Published" else "Failed to publish"
                            if (isSuccess) {
                                chargingDao?.markChargingSessionAsSynced(chargingSessionId)
                            }
                            showNotification("$status message with $messageSize Bytes")
                            logHelper.log(
                                tag = "Send Charging Data to portal",
                                message = "sendDataToPortal - $status \n $feedData"
                            )
                        }
                    }
                }
            } ?: run {
                logcat { "Failed to send trip data to portal, IMEI is null" }
            }
        }
    }

    private fun cancelChargingSessionUploads() {
        tripSyncJob?.cancel()
    }

    private fun sendDataToPortal(
        feedData: String,
        topic: String,
        onComplete: (isSuccess: Boolean, messageSize: Int, feedData: String) -> Unit = { b: Boolean, i: Int, s: String -> }
    ) {
        logcat { "sendDataToPortal $feedData" }

        val messageSize = feedData.toByteArray().size
        showNotification("Publishing message of $messageSize Bytes") //Show only important data like seq no.
        portalMQTTClient.publish(topic, feedData) { isSuccess ->
            onComplete(isSuccess, messageSize, feedData)
        }
    }

    private fun sendChargingData(chargingSession: String) {
        val broadcastIntent = Intent().apply {
            action = CHARGING_DATA_ACTION_NAME
            putExtra(CHARGE_DATA, chargingSession)
            putExtra(TIME_STAMP, System.currentTimeMillis())
            setPackage(DISPLAY_PACKAGE_NAME)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun observeClearVIPChanges() {
        val clearVipUri = Uri.parse(CLEAR_VIP_URI)
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                startOdometer = 0.0
            }
        }
        contentResolver?.registerContentObserver(clearVipUri, true, contentObserver)
    }


    private fun createTripFeed(trip: TripDataInfoWrapper): String {
        val tripReportFeed = Json.encodeToString(trip)
        return tripReportFeed
    }

    private fun createChargingSessionsFeed(
        chargingSession: ChargingSessionInfoWrapper,
    ): String {
        val chargingSessionFeed = Json.encodeToString(chargingSession)
        return chargingSessionFeed
    }

    override fun onDestroy() {
        handler = null
        externalScope.cancel()

        logcat(tag) { "UARTService onDestroy" }
        serialListenerJob?.cancel()
        unbindService(serviceConnection)
        stopTripSync()
        cancelChargingSessionUploads()
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

        private const val DISPLAY_PACKAGE_NAME = "com.nlevcluster.display"
        private const val CHARGING_DATA_ACTION_NAME =
            "com.nlevcluster.display.ACTION_SEND_CHARGE_DATA"
        const val CLEAR_VIP_URI = "content://com.nlevcluster.nl_admin.vip_provider/clear_all_data"
        private const val CHARGE_DATA = "CHARGE_DATA"
        private const val TIME_STAMP = "TIME_STAMP"
        private const val TRIP_MIN_DISTANCE = 0.500
    }
}