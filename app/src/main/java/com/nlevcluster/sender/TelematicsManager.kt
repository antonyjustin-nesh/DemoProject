package com.nlevcluster.sender

import android.content.Context
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.nllogger.LogType
import com.nlevcluster.sender.nllogger.NLLoggerLogHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import live.nesh.cluster.telematics.FeedConfig
import live.nesh.cluster.telematics.TelemetryConfig
import live.nesh.cluster.telematics.api.TelematicsConfig
import live.nesh.cluster.telematics.api.TelematicsSdk
import live.nesh.cluster.telematics.logging.TelematicsDebugCallback
import live.nesh.cluster.telematics.logging.TelematicsLog
import live.nesh.cluster.telematics.model.CoreVehicleFeed
import live.nesh.cluster.telematics.model.GpsFeed
import live.nesh.cluster.telematics.model.InstrumentationInfoRoot
import live.nesh.cluster.telematics.model.TelemetryFeedType
import live.nesh.cluster.telematics.model.VehicleFeedRoot
import live.nesh.cluster.telematics.packet.InstrumentationInfoPacket
import live.nesh.cluster.telematics.packet.VehicleFeedTelemetryPacket
import com.nlevcluster.sender.model.LocationInfo

private const val TELEMATICS_TAG = "TelematicsManager"

data class TelematicsRuntimeConfig(
    val brokerUrl: String,
    val clientId: String,
    val vehicleFeed: FeedConfig,
    val instrumentationFeed: FeedConfig,
    val enableLogging: Boolean = BuildConfig.DEBUG,
)

internal fun coreOnlyVehicleHeartbeat(
    imei: String,
    epoch: Long,
    location: LocationInfo? = null,
): VehicleFeedRoot =
    VehicleFeedRoot(
        coreVehicleFeed = CoreVehicleFeed(
            epoch = epoch,
            imei = imei,
            ignition = 0,
            vehicleStatus = "UNKNOWN",
            mainPowerStatus = 0,
            packetStatus = 0,
            externalBatteryVoltage = 0f,
        )
    ).withLocation(location)

internal fun VehicleFeedRoot.withLocation(location: LocationInfo?): VehicleFeedRoot {
    if (location == null) return this
    return copy(
        gpsFeed = GpsFeed(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude.toDouble(),
            odometer = icFeed?.odometer ?: 0.0,
            speed = location.speed.toDouble(),
            gpsDataValidityStatus = 0,
            heading = 0,
            numberOfSatellites = 0,
        )
    )
}

/** Owns the SDK lifecycle for one sender-service lifetime. */
class TelematicsManager private constructor() {

    private var initialized = false
    private var vehicleFeedRunning = false
    private var instrumentationFeedRunning = false
    @Volatile
    private var latestVehicleFeed: VehicleFeedRoot? = null
    @Volatile
    private var latestLocation: LocationInfo? = null
    private var heartbeatImei: String = ""
    private var lastIgnitionState: Boolean? = null
    private var mqttObserverJob: Job? = null
    private var networkObserverJob: Job? = null
    private var logHelper: LogHelper? = null

    private val _mqttConnection = MutableStateFlow(false)
    val mqttConnection: StateFlow<Boolean> = _mqttConnection

    private val _networkConnection = MutableStateFlow(false)
    val networkConnection: StateFlow<Boolean> = _networkConnection

    companion object {
        @Volatile
        private var instance: TelematicsManager? = null

        fun getInstance(): TelematicsManager = instance ?: synchronized(this) {
            instance ?: TelematicsManager().also { instance = it }
        }
    }

    @Synchronized
    fun start(context: Context, serviceScope: CoroutineScope, config: TelematicsRuntimeConfig) {
        logHelper = logHelper ?: NLLoggerLogHelper(context.applicationContext)
        heartbeatImei = config.clientId
        if (initialized) {
            log(LogType.INFO, "Telematics SDK already initialized; applying feed configuration")
            reconfigure(config)
            return
        }

        log(LogType.INFO, "Initializing Telematics SDK")
        runSdkOperation("initialization") {
            TelematicsSdk.setDebugCallback(debugCallback)
            TelematicsSdk.initialize(
                context.applicationContext,
                TelematicsConfig(
                    brokerUrl = config.brokerUrl,
                    baseClientId = config.clientId,
                    logging = config.enableLogging,
                ),
                serviceScope,
            )
        }
        initialized = true
        log(LogType.INFO, "Telematics SDK initialized")
        observeConnectionState(serviceScope)
        reconfigure(config)
        lastIgnitionState?.let(::applyIgnitionState)
        startVehicleFeed()
        startInstrumentationFeed(context.applicationContext)
    }

    @Synchronized
    fun reconfigure(config: TelematicsRuntimeConfig) {
        check(initialized) { "TelematicsManager must be started before configuration" }
        runSdkOperation("feed configuration") {
            TelematicsSdk.configureFeeds(
                TelemetryConfig(
                    mapOf(
                        TelemetryFeedType.VEHICLE_FEED to config.vehicleFeed,
                        TelemetryFeedType.INSTRUMENTATION to config.instrumentationFeed,
                    )
                )
            )
        }
        log(LogType.INFO, TelematicsManagerDiagnostics.feedConfiguration(config))
    }

