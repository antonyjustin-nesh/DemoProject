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
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.nlevcluster.aidl.IAckCallback
import com.nlevcluster.aidl.IProcessedFeedCallback
import com.nlevcluster.aidl.IUartService
import com.nlevcluster.sender.R
import com.nlevcluster.sender.TelematicsManager
import com.nlevcluster.sender.TelematicsRuntimeConfig
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.model.CanFeed
import com.nlevcluster.sender.model.LocationInfo
import com.nlevcluster.sender.model.VIP
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.toVehicleFeedRoot
import com.nlevcluster.sender.util.NLDataStore
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.asLog
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

const val NOTIFICATION_ID = 3
private const val NOTIFICATION_CHANNEL = "DATA_SYNC_SERVICE"
private const val VEHICLE_IGNITION_ON_DELAY_MS = 30_000L
private const val VEHICLE_IGNITION_OFF_DELAY_MS = 180_000L
private const val INSTRUMENTATION_IGNITION_ON_DELAY_MS = 60_000L
private const val INSTRUMENTATION_IGNITION_OFF_DELAY_MS = 300_000L
private const val VEHICLE_TOPIC = "CLUSTER_DATA"
private const val INSTRUMENTATION_TOPIC = "INSTRUMENTATION"
private const val RECONNECT_DELAY_MS = 1_000L

@AndroidEntryPoint
class DataSendService : Service() {

    @Inject
    lateinit var nlDataStore: NLDataStore
    @Inject
    lateinit var gson: Gson
    @Inject
    lateinit var logHelper: LogHelper
    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val tag = javaClass.simpleName

    private lateinit var notificationManager: NotificationManager
    private lateinit var telematics: TelematicsManager
    private lateinit var locationManager: LocationManager

    private var uartService: IUartService? = null
    private var isBound = false
    private var imei: String? = null
    private var pendingBootCommand = false
    private var telemetryStarted = false

    private var serialListenerJob: Job? = null
    private var locationListenerJob: Job? = null
    private var reconnectJob: Job? = null
    private var telemetryStartJob: Job? = null
    private var telemetryConnectionJob: Job? = null
    private var telemetryNetworkConnectionJob: Job? = null
    private var vipObserver: ContentObserver? = null

