package com.nlevcluster.sender.service

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
import android.database.Cursor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.nlevcluster.aidl.IAckCallback
import com.nlevcluster.aidl.IProcessedFeedCallback
import com.nlevcluster.aidl.IUartService
import com.nlevcluster.sender.R
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.data.constants.Constants.APPLY_VIP_URI
import com.nlevcluster.sender.data.constants.Constants.DEFAULT_IGN_OFF_DELAY
import com.nlevcluster.sender.data.constants.Constants.DEFAULT_IGN_ON_DELAY
import com.nlevcluster.sender.data.constants.Constants.MODEL_VIP_URI
import com.nlevcluster.sender.model.BatteryFeed
import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.GPSFeed
import com.nlevcluster.sender.model.ICFeed
import com.nlevcluster.sender.model.Ignition
import com.nlevcluster.sender.model.IgnitionStatus
import com.nlevcluster.sender.model.LocationInfo
import com.nlevcluster.sender.model.MotorFeed
import com.nlevcluster.sender.model.VIP
import com.nlevcluster.sender.model.VehicleCANFeed
import com.nlevcluster.sender.model.VehicleFeedWrapper
import com.nlevcluster.sender.model.VehicleInfo
import com.nlevcluster.sender.model.VehicleInfoWrapper
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.report.VehicleState
import com.nlevcluster.sender.util.MQTTUtil
import com.nlevcluster.sender.util.NLDataStore
import com.nlevcluster.sender.util.Util
import com.nlevcluster.sender.util.determineVehicleState
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.asLog
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

const val IGNITION_ON_DELAY = 30_000L
const val IGNITION_OFF_DELAY = 180_000L
const val INTERVAL_IN_MILLIS = 60_000L
const val NOTIFICATION_ID = 3

@AndroidEntryPoint
class DataSendService : Service() {

    @Inject
    lateinit var portalMQTTClient: MQTTUtil

    @Inject
    lateinit var nlDataStore: NLDataStore

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var logHelper: LogHelper

    @Inject
    @ApplicationContext
    lateinit var context: Context

    private val json = Json { ignoreUnknownKeys = true }

    private val tag: String = this.javaClass.simpleName

    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var senderJob: Job? = null

    private val reportProcessingService: String =
        "com.nlevcluster.sender.report.service.ReportProcessingService"

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

    private var pendingBootCommand: Boolean = false

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private lateinit var connectivityManager: ConnectivityManager
    private var pendingVehicleInfo: String? = null
    private var retryCount = 0
    private val maxRetries = 10
    private val retryDelay = 5000L
    private var firmWareVer = ""

    private val ackCallback = object : IAckCallback.Stub() {
        override fun onAckReceived(data: String?) {

            if (data.isNullOrEmpty()) return

            externalScope.launch {
                runCatching {
                    val ack = json.decodeFromString<Ack>(data)
                    if (ack.commandName == "F99" && ack.isSuccess) {

                        val sharedPref = getSharedPreferences("properties", MODE_PRIVATE)
                        logHelper.log(
                            tag = "ProtocolUpdate",
                            type = LogType.DEBUG,
                            message = "Received Firmware Version - ${ack.result}"
                        )

                        val isVehicleInfoPublished =
                            sharedPref.getString("IsPublishVehicleInfo", null) ?: ""

                        if (isVehicleInfoPublished.isEmpty()) {

                            if (!isInternetAvailable()) {
                                logHelper.log(
                                    tag = "ProtocolUpdate",
                                    message = "No internet, waiting to publish Vehicle Info...",
                                    type = LogType.INFO
                                )
                                registerNetworkCallback()
                                firmWareVer = ack.result
                            } else {

                                firmWareVer = ack.result
                                imei?.let { startPublishProcess(imei = it, firmWareVer = ack.result) }

                            }

                        }

                    }

                }.onFailure {
                    logHelper.log(
                        tag = "ProtocolUpdate",
                        type = LogType.DEBUG,
                        message = "Ack parsing failed - $it"
                    )
                }
            }

            logHelper.log(
                tag = "ProtocolUpdate",
                type = LogType.DEBUG,
                message = "Received onAckReceived from serial service $data"
            )

        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            uartService = IUartService.Stub.asInterface(binder)
            isBound = true
            logcat(tag) { "Service connected" }
            logHelper.log(tag = tag, type = LogType.DEBUG, message = "Service connected")
            startObservingUARTData() // Start collecting data only after binding

            uartService?.registerAckCallback(ackCallback)

            // If boot command was pending, send it now
            if (pendingBootCommand) {
                sendCommand("""{"F99":"O02"}""")
                pendingBootCommand = false
            }

        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            logcat(tag) { "Service onBindingDied" }
            logHelper.log(tag = tag, type = LogType.DEBUG, message = "Service onBindingDied")
            uartService = null
            isBound = false
            scheduleReconnect()
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            logcat(tag) { "Service onNullBinding" }
            logHelper.log(tag = tag, type = LogType.DEBUG, message = "Service onNullBinding")
            uartService = null
            isBound = false
            scheduleReconnect()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            uartService = null
            logcat(tag) { "Service disconnected" }
            logHelper.log(tag = tag, type = LogType.DEBUG, message = "Service disconnected")
            uartService = null
            isBound = false
            scheduleReconnect()
        }
    }

