package com.nlevcluster.sender.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.database.entity.CanParseEntity
import com.nlevcluster.sender.database.util.Util
import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.Ignition
import com.nlevcluster.sender.mqtt.MQTTClient
import com.nlevcluster.sender.util.MQTTUtil
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

    private val portalMQTTClient = MQTTUtil()

    @Inject
    @Named("local")
    lateinit var localMqttClient: MQTTClient

    private var ignitionOnDelay = 30_000L
    private var ignitionOffDelay = 60_000L

    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var senderJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            logcat { "Scheduling send data with executor service" }
            if (senderJob == null) {
                startForeground(SenderNotificationChannelId, createSenderNotification())
                initMQTTClient()
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

    private fun initMQTTClient() {
        portalMQTTClient.connect(
            url = Constants.MQTT_PORTAL_URL,
            port = Constants.MQTT_PORTAL_PORT,
            clientId = Constants.TEST_IMEI
        ) { isConnected ->
            if (isConnected) {
                logcat { "Connected" }
            } else {
                logcat { "failed to connect" }
            }
        }
    }

    private fun sendData() {
        senderJob = externalScope.launch(Dispatchers.IO) {
            localMqttClient.canParsedEntityFlow.conflate().onEach { canParseEntity ->
                val dataPushDelay = if (canParseEntity.ignitionStatus == "ON") ignitionOnDelay
                else ignitionOffDelay

                sendDataToPortal(canParseEntity)
                delay(dataPushDelay)
                logcat("MQTT_FILE_TRANSFER") { "Sending data to portal" }
            }.collect()
        }
    }

    private fun sendDataToPortal(canFeed: CanParseEntity) {
        val feedData = createFeed(canFeed)
        val mqttPacket = """
        {
            "type":"${Constants.TOPIC_RAW_FEED}",
            "data":${feedData}
        }
        """.trimIndent()
        logcat { "sendDataToPortal $feedData" }

        portalMQTTClient.publish(Constants.TOPIC_RAW_FEED, mqttPacket.toByteArray())
    }

    private fun createFeed(canFeedEntity: CanParseEntity): String {
        val gearPos = when (canFeedEntity.evMode) {
            EvMode.SPORTS.mode -> "1"
            EvMode.POWER.mode -> "2"
            else -> "0"
        }
        //Vehicle Mode P/N/D/R
        val vehicleStatus = when (canFeedEntity.ignition) {
            Ignition.REVERSE.name -> "1"
            Ignition.DRIVE.name -> "4"
            Ignition.NEUTRAL.name -> "2"
            else -> "0"
        }
        canFeedEntity.apply {
            return """
            {
              "imei": "911233851860432",
              "sequenceNumber": "$id",
              "latitude": "$latitude",
              "longitude": "$longitude",
              "altitude": "$altitude",
              "time": "${Util.getTimeFromLong(createdDate)}",
              "date": "${Util.getDateFromLong(createdDate)}",
              "speed": "$speed",
              "heading": "34",
              "noOfSat": "5",
              "mainPowerStatus": "1",
              "ignitionStatus": "${getIgnitionStatus(ignitionStatus)}",
              "movementStatus": "1",
              "vehicleStatus": "${returnVehicleStatus(ignitionStatus)}",
              "interBatteryVoltage": "3.9",
              "interBatteryPercentage": "$soc",
              "gpsOdometer": "$odometer",
              "gpsDataValidityStatus": "1",
              "packetLiveOrStoredStatus": "1",
              "batteryVoltage": "$batteryVoltage",
              "batteryCurrent": "$batteryCurrent",
              "batterySoC": "$soc",
              "batteryCapacityRemaining": "32",
              "vehicleSpeed": "$speed",
              "vehicleOdometer": "$odometer",
              "motorRPM": "$rpm",
              "vehicleMode": "$vehicleStatus",
              "gearPosition": "$gearPos",
              "customerCode": "100"
            }
        """.trimIndent()
        }
    }


    private fun sendOtaBroadcast(response: String) {
        val dummyResponse =
            "{\"version\":\"v1.2.3\",\"apps\":[{\"id\":3,\"downloadUrl\":\"https://firebasestorage.googleapis.com/v0/b/nlmdm-e6fdb.appspot.com/o/AndroidCluster%2FProcessor%2Fdemo%2FNL-Processor-Demo?alt=media&token=cb66ce20-b41c-4092-abc8-d809c3a3a318\",\"forceInstall\":false,\"iconUrl\":\"\",\"name\":\"NL-Processor\",\"packageName\":\"com.nlevcluster.processor\",\"version\":\"1.2.3\"}]}"
        val otaIntent = Intent(Constants.ACTION_OTA_UPDATE_AVAILABLE)

        otaIntent.putExtra(Constants.OTA_DATA, dummyResponse)
        sendBroadcast(otaIntent)
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

    private fun getIgnitionStatus(status: String): String {
        return when (status) {
            Ignition.PARK.name -> "0"
            Ignition.DRIVE.name, Ignition.NEUTRAL.name, Ignition.REVERSE.name -> "1"
            else -> "0"
        }
    }

    private fun log(message: String) {
        if (::logHelper.isInitialized) {
            logHelper.log(LogType.INFO, message)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        senderJob?.cancel()
        senderJob = null
        externalScope.cancel()
        portalMQTTClient.disconnect()
        super.onDestroy()
    }
}