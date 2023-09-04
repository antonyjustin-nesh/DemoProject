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
import com.nlevcluster.sender.util.SenderNotificationChannelId
import com.nlevcluster.sender.util.createSenderNotification
import dagger.hilt.android.AndroidEntryPoint
import logcat.asLog
import logcat.logcat
import java.io.PrintWriter
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@AndroidEntryPoint
class DataSendService : Service() {

    private var ignitionOff = true
    private var count = 0
    private var timing = 0

    @Inject
    lateinit var canParseDao: CanParseDao

    @Inject
    lateinit var logHelper: LogHelper


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(SenderNotificationChannelId, createSenderNotification())
            logcat { "Scheduling send data with executor service" }
            scheduleDataSend()
        } catch (ex: Exception) {
            log(
                message =
                "Exception on starting the scheduledexecutorservice ${ex.asLog()}"
            )
        }
        return START_STICKY
    }

    /**
     * Scheduling executor service
     *
     */
    private fun scheduleDataSend() {
        var timerTask = object : TimerTask() {
            override fun run() {
                log(message = "Data send scheduling")
                val canFeed = canParseDao.getLastCanParseEntity()
                log(message = "CanParseEntity from database $canFeed")
                if (canFeed != null) {
                    log(message = "CanParseEntity from database $canFeed")
                    if (canFeed.ignitionStatus == "ON") {
                        //Resetting values back to 0 once the ignition is ON
                        ignitionOff = false
                        count = 0
                        timing = 0
                        sendDataToBackEnd(canFeed)
                    } else if (canFeed.ignitionStatus == "OFF") {
                        ignitionOff = true
                        if (count == 0 && (timing / 60 == 10 || timing / 60 == 0)) {
                            count += 1
                            timing += 30
                            sendDataToBackEnd(canFeed)
                        }
                    }
                }
            }

        }
        Timer().scheduleAtFixedRate(
            timerTask, 0L, 30000L
        )

    }

    private fun log(message: String) {
        if (this::logHelper.isInitialized) {
            logHelper.log(LogType.INFO, message)
        }
    }

    //Send Data to backend
    private fun sendDataToBackEnd(canFeed: CanParseEntity) {
        try {
            val url =
                if (BuildConfig.IS_PRODUCTION) "ev.nesh.live" else "qa.nesh.live"
            val socket = java.net.Socket(url, 7788)
            val outputStream = PrintWriter(socket.getOutputStream())
            outputStream.println(getDataString(canFeed))
            outputStream.flush()
            outputStream.close()
            socket.close()
        } catch (ex: Exception) {
            log(
                "Exception on sending data to the backend using socket $ex"
            )
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
                else -> "353555072153311"
            }
        } else "350544503177043"
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
        log("Sending data to backend $dataString")
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
        super.onDestroy()
    }
}