    private val serialServiceDataFlow = callbackFlow {
        val callback = object : IProcessedFeedCallback.Stub() {

            override fun onProcessedFeedReceived(data: String?) {
                logcat { "RX $data" }
                logHelper.log(
                    tag = tag,
                    type = LogType.DEBUG,
                    message = "Received Processing Feed Data - $data"
                )
                if (data != null) {
                    if (data.isValidJson()) {
                        try {
                            val serialCANFeed = json.decodeFromString<CanFeed>(data)
                            logHelper.log(
                                tag = tag,
                                type = LogType.DEBUG,
                                message = "Processed SerialCANFeed - $serialCANFeed"
                            )
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
            logcat("GPS_LOCATION") { "GPS provider disabled" }
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
                GPS_MIN_TIME_MS,
                MIN_DISTANCE_M,
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
                logHelper.log(
                    tag = tag,
                    type = LogType.DEBUG,
                    message = "Attempting to reconnect to UART service"
                )
                bindToSerialService()
            } else {
                logHelper.log(
                    tag = tag,
                    type = LogType.DEBUG,
                    message = "UART service connected successfully"
                )
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
                .collect { canData ->
                    val dataPushDelay =
                        if (canData.ignitionStatus.name == "ON") nlDataStore.ignitionOnDelay()
                            ?: IGNITION_ON_DELAY
                        else nlDataStore.ignitionOffDelay() ?: IGNITION_OFF_DELAY

                    imei?.let {
                        sendVehicleFeedToPortal(canData, it)
                    } ?: run {
                        logHelper.log(
                            tag = "ProtocolUpdate",
                            type = LogType.ERROR,
                            message = "Failed to send data to portal, IMEI is null"
                        )
                        logcat { "Failed to send data to portal, IMEI is null" }
                    }

                    delay(dataPushDelay.toLong())
                }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
            logHelper.log(
                tag = "NLVIPHelper",
                type = LogType.ERROR,
                message = "Exception while configure sender service - ${e.message}"
            )
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
                        message = "Portal MQTT Connection active. No action required.",
                        type = LogType.INFO
                    )
                }

                handler?.postDelayed(this, INTERVAL_IN_MILLIS)
            }
        }

        monitorMQTTConnectionRunnable?.let { handler?.post(it) }
        logHelper.log(
            tag = "MQTT_CONNECTION_MONITOR",
            message = "MQTT Connection monitoring initiated.",
            type = LogType.INFO
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val initialNotification =
                createNLSenderNotification("NL-Sender ", "Data sync service started")
            startForeground(NOTIFICATION_ID, initialNotification)

            initReportProcessingService(applicationContext)

            val sharedPref = getSharedPreferences("properties", MODE_PRIVATE)
            imei = intent?.getStringExtra("IMEI") ?: sharedPref.getString("imei", null)
            imei?.let { imeiValue ->
                logHelper.log(
                    tag = tag,
                    type = LogType.INFO,
                    message = "Started Data send service with Device IMEI - $imeiValue"
                )
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

                    val triggerFrom = intent?.getStringExtra("FROM").orEmpty()

                    if (triggerFrom == "Boot_Receiver") {
                        with(sharedPref.edit()) {
                            putString("IsPublishVehicleInfo", "")
                            commit()
                        }
                        pendingBootCommand = true
                    }

                    return START_REDELIVER_INTENT
                }
            }
        } catch (e: Exception) {
            logcat { e.asLog() }
            logHelper.log(
                tag = tag,
                type = LogType.ERROR,
                message = "Exception while starting DataSendService - ${e.asLog()}"
            )
        }

        return START_STICKY
    }

    private fun sendCommand(command: String) {
        externalScope.launch(Dispatchers.IO) {
            runCatching {
                uartService?.writeData(command)
            }.onFailure {
                logHelper.log(
                    tag = tag,
                    type = LogType.ERROR,
                    message = "Failed to send command - $it"
                )
            }
        }
    }

