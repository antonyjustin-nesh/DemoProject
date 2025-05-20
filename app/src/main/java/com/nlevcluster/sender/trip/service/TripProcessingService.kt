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
import androidx.lifecycle.lifecycleScope
import com.nlevcluster.data_processor.IUartCallback
import com.nlevcluster.data_processor.IUartService
import com.nlevcluster.sender.R
import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.IgnitionStatus
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.service.NOTIFICATION_ID
import com.nlevcluster.sender.trip.TripStatus
import com.nlevcluster.sender.trip.VehicleState
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportDatabase
import com.nlevcluster.sender.trip.db.TripReportEntity
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
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class TripProcessingService : Service() {

    @Inject
    lateinit var logHelper: LogHelper

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

    var tripStatus: TripStatus = TripStatus.NOT_STARTED
    var tripId = 0
    var startSoc: Int = 0
    var endSoc: Int = 0
    var startOdometer: Double = 0.0
    var endOdometer: Double = 0.0
    var tripStartTimeDurationMs: Long = 0
    var tripEndTimeDurationMs: Long = 0
    var isTripStart = false
    var isTripEntered = false

    private var gpsSpeed = 0.0f
    private var tripDao: TripReportDao? = null


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
        //val speedInKmh = location.speed * 3.6f
        Log.d("GPS_SPEED", "Speed - ${location.speed}")
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
                    Log.d(
                        "Trip_Processing_Service",
                        "UART data error : $it"
                    )
                }
                .onEach { canData ->

                    Log.d(
                        "Trip_Processing_Service",
                        "OnEach - CanData Received - $canData"
                    )

//                    val vehicleState = determineVehicleState(canData)
//
//                    Log.d(
//                        "Trip_Report",
//                        "DataSendService - startObservingUARTData vehicleState - $vehicleState" +
//                                " - canData - $canData"
//                    )
//
//                    val currentTime = System.currentTimeMillis()
//
//                    if (canData.ignitionStatus.name == "ON") {
//                        tripDistance = canData.odometer.currentRideInKm
//                        Log.d("Trip_Detail", "DataSendService tripDistance - $tripDistance")
//                    }
//                    if (canData.transmission.speed > maximumSpeed) {
//                        maximumSpeed = canData.transmission.speed
//                    }
//
//                    if (!isTripStarted && canData.ignitionStatus.name == "ON" && canData.transmission.speed > 0 && canData.odometer.currentRideInKm > 0.500) {
//
//                        tripStartTime = currentTime
//                        isTripStarted = true
//
////                        insertedId = tripDao?.insertTrip(
////                            TripReportEntity(
////                                distance = 0.0,
////                                duration = 0,
////                                averageSpeed = 0,
////                                startingSOC = 0,
////                                endingSOC = 0,
////                                maximumSpeed = 0,
////                                speedAlerts = 0,
////                                idleAlerts = 0,
////                                harshAlerts = 0,
////                                tripStartLocation = "",
////                                tripEndLocation = "",
////                                tripStartTime = currentTime,
////                                tripEndTime = 0
////                            )
////                        )?.toInt() ?: 0
//
//                        Log.d(
//                            "Trip_Summary",
//                            "Trip - Started - " +
//                                    " tripId - $tripId tripDistance - $tripDistance tripDuration - $tripDuration" +
//                                    " averageSpeed - $averageSpeed" +
//                                    " maximumSpeed - $maximumSpeed" +
//                                    " speedAlerts - $speedAlerts" +
//                                    " idleAlerts - $idleAlerts" +
//                                    " harshAlerts - $harshAlerts" +
//                                    " tripStartLocation - $tripStartLocation" +
//                                    " tripEndLocation - $tripEndLocation" +
//                                    " currentRideInKm - ${canData.odometer.currentRideInKm}" +
//                                    " tripStartTime - $tripStartTime" +
//                                    " tripEndTime - $tripEndTime" +
//                                    " insertedId - $insertedId"
//                        )
//
//                    } else if (isTripStarted && canData.ignitionStatus.name == "OFF") {
//                        tripEndTime = currentTime
//                        isTripStarted = false
//
//                        val durationMillis = tripEndTime - tripStartTime
//                        val totalSeconds = durationMillis / 1000
//                        val hours = totalSeconds / 3600
//                        val minutes = (totalSeconds % 3600) / 60
//                        val seconds = totalSeconds % 60
//                        val durationFormatted =
//                            String.format("%02d:%02d:%02d", hours, minutes, seconds)
//
////                        val tripEntity = TripReportEntity(
////                            tripId = insertedId,
////                            distance = tripDistance,
////                            duration = durationMillis,
////                            averageSpeed = averageSpeed,
////                            startingSOC = 0,
////                            endingSOC = 0,
////                            maximumSpeed = maximumSpeed,
////                            speedAlerts = speedAlerts,
////                            idleAlerts = idleAlerts,
////                            harshAlerts = harshAlerts,
////                            tripStartLocation = tripStartLocation,
////                            tripEndLocation = tripEndLocation,
////                            tripStartTime = currentTime,
////                            tripEndTime = tripEndTime
////                        )
////                        val updateResult = tripDao?.updateTrip(tripEntity)
//
////                        Log.d(
////                            "Trip_Summary",
////                            "Trip - Stopped " +
////                                    " TripEndTime - $tripEndTime " +
////                                    " tripDistance - $tripDistance" +
////                                    " hours - $hours" +
////                                    " minutes - $minutes" +
////                                    " seconds - $seconds" +
////                                    " durationFormatted - $durationFormatted" +
////                                    " durationMillis - $durationMillis" +
////                                    " maximumSpeed - $maximumSpeed" +
////                                    " tripId - $tripId tripDistance - $tripDistance tripDuration - $tripDuration" +
////                                    " averageSpeed - $averageSpeed" +
////                                    " maximumSpeed - $maximumSpeed" +
////                                    " speedAlerts - $speedAlerts" +
////                                    " idleAlerts - $idleAlerts" +
////                                    " harshAlerts - $harshAlerts" +
////                                    " tripStartLocation - $tripStartLocation" +
////                                    " tripEndLocation - $tripEndLocation" +
////                                    " currentRideInKm - ${canData.odometer.currentRideInKm}" +
////                                    " tripStartTime - $tripStartTime" +
////                                    " tripEndTime - $tripEndTime" +
////                                    " insertedId - $insertedId" +
////                                    " updateResult - $updateResult"
////                        )
//
//                        tripStartTime = 0
//                        tripEndTime = 0
//                        maximumSpeed = 0
//                        tripDistance = 0.0
//                        tripDuration = 0
//                        averageSpeed = 0
//                        tripStartLocation = ""
//                        tripEndLocation = ""
//                        tripStartTime = 0
//                        tripEndTime = 0
//
//                    }

                }
                .conflate()
                .collect { canData ->

                    Log.d(
                        "Trip_Processing_Service",
                        "Collect - CanData Received - $canData"
                    )
                    val vehicleState = determineVehicleState(canData)
                    Log.d(
                        "Vehicle_State",
                        "vehicle state value - $vehicleState"
                    )
                    processTrip(vehicleState, canData)

                }
        }
    }

    private fun determineVehicleState(canFeed: CanFeed): VehicleState {
        val canSpeed = canFeed.transmission.speed
        val ignition = canFeed.ignitionStatus
        //val batteryCurrent = canFeed.battery.current
//        val batteryCurrent = if(ignition == IgnitionStatus.ON && canSpeed > 0) {
//            5.0
//        } else if(ignition == IgnitionStatus.ON && canSpeed == 0) {
//            2.0
//        } else {
//            0.0
//        }

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

            if (vehicleState == VehicleState.RUNNING) {

                Log.d(
                    "Trip_Report",
                    "vehicle state running entered - $vehicleState"
                )

                if (!isTripStart) {
                    startSoc = canFeed.battery.remainingPercent
                    startOdometer = canFeed.odometer.totalKm
                    tripStartTimeDurationMs = System.currentTimeMillis()
                    isTripStart = !isTripStart

                    Log.d(
                        "Trip_Report",
                        "Trip start value captured - startSoc - $startSoc startOdometer - $startOdometer" +
                                "tripStartTimeDurationMs - $tripStartTimeDurationMs"
                    )
                }

                if (canFeed.odometer.currentRideInKm > 0.500) {

                    Log.d(
                        "Trip_Report",
                        "Actual trip started current distance > 500"
                    )

                    tripStatus = TripStatus.IN_PROGRESS
                    if(!isTripEntered) {
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
                            Log.d("Trip_Report", "Trip Details Inserted - $tripId")
                            isTripEntered = !isTripEntered

                        }
                    }

                }

            } else if (vehicleState == VehicleState.IDLE) {

            } else if (vehicleState == VehicleState.STOP) {

                if (tripStatus == TripStatus.IN_PROGRESS) {

                    if(isTripStart && isTripEntered) {

                        tripStatus = TripStatus.COMPLETED
                        val existing = tripDao?.getTripByTripId(tripId)
                        val updated = existing?.copy(
                            endSOC = canFeed.battery.remainingPercent,
                            endOdometer = canFeed.odometer.totalKm,
                            tripEndTimeDurationMs = System.currentTimeMillis(),
                            tripStatus = tripStatus
                        )
                        val updateStatus = updated?.let { tripDao?.updateTrip(it) }
                        Log.d("Trip_Report", "Trip Details Updated - $updateStatus")

                        tripStatus = TripStatus.NOT_STARTED
                        startSoc = 0
                        startOdometer = 0.0
                        tripStartTimeDurationMs = 0
                        isTripStart = false
                        isTripEntered = false

                    }

                }

            } else {

            }

        } catch (e:Exception) {
            if(isTripStart && isTripEntered) {
                val existing = tripDao?.getTripByTripId(tripId)
                val updated = existing?.copy(
                    endSOC = canFeed.battery.range,
                    endOdometer = canFeed.odometer.totalKm,
                    tripEndTimeDurationMs = System.currentTimeMillis(),
                    tripStatus = TripStatus.FAILED
                )
                val updateStatus = updated?.let { tripDao?.updateTrip(it) }
                Log.d("Trip_Report", "Trip Details Updated - $updateStatus")
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val initialNotification =
                createNLSenderNotification("NL-Sender ", "Trip processing service started")
            startForeground(NOTIFICATION_ID, initialNotification)
        } catch (e: Exception) {
            logcat { e.asLog() }
        }

        return START_STICKY
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
        handler = null
        externalScope.cancel()

        logcat(TAG) { "UARTService onDestroy" }
        serialListenerJob?.cancel()
        unbindService(serviceConnection)
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