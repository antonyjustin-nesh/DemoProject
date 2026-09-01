package com.nlevcluster.sender

import android.content.Context
import android.util.Log
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
import live.nesh.cluster.telematics.logging.TelematicsLogLevel
import live.nesh.cluster.telematics.model.CoreVehicleFeed
import live.nesh.cluster.telematics.model.GpsFeed
import live.nesh.cluster.telematics.model.InstrumentationInfoRoot
import live.nesh.cluster.telematics.model.TelemetryFeedType
import live.nesh.cluster.telematics.model.VehicleFeedRoot
import live.nesh.cluster.telematics.packet.InstrumentationInfoPacket
import live.nesh.cluster.telematics.packet.VehicleFeedTelemetryPacket
import com.nlevcluster.sender.model.LocationInfo

private const val TELEMATICS_TAG = "TelematicsSdk"

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
        heartbeatImei = config.clientId
        if (initialized) {
            reconfigure(config)
            return
        }

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
        initialized = true
        observeConnectionState(serviceScope)
        reconfigure(config)
        lastIgnitionState?.let(TelematicsSdk::updateIgnition)
        startVehicleFeed()
        startInstrumentationFeed(context.applicationContext)
    }

    @Synchronized
    fun reconfigure(config: TelematicsRuntimeConfig) {
        check(initialized) { "TelematicsManager must be started before configuration" }
        Log.d("Telematics_Test", "reconfigure - config $config")
        TelematicsSdk.configureFeeds(
            TelemetryConfig(
                mapOf(
                    TelemetryFeedType.VEHICLE_FEED to config.vehicleFeed,
                    TelemetryFeedType.INSTRUMENTATION to config.instrumentationFeed,
                )
            )
        )
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
            return
        }
        TelematicsSdk.updateIgnition(isOn)
        lastIgnitionState = isOn
    }

    @Synchronized
    fun stop() {
        if (!initialized) return
        if (vehicleFeedRunning) TelematicsSdk.stopFeed(TelemetryFeedType.VEHICLE_FEED)
        if (instrumentationFeedRunning) TelematicsSdk.stopFeed(TelemetryFeedType.INSTRUMENTATION)
        TelematicsSdk.disconnect()
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
    }

    private fun startVehicleFeed() {
        if (!initialized || vehicleFeedRunning) return
        vehicleFeedRunning = true
        Log.d("Telematics_Test", "startVehicleFeed Called()")
        TelematicsSdk.startFeed(TelemetryFeedType.VEHICLE_FEED) {
            val feed = latestVehicleFeed ?: coreOnlyVehicleHeartbeat(
                imei = heartbeatImei,
                epoch = System.currentTimeMillis() / 1_000L,
                location = latestLocation,
            )
            Log.d("Telematics_Test", "startFeed's scheduling Called vehicleFeed - $feed")
            VehicleFeedTelemetryPacket(feed)
        }
    }

    private fun startInstrumentationFeed(context: Context) {
        if (instrumentationFeedRunning) return
        instrumentationFeedRunning = true
        TelematicsSdk.startFeed(TelemetryFeedType.INSTRUMENTATION) {
            InstrumentationInfoPacket(
                InstrumentationInfoRoot(
                    coreVehicleFeed = latestVehicleFeed?.coreVehicleFeed,
                    instrumentationInfo = InstrumentationUtils.buildInstrumentationFeed(context),
                )
            )
        }
    }

    private fun observeConnectionState(serviceScope: CoroutineScope) {
        mqttObserverJob?.cancel()
        networkObserverJob?.cancel()
        mqttObserverJob = serviceScope.launch {
            TelematicsSdk.observeMQTTConnection().collect { _mqttConnection.value = it }
        }
        networkObserverJob = serviceScope.launch {
            TelematicsSdk.observeNetworkConnection().collect { _networkConnection.value = it }
        }
    }

    private val debugCallback = object : TelematicsDebugCallback {
        override fun onLog(log: TelematicsLog) {
            val message = "${log.commonTag} ${log.className} ${log.functionName} ${log.message}"
            when (log.level) {
                TelematicsLogLevel.DEBUG -> Log.d(TELEMATICS_TAG, message)
                TelematicsLogLevel.INFO -> Log.i(TELEMATICS_TAG, message)
                TelematicsLogLevel.ERROR -> Log.e(TELEMATICS_TAG, message)
            }
        }
    }
}