    @Synchronized
    fun submitVehicleFeed(feed: VehicleFeedRoot) {
        latestVehicleFeed = feed.withLocation(latestLocation)
        if (initialized) startVehicleFeed()
    }

    @Synchronized
    fun updateLocation(location: LocationInfo) {
        latestLocation = location
        latestVehicleFeed = latestVehicleFeed?.withLocation(location)
    }

    @Synchronized
    fun updateIgnition(isOn: Boolean) {
        if (lastIgnitionState == isOn) return
        if (!initialized) {
            lastIgnitionState = isOn
            log(LogType.INFO, "Telemetry ignition state cached: ${TelematicsManagerDiagnostics.ignitionState(isOn)}")
            return
        }
        applyIgnitionState(isOn)
    }

    @Synchronized
    fun stop() {
        if (!initialized) return
        log(LogType.INFO, "Stopping Telematics SDK")
        if (vehicleFeedRunning) {
            runSdkOperation("vehicle feed stop") { TelematicsSdk.stopFeed(TelemetryFeedType.VEHICLE_FEED) }
        }
        if (instrumentationFeedRunning) {
            runSdkOperation("instrumentation feed stop") { TelematicsSdk.stopFeed(TelemetryFeedType.INSTRUMENTATION) }
        }
        runSdkOperation("disconnect") { TelematicsSdk.disconnect() }
        mqttObserverJob?.cancel()
        networkObserverJob?.cancel()
        mqttObserverJob = null
        networkObserverJob = null
        initialized = false
        vehicleFeedRunning = false
        instrumentationFeedRunning = false
        latestVehicleFeed = null
        latestLocation = null
        heartbeatImei = ""
        lastIgnitionState = null
        _mqttConnection.value = false
        _networkConnection.value = false
        log(LogType.INFO, "Telematics SDK stopped")
        logHelper = null
    }

    private fun startVehicleFeed() {
        if (!initialized || vehicleFeedRunning) return
        vehicleFeedRunning = true
        runSdkOperation("vehicle feed start") {
            TelematicsSdk.startFeed(TelemetryFeedType.VEHICLE_FEED) {
                val feed = latestVehicleFeed ?: coreOnlyVehicleHeartbeat(
                    imei = heartbeatImei,
                    epoch = System.currentTimeMillis() / 1_000L,
                    location = latestLocation,
                )
                VehicleFeedTelemetryPacket(feed)
            }
        }
        log(LogType.INFO, "Vehicle telemetry feed started")
    }

    private fun startInstrumentationFeed(context: Context) {
        if (instrumentationFeedRunning) return
        instrumentationFeedRunning = true
        runSdkOperation("instrumentation feed start") {
            TelematicsSdk.startFeed(TelemetryFeedType.INSTRUMENTATION) {
                InstrumentationInfoPacket(
                    InstrumentationInfoRoot(
                        coreVehicleFeed = latestVehicleFeed?.coreVehicleFeed,
                        instrumentationInfo = InstrumentationUtils.buildInstrumentationFeed(context),
                    )
                )
            }
        }
        log(LogType.INFO, "Instrumentation telemetry feed started")
    }

    private fun observeConnectionState(serviceScope: CoroutineScope) {
        mqttObserverJob?.cancel()
        networkObserverJob?.cancel()
        mqttObserverJob = serviceScope.launch {
            TelematicsSdk.observeMQTTConnection().collect(::updateMqttConnection)
        }
        networkObserverJob = serviceScope.launch {
            TelematicsSdk.observeNetworkConnection().collect(::updateNetworkConnection)
        }
    }

    private fun applyIgnitionState(isOn: Boolean) {
        runSdkOperation("ignition update") { TelematicsSdk.updateIgnition(isOn) }
        lastIgnitionState = isOn
        log(LogType.INFO, "Telemetry ignition state updated: ${TelematicsManagerDiagnostics.ignitionState(isOn)}")
    }

    private fun updateMqttConnection(connected: Boolean) {
        if (_mqttConnection.value == connected) return
        _mqttConnection.value = connected
        log(
            if (connected) LogType.INFO else LogType.ERROR,
            if (connected) "Telemetry MQTT connected" else "Telemetry MQTT disconnected",
        )
    }

    private fun updateNetworkConnection(connected: Boolean) {
        if (_networkConnection.value == connected) return
        _networkConnection.value = connected
        log(
            if (connected) LogType.INFO else LogType.ERROR,
            if (connected) "Telemetry network available" else "Telemetry network unavailable",
        )
    }

    private inline fun <T> runSdkOperation(operation: String, block: () -> T): T =
        try {
            block()
        } catch (error: Exception) {
            log(LogType.ERROR, "Telematics SDK $operation failed: ${error.javaClass.simpleName}")
            throw error
        }

    private fun log(type: LogType, message: String) {
        logHelper?.log(type = type, tag = TELEMATICS_TAG, message = message)
    }

    private val debugCallback = object : TelematicsDebugCallback {
        override fun onLog(log: TelematicsLog) {
            this@TelematicsManager.log(log.level.toSenderLogType(), TelematicsManagerDiagnostics.sdkLog(log))
        }
    }
}
