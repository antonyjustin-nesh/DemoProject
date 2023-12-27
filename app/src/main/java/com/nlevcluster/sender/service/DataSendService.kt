package com.nlevcluster.sender.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.nlevcluster.sender.BuildConfig
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.database.dao.CanParseDao
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import javax.inject.Inject

@AndroidEntryPoint
class DataSendService : Service() {

    @Inject
    lateinit var canParseDao: CanParseDao

    @Inject
    lateinit var logHelper: LogHelper

    @Inject
    lateinit var mqttClient: MQTTClient

    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ignitionOnDelay = 30000L
    private var ignitionOffDelay = 60000L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(SenderNotificationChannelId, createSenderNotification())
            logcat { "Scheduling send data with executor service" }
            sendData()
        } catch (ex: Exception) {
            log(
                message =
                "Exception on starting the scheduledexecutorservice ${ex.asLog()}"
            )
        }
        return START_STICKY
    }

    private fun sendData() {
        externalScope.launch {
            mqttClient.canParsedEntityFlow.conflate()
                .onEach { canParseEntity ->
                    val dataPushDelay = if (canParseEntity.ignitionStatus == "ON") ignitionOnDelay
                    else ignitionOffDelay
                    delay(dataPushDelay)
                }.collect { canParseEntity ->
                    logcat { "collected can feed" }
                    sendDataToBackEnd(canParseEntity)
                }
        }
    }

    private fun log(message: String) {
        if (this::logHelper.isInitialized) {
            logHelper.log(LogType.INFO, message)
        }
    }

    //Send Data to backend
    private fun sendDataToBackEnd(canFeed: CanParseEntity) {
        externalScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val url = if (BuildConfig.IS_PRODUCTION) "ev.nesh.live"
                    else "qadevice.nesh.live"

                    Socket(url, 7788).use { socket ->
                        PrintWriter(socket.getOutputStream()).use { outputStream ->
                            // Send data to the backend
                            val reqData = getDataString(canFeed)
                            logcat { "Socket Request - $reqData" }
                            outputStream.println(reqData)
                            outputStream.flush()

                            // Receive response from the backend
                            BufferedReader(
                                InputStreamReader(
                                    socket.getInputStream(),
                                    Charsets.UTF_8
                                )
                            ).use { reader ->
                                val response = reader.readLine()
                                logcat { "Socket Response - $response" }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    log("Exception on sending/receiving data to/from the backend using socket $ex")
                }
            }
        }
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
        mqttClient.disconnect()
        externalScope.cancel()
        super.onDestroy()
    }
}