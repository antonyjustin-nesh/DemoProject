package com.nlevcluster.sender.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.nlevcluster.sender.BuildConfig
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.database.entity.CanParseEntity
import com.nlevcluster.sender.database.util.Util
import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.Ignition
import com.nlevcluster.sender.mqtt.MQTTClient
import com.nlevcluster.sender.util.SenderNotificationChannelId
import com.nlevcluster.sender.util.createSenderNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class DataSendService : Service() {

    @Inject
    lateinit var logHelper: LogHelper


    @Inject
    @Named("portal")
    lateinit var portalMqttClient: MQTTClient

    @Inject
    @Named("local")
    lateinit var localMqttClient: MQTTClient

    private var ignitionOnDelay = 5_000L
    private var ignitionOffDelay = 60_000L

    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var senderJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            logcat { "Scheduling send data with executor service" }
            if (senderJob == null) {
                startForeground(SenderNotificationChannelId, createSenderNotification())
                portalMqttClient.connectMqttClient()
                sendData()
            }
        } catch (ex: Exception) {
            log(
                message =
                "Exception on starting the scheduledexecutorservice ${ex.asLog()}"
            )
        }
        return START_STICKY
    }

    private fun sendData() {
        senderJob = externalScope.launch(Dispatchers.IO) {
            localMqttClient.canParsedEntityFlow.conflate().onEach { canParseEntity ->
                val dataPushDelay = if (canParseEntity.ignitionStatus == "ON") ignitionOnDelay
                else ignitionOffDelay

                delay(dataPushDelay)

                sendDataToPortal(canParseEntity)
            }.collect()
        }
    }

    private fun log(message: String) {
        if (::logHelper.isInitialized) {
            logHelper.log(LogType.INFO, message)
        }
    }

    private fun sendDataToPortal(canFeed: CanParseEntity) {
        val reqData = getDataString(canFeed)
        logcat { "sendDataToPortal $reqData" }
        portalMqttClient.publish(msg = reqData)
    }

    private fun sendOtaBroadcast(response: String) {
        val dummyResponse =
            "{\"version\":\"v1.2.3\",\"apps\":[{\"id\":3,\"downloadUrl\":\"https://firebasestorage.googleapis.com/v0/b/nlmdm-e6fdb.appspot.com/o/AndroidCluster%2FProcessor%2Fdemo%2FNL-Processor-Demo?alt=media&token=cb66ce20-b41c-4092-abc8-d809c3a3a318\",\"forceInstall\":false,\"iconUrl\":\"\",\"name\":\"NL-Processor\",\"packageName\":\"com.nlevcluster.processor\",\"version\":\"1.2.3\"}]}"
        val otaIntent = Intent(Constants.ACTION_OTA_UPDATE_AVAILABLE)

        otaIntent.putExtra(Constants.OTA_DATA, dummyResponse)
        sendBroadcast(otaIntent)
    }

    /**
     * Generate tcp/ip protocol feed
     */
//    for production
//    353555072153311

    private fun getDataString(canParseEntity: CanParseEntity): String {
//        ^NLPR|DeviceID|SequenceNumber|Latitude|Longitude|Altitude|Time|Date|Speed|Heading|
//        NoOfSat|MainPowerStatus|IgnitionStatus|MovementStatus|VehicleStatus|InterBatteryVoltage|InterBatteryPercentage|GPSOdometer
//        |GPSDataValidityStatus|PacketLiveOrStoredStatus|Battery Voltage|Battery Current|Battery SoC|Battery CapacityRemaining
//        |Vehicle Speed| Vehicle Odometer|MotorRPM||VehicleMode|Gear Postion|CustomerCode#
        val imei = if (BuildConfig.IS_PRODUCTION) {
            when (BuildConfig.TABLET) {
                "3283" -> "353555072721125"
                "4123" -> "353555072729524"
                else -> "911233851860432"
            }
        } else "911233851860432"
        //Echo/Power/Sports
        val gearPos = when (canParseEntity.evMode) {
            EvMode.SPORTS.mode -> "1"
            EvMode.POWER.mode -> "2"
            else -> "0"
        }
        //Vehicle Mode P/N/D/R
        val vehicleStatus = when (canParseEntity.ignition) {
            Ignition.REVERSE.name -> "1"
            Ignition.DRIVE.name -> "4"
            Ignition.NEUTRAL.name -> "2"
            else -> "0"
        }
        log(message = "CanParseEntity on data String $canParseEntity")
        val dataString =
            "^NLPR|$imei|${canParseEntity.id}|${canParseEntity.latitude}|${canParseEntity.longitude}|" +
                    "${canParseEntity.altitude}|${Util.getTimeFromLong(canParseEntity.createdDate)}|" +
                    "${Util.getDateFromLong(canParseEntity.createdDate)}|" +
                    "${canParseEntity.speed}|34|5|1|${returnIgnitionStatus(canParseEntity.ignitionStatus)}|1|" +
                    "${returnVehicleStatus(canParseEntity.ignitionStatus)}|3.9|${canParseEntity.soc}|" +
                    "${canParseEntity.odometer}|1|1|${canParseEntity.batteryVoltage}|${canParseEntity.batteryCurrent}|" +
                    "${canParseEntity.soc}|32|${canParseEntity.speed}|${canParseEntity.odometer}|${canParseEntity.rpm}|$vehicleStatus|$gearPos|100#"
        return dataString
    }

    private fun returnVehicleStatus(status: String): String {
        return when (status) {
            Ignition.PARK.name -> "0"
            Ignition.DRIVE.name -> "1"
            Ignition.NEUTRAL.name -> "0"
            Ignition.REVERSE.name -> "1"
            else -> "0"
        }
    }

    private fun returnIgnitionStatus(status: String): String {
        return when (status) {
            Ignition.PARK.name -> "0"
            Ignition.DRIVE.name, Ignition.NEUTRAL.name, Ignition.REVERSE.name -> "1"
            else -> "0"
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        senderJob?.cancel()
        senderJob = null
        externalScope.cancel()
        super.onDestroy()
    }
}