    private fun initReportProcessingService(context: Context) {
        val tripIntent = Intent()
        tripIntent.setClassName("com.nlevcluster.sender", reportProcessingService)
        tripIntent.putExtra("IMEI", imei)
        context.startForegroundService(tripIntent)
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
                        message = "initMQTTClient - MQTT connected to Cloud.",
                        type = LogType.INFO
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

    private fun sendVehicleFeedToPortal(canFeed: CanFeed, imei: String) {
        val feedData = generateVehicleCANFeed(canFeed, imei)
        logcat { "feedData $feedData" }

        val messageSize = feedData.toByteArray().size
        showNotification("Publishing message of $messageSize Bytes") //Show only important data like seq no.
        portalMQTTClient.publish(Constants.TOPIC_VEHICLE_FEED, feedData) { isSuccess ->
            val status = if (isSuccess) "Published" else "Failed to publish"
            showNotification("$status message with $messageSize Bytes")
            logHelper.log(
                tag = "Send Data to portal",
                message = "sendDataToPortal - $status \n $feedData",
                type = LogType.INFO
            )
            logHelper.log(
                tag = "ProtocolUpdate",
                message = "sendVehicleFeedToPortal - $status \n $feedData",
                type = LogType.INFO
            )

        }
    }

    private fun sendVehicleInfoToPortal(
        vehicleInfo: String,
        onPublishComplete: (isSuccess: Boolean) -> Unit
    ) {
        logcat { "VehicleInfo - $vehicleInfo" }
        logHelper.log(
            tag = "ProtocolUpdate",
            message = "sendVehicleInfoToPortal - vehicleInfo - $vehicleInfo",
            type = LogType.INFO
        )

        val messageSize = vehicleInfo.toByteArray().size
        showNotification("Publishing message of $messageSize Bytes") //Show only important data like seq no.
        portalMQTTClient.publish(Constants.TOPIC_VEHICLE_FEED, vehicleInfo) { isSuccess ->
            val status = if (isSuccess) "Published" else "Failed to publish"
            showNotification("$status message with $messageSize Bytes")
            logHelper.log(
                tag = "Send Data to portal",
                message = "sendDataToPortal - $status \n $vehicleInfo",
                type = LogType.INFO
            )
            logHelper.log(
                tag = "ProtocolUpdate",
                message = "sendVehicleInfoToPortal - $status \n $vehicleInfo",
                type = LogType.INFO
            )
            onPublishComplete(isSuccess)
        }
    }

    private fun generateVehicleInfoFeed(imei: String, firmWareVer: String): String {
        val vehicleInfoWrapper = VehicleInfoWrapper(
            VehicleInfo(
                imei = imei,
                vehicleIdentificationNumber = "1HGCM82633A004352",
                batteryIdentificationNumber = "EV2024BIN000",
                motorIdentificationNumber = "EV2024MIN000",
                bmsHardwareVersion = "HW1.2.0",
                bmsSoftwareVersion = "SW3.4.0",
                mcuHardwareVersion = "MCU_HW_2.0",
                mcuSoftwareVersion = "MCU_SW_3.0.0",
            )
        )
        return json.encodeToString(vehicleInfoWrapper)
    }

    private fun generateVehicleCANFeed(processedCANFeed: CanFeed, imei: String): String {
        val vehicleInfoWrapper = VehicleFeedWrapper(
            VehicleCANFeed(
                epochTimeStamp = "${System.currentTimeMillis() / 1000}",
                imei = imei,
                ignition = getIgnition(processedCANFeed.ignitionStatus),
                vehicleStatus = getVehicleState(determineVehicleState(processedCANFeed))
            ),
            gpsFeed = GPSFeed(
                latitude = processedCANFeed.currentLocation?.latitude ?: 0.0,
                longitude = processedCANFeed.currentLocation?.longitude ?: 0.0,
                altitude = processedCANFeed.currentLocation?.altitude?.toDouble() ?: 0.0,
                odometer = 0.0,
                gpsSpeed = processedCANFeed.currentLocation?.speed ?: 0f,
            ),
            batteryFeed = BatteryFeed(
                current = processedCANFeed.battery.current.toFloat(),
                voltage = processedCANFeed.battery.volt.toFloat(),
                soc = processedCANFeed.battery.remainingPercent.toFloat(),
                status = if(getVehicleState(determineVehicleState(processedCANFeed)) == "CHARGING") "CHARGING" else "DISCHARGING",
                remainingCapacity = 0f,
                fullCapacity = 0f,
                energy = 0f,
                cycleCount = 0,
            ),
            icFeed = ICFeed(
                odometer = processedCANFeed.odometer.totalKm,
                speed = processedCANFeed.transmission.speed.toFloat()
            ),
            motorFeed = MotorFeed(
                current = 0f,
                voltage = 0f,
                speed = processedCANFeed.transmission.rpm,
                odometer = processedCANFeed.odometer.totalKm,
                rpm = processedCANFeed.transmission.rpm,
                motorTemperature = 0f,
                gearPosition = when (processedCANFeed.evMode) {
                    EvMode.SPORTS -> 1
                    EvMode.POWER -> 2
                    else -> 0
                },
                breakPosition = "FRONT_RIGHT",
                controllerTemperature = 0f,
                torque = 0f,
                direction = 0,
                fault = 0
            )
        )
        return json.encodeToString(vehicleInfoWrapper)
    }

    private fun getIgnition(status: IgnitionStatus): Int {
        return when (status) {
            IgnitionStatus.ON -> 1
            IgnitionStatus.OFF -> 0
        }
    }

    private fun getVehicleState(vehicleState: VehicleState): String {
        return when (vehicleState) {
            VehicleState.RUNNING -> "MOVING"
            VehicleState.IDLE -> "STOP"
            VehicleState.STOP -> "STOP"
            VehicleState.CHARGING -> "CHARGING"
            VehicleState.OFFLINE -> "OFFLINE"
            VehicleState.TOWING -> "TOWING"
            VehicleState.UNKNOWN -> "UNKNOWN"
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
            logHelper.log(
                tag = tag,
                type = LogType.INFO,
                message = "createNLSenderNotificationChannel - Created notification channel"
            )
        }
    }

    private fun startPublishProcess(imei: String, firmWareVer: String) {
        val vehicleInfo = generateVehicleInfoFeed(imei, firmWareVer)
        logHelper.log(
            tag = "ProtocolUpdate",
            type = LogType.INFO,
            message = "generateVehicleInfoFeed - $vehicleInfo"
        )
        pendingVehicleInfo = vehicleInfo
        retryPublish()
    }

    private fun retryPublish() {
        pendingVehicleInfo?.let { info ->
            sendVehicleInfoToPortal(info) { isSuccess ->
                if (isSuccess) {
                    logHelper.log(
                        tag = "ProtocolUpdate",
                        type = LogType.INFO,
                        message = "SendVehicleInfoToPortal - Success"
                    )
                    getSharedPreferences("properties", MODE_PRIVATE)
                        .edit()
                        .putString("IsPublishVehicleInfo", "PUBLISHED")
                        .apply()
                    unregisterNetworkCallback()
                } else {
                    if (retryCount < maxRetries) {
                        retryCount++
                        logHelper.log(
                            tag = "ProtocolUpdate",
                            type = LogType.INFO,
                            message = "Retry #$retryCount in ${retryDelay}ms"
                        )
                        Handler(Looper.getMainLooper()).postDelayed({ retryPublish() }, retryDelay)
                    } else {
                        logHelper.log(
                            tag = "ProtocolUpdate",
                            type = LogType.INFO,
                            message = "Max retries reached"
                        )
                    }
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            logHelper.log(
                tag = "ProtocolUpdate",
                type = LogType.INFO,
                message = "Internet connected, starting publish... LogType.INFO"
            )
            unregisterNetworkCallback()
            imei?.let { startPublishProcess(imei = it, firmWareVer = firmWareVer) }
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    private fun isInternetAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    override fun onDestroy() {
        monitorMQTTConnectionRunnable?.let { handler?.removeCallbacks(it) }
        monitorMQTTConnectionRunnable = null
        handler = null

        senderJob?.cancel()
        senderJob = null
        externalScope.cancel()
        portalMQTTClient.disconnect()

        logcat(tag) { "UARTService onDestroy" }
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

    @Serializable
    data class Ack(
        @SerialName("A11")
        val commandName: String = "",
        @SerialName("A12")
        val status: String = "",
        @SerialName("A13")
        val result: String = ""
    ) {
        val isSuccess: Boolean = status == "A21"
        val isFailure: Boolean = status == "A22"
    }

    companion object {
        const val DATA = "DATA"
        private const val RECONNECT_DELAY = 1000L // 1 second
        private const val SERIAL_SERVICE_INTENT = "com.nlevcluster.serialservice.BIND_UART_SERVICE"
        private const val SERIAL_SERVICE_PACKAGE_NAME = "com.nlevcluster.serialservice"

        private const val GPS_MIN_TIME_MS = 1000L // Update every 1 second
        private const val MIN_DISTANCE_M = 1f  // Update every 1 meter
    }
}