    private val ackCallback = object : IAckCallback.Stub() {
        override fun onAckReceived(data: String?) {
            if (data.isNullOrBlank()) return
            serviceScope.launch {
                runCatching { json.decodeFromString(Ack.serializer(), data) }
                    .onSuccess { ack ->
                        log(LogType.DEBUG, "UART acknowledgement ${ack.commandName}: ${ack.status}", "ProtocolUpdate")
                    }
                    .onFailure {
                        log(LogType.DEBUG, "Ignoring unparsable UART acknowledgement; payloadLength=${data.length ?: 0}", "ProtocolUpdate")
                    }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            uartService = IUartService.Stub.asInterface(binder)
            isBound = true
            uartService?.registerAckCallback(ackCallback)
            startObservingUartData()
            log(LogType.INFO, "UART service connected; acknowledgement callback and CAN observer registered")
            if (pendingBootCommand) {
                sendCommand("""{"F99":"O02"}""")
                pendingBootCommand = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) = handleUartDisconnect("service disconnected")
        override fun onBindingDied(name: ComponentName?) = handleUartDisconnect("binding died")
        override fun onNullBinding(name: ComponentName?) = handleUartDisconnect("null binding")
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        telematics = TelematicsManager.getInstance()
        createNotificationChannel()
        log(LogType.INFO, "DataSendService created; initializing telemetry, GPS, VIP, and UART observers")
        registerVipObserver()
        startObservingLocation()
        bindToSerialService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Starting telemetry"))
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val resolvedImei = intent?.getStringExtra(EXTRA_IMEI)
            ?: preferences.getString(PREF_IMEI, null)

        log(
            LogType.INFO,
            "Telemetry start requested: startId=$startId, source=" +
                if (intent?.getStringExtra(EXTRA_FROM) == BOOT_RECEIVER) "boot" else "service"
        )

        if (resolvedImei.isNullOrBlank()) {
            log(LogType.ERROR, "Telemetry cannot start until a device IMEI is provided")
            return START_REDELIVER_INTENT
        }

        imei = resolvedImei
        preferences.edit().putString(PREF_IMEI, resolvedImei).apply()
        if (intent?.getStringExtra(EXTRA_FROM) == BOOT_RECEIVER) pendingBootCommand = true
        startTelemetry(resolvedImei)
        return START_REDELIVER_INTENT
    }

    private fun startTelemetry(deviceImei: String) {
        telemetryStartJob?.cancel()
        log(LogType.INFO, "Starting telemetry SDK configuration")
        telemetryStartJob = serviceScope.launch {
            runCatching {
                val config = loadTelemetryConfig(deviceImei)
                telematics.start(appContext, serviceScope, config)
            }.onSuccess {
                telemetryStarted = true
                observeTelemetryConnection()
                log(LogType.INFO, "Telematics SDK started for vehicle and instrumentation feeds")
            }.onFailure { error ->
                telemetryStarted = false
                log(LogType.ERROR, "Telematics SDK failed to start: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun observeTelemetryConnection() {
        telemetryConnectionJob?.cancel()
        telemetryNetworkConnectionJob?.cancel()
        telemetryConnectionJob = serviceScope.launch {
            telematics.mqttConnection
                .drop(1)
                .distinctUntilChanged()
                .collect { connected ->
                    val status =
                        if (connected) "Telemetry connected" else "Telemetry offline; SDK will retry"
                    notificationManager.notify(NOTIFICATION_ID, createNotification(status))
                    log(if (connected) LogType.INFO else LogType.ERROR, status)
                }
        }
        telemetryNetworkConnectionJob = serviceScope.launch {
            telematics.networkConnection
                .drop(1)
                .distinctUntilChanged()
                .collect { connected ->
                    log(
                        if (connected) LogType.INFO else LogType.ERROR,
                        if (connected) "Telemetry network available" else "Telemetry network unavailable"
                    )
                }
        }
    }

    private suspend fun loadTelemetryConfig(deviceImei: String): TelematicsRuntimeConfig {
        val vipFeed = readVipFeed()?.also { feed ->
            nlDataStore.setMqttFeedUrl(feed.mqttUrl)
            nlDataStore.setIgnitionOnDelay(feed.ignitionOnDelayMs.toInt())
            nlDataStore.setIgnitionOffDelay(feed.ignitionOffDelayMs.toInt())
        }
        val storedBroker = nlDataStore.mqttFeedUrl()?.takeIf(String::isNotBlank)
        val broker = vipFeed?.mqttUrl?.takeIf(String::isNotBlank)
            ?: storedBroker
            ?: Constants.MQTT_PORTAL_URL
        val vehicleOnDelay = vipFeed?.ignitionOnDelayMs
            ?: nlDataStore.ignitionOnDelay()?.toLong()?.takeIf { it > 0L }
            ?: VEHICLE_IGNITION_ON_DELAY_MS
        val vehicleOffDelay = vipFeed?.ignitionOffDelayMs
            ?: nlDataStore.ignitionOffDelay()?.toLong()?.takeIf { it > 0L }
            ?: VEHICLE_IGNITION_OFF_DELAY_MS

        val runtimeConfig = TelematicsRuntimeConfig(
            brokerUrl = broker,
            clientId = deviceImei,
            vehicleFeed = live.nesh.cluster.telematics.FeedConfig(
                enabled = true,
                ignitionOnIntervalMs = vehicleOnDelay,
                ignitionOffIntervalMs = vehicleOffDelay,
                topic = VEHICLE_TOPIC,
            ),
            instrumentationFeed = live.nesh.cluster.telematics.FeedConfig(
                enabled = true,
                ignitionOnIntervalMs = INSTRUMENTATION_IGNITION_ON_DELAY_MS,
                ignitionOffIntervalMs = INSTRUMENTATION_IGNITION_OFF_DELAY_MS,
                topic = INSTRUMENTATION_TOPIC,
            ),
        )
        val brokerSource = when {
            vipFeed?.mqttUrl?.isNotBlank() == true -> "vip"
            storedBroker != null -> "dataStore"
            else -> "default"
        }
        log(
            LogType.INFO,
            DataSendServiceDiagnostics.runtimeConfig(
                brokerSource = brokerSource,
                vehicleOnDelayMs = vehicleOnDelay,
                vehicleOffDelayMs = vehicleOffDelay,
                instrumentationOnDelayMs = INSTRUMENTATION_IGNITION_ON_DELAY_MS,
                instrumentationOffDelayMs = INSTRUMENTATION_IGNITION_OFF_DELAY_MS,
            )
        )
        return runtimeConfig
    }

    private fun readVipFeed(): VipFeedConfig? = runCatching {
        contentResolver.query(Uri.parse(Constants.MODEL_VIP_URI), null, null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex("model_vip")
                if (column < 0 || !cursor.moveToFirst()) return@use null
                val vip = gson.fromJson(cursor.getString(column), VIP::class.java)
                VipFeedConfig(
                    mqttUrl = vip.appConfig.feed.mqttUrl,
                    ignitionOnDelayMs = vip.appConfig.feed.ignOnDelay.toLong().takeIf { it > 0L }
                        ?: VEHICLE_IGNITION_ON_DELAY_MS,
                    ignitionOffDelayMs = vip.appConfig.feed.ignOffDelay.toLong().takeIf { it > 0L }
                        ?: VEHICLE_IGNITION_OFF_DELAY_MS,
                )
            }
    }.onFailure { error ->
        log(LogType.ERROR, "VIP configuration unavailable: ${error.javaClass.simpleName}")
    }.getOrNull()

    private fun registerVipObserver() {
        if (vipObserver != null) return
        vipObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val deviceImei = imei ?: return
                serviceScope.launch {
                    log(LogType.INFO, "VIP telemetry configuration changed; reloading configuration")
                    val config = loadTelemetryConfig(deviceImei)
                    if (telemetryStarted) {
                        try {
                            telematics.reconfigure(config)
                            log(
                                LogType.INFO,
                                "Applied VIP telemetry interval configuration; broker changes apply next launch"
                            )
                        } catch (error: Exception) {
                            log(LogType.ERROR, "VIP telemetry reconfiguration failed: ${error.javaClass.simpleName}")
                            throw error
                        }
                    }
                }
            }
        }.also { observer ->
            contentResolver.registerContentObserver(
                Uri.parse(Constants.APPLY_VIP_URI),
                true,
                observer
            )
        }
    }

    private fun bindToSerialService() {
        if (isBound) return
        val intent = Intent(SERIAL_SERVICE_INTENT).setPackage(SERIAL_SERVICE_PACKAGE_NAME)
        log(LogType.INFO, "Requesting UART service binding")
        if (!bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            log(LogType.ERROR, "UART service bind request was rejected")
            scheduleReconnect()
        }
    }

    private fun handleUartDisconnect(reason: String) {
        uartService = null
        isBound = false
        serialListenerJob?.cancel()
        serialListenerJob = null
        scheduleReconnect()
        log(LogType.ERROR, "UART service disconnected: $reason")
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            log(LogType.INFO, "UART reconnect scheduled in ${RECONNECT_DELAY_MS}ms")
            kotlinx.coroutines.delay(RECONNECT_DELAY_MS)
            if (!isBound) {
                log(LogType.INFO, "Attempting UART reconnect")
                bindToSerialService()
            }
        }
    }

    private fun startObservingUartData() {
        serialListenerJob?.cancel()
        serialListenerJob = serviceScope.launch {
            serialDataFlow()
                .conflate()
                .catch { error ->
                    log(LogType.ERROR, "UART telemetry collection failed: ${error.javaClass.simpleName}")
                }
                .collect { canFeed ->
                    val deviceImei = imei ?: return@collect
                    try {
                        telematics.submitVehicleFeed(
                            canFeed.toVehicleFeedRoot(
                                imei = deviceImei,
                                epoch = System.currentTimeMillis() / 1_000L,
                            )
                        )
                        telematics.updateIgnition(canFeed.ignitionStatus.status == "ON")
                        log(LogType.DEBUG, DataSendServiceDiagnostics.canFeedSubmitted(canFeed))
                    } catch (error: Exception) {
                        log(
                            LogType.ERROR,
                            "CAN vehicle-feed handoff failed: seq=${canFeed.seqNo}, error=${error.javaClass.simpleName}"
                        )
                        throw error
                    }
                }
        }
    }

    private fun startObservingLocation() {
        locationListenerJob?.cancel()
        locationListenerJob = serviceScope.launch {
            locationUpdates()
                .filterNotNull()
                .catch { error ->
                    log(LogType.ERROR, "GPS telemetry collection failed: ${error.javaClass.simpleName}")
                }
                .collect { location ->
                    try {
                        telematics.updateLocation(location)
                        log(LogType.DEBUG, DataSendServiceDiagnostics.gpsUpdateAccepted(location))
                    } catch (error: Exception) {
                        log(LogType.ERROR, "GPS telemetry handoff failed: ${error.javaClass.simpleName}")
                        throw error
                    }
                }
        }
    }

    private fun serialDataFlow(): Flow<CanFeed> = callbackFlow {
        val callback = object : IProcessedFeedCallback.Stub() {
            override fun onProcessedFeedReceived(data: String?) {
                if (!data.isValidJson()) {
                    log(LogType.ERROR, "Ignoring invalid UART feed; payloadLength=${data?.length ?: 0}")
                    return
                }
                runCatching { json.decodeFromString(CanFeed.serializer(), data.orEmpty()) }
                    .onSuccess {
                        log(LogType.DEBUG, DataSendServiceDiagnostics.canFeedAccepted(it))
                        trySend(it)
                    }
                    .onFailure {
                        log(LogType.ERROR, "Unable to parse UART feed: ${it.javaClass.simpleName}; payloadLength=${data?.length ?: 0}")
                    }
            }
        }
        val service = uartService
        if (service == null) {
            close(IllegalStateException("UART service is not bound"))
        } else {
            service.registerProcessedFeedCallback(callback)
        }
        awaitClose { runCatching { service?.unregisterProcessedFeedCallback(callback) } }
    }

    @SuppressLint("MissingPermission")
    private fun locationUpdates(): Flow<LocationInfo?> = callbackFlow {
        trySend(null)
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            log(LogType.ERROR, "GPS provider is disabled; telemetry will omit GPS")
            awaitClose { }
            return@callbackFlow
        }
        val listener = LocationListener { location ->
            trySend(
                LocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy.toDouble(),
                    speed = location.speed,
                    altitude = location.altitude.toFloat(),
                )
            )
        }
        try {
            withContext(Dispatchers.Main) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_MIN_TIME_MS,
                    GPS_MIN_DISTANCE_M,
                    listener,
                )
            }
        } catch (error: SecurityException) {
            log(LogType.ERROR, "Location permission unavailable; telemetry will omit GPS")
            close(error)
        }
        awaitClose { locationManager.removeUpdates(listener) }
    }

    private fun sendCommand(command: String) {
        serviceScope.launch {
            val service = uartService
            if (service == null) {
                log(LogType.ERROR, "UART command skipped because service is not bound; payloadLength=${command.length}")
                return@launch
            }
            log(LogType.DEBUG, "Sending UART command; payloadLength=${command.length}")
            runCatching { service.writeData(command) }
                .onSuccess { log(LogType.DEBUG, "UART command dispatched; payloadLength=${command.length}") }
                .onFailure { log(LogType.ERROR, "Failed to send UART command: ${it.javaClass.simpleName}") }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        log(LogType.INFO, "DataSendService stopping; telemetryStarted=$telemetryStarted, uartBound=$isBound")
        vipObserver?.let { contentResolver.unregisterContentObserver(it) }
        vipObserver = null
        telemetryConnectionJob?.cancel()
        telemetryNetworkConnectionJob?.cancel()
        telemetryStartJob?.cancel()
        serialListenerJob?.cancel()
        locationListenerJob?.cancel()
        reconnectJob?.cancel()
        runCatching { uartService?.unregisterAckCallback(ackCallback) }
        if (isBound) runCatching { unbindService(serviceConnection) }
        isBound = false
        telematics.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(content: String): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("NL-Sender - Data Sync")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Data Sync Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Vehicle telemetry synchronization" }
        )
    }

    private fun log(type: LogType, message: String, logTag: String = tag) {
        logcat(logTag) { message }
        logHelper.log(tag = logTag, type = type, message = message)
    }

    private fun String?.isValidJson(): Boolean = !isNullOrBlank() && runCatching {
        JSONObject(this)
    }.recoverCatching {
        JSONArray(this)
    }.isSuccess

    @Serializable
    private data class Ack(
        @SerialName("A11") val commandName: String = "",
        @SerialName("A12") val status: String = "",
    )

    private data class VipFeedConfig(
        val mqttUrl: String,
        val ignitionOnDelayMs: Long,
        val ignitionOffDelayMs: Long,
    )

    private companion object {
        const val EXTRA_IMEI = "IMEI"
        const val EXTRA_FROM = "FROM"
        const val BOOT_RECEIVER = "Boot_Receiver"
        const val PREFERENCES = "properties"
        const val PREF_IMEI = "imei"
        const val SERIAL_SERVICE_INTENT = "com.nlevcluster.serialservice.BIND_UART_SERVICE"
        const val SERIAL_SERVICE_PACKAGE_NAME = "com.nlevcluster.serialservice"
        const val GPS_MIN_TIME_MS = 1_000L
        const val GPS_MIN_DISTANCE_M = 1f
    }
}
