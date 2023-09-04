package com.nlevcluster.sender

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nlevcluster.sender.database.LogType
import com.nlevcluster.sender.database.dao.CanParseDao
import com.nlevcluster.sender.database.dao.LogDao
import com.nlevcluster.sender.database.entity.CanParseEntity
import com.nlevcluster.sender.database.util.Util
import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.model.EvMode
import com.nlevcluster.sender.model.Ignition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.io.File
import java.io.PrintWriter
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val logHelper: LogHelper,
    private val canParseDao: CanParseDao,
    private val logDao: LogDao,
) : ViewModel() {

    private var ignitionOff = true
    private var count = 0
    private var timing = 0

    /**
     * Logging the message to the database
     *
     * @param message
     */
    fun log(message: String) {
        logHelper.log(message = message)
        logcat(LogPriority.INFO, "MainViewModel") {
            message
        }
    }

    /**
     * Send data to backend every 30 seconds on background when ignition is ON and every 10 minutes when ignition is OFF
     */
    fun sendData() {
        val scheduledExecutorService: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor()
        scheduledExecutorService.scheduleWithFixedDelay({
            viewModelScope.launch(Dispatchers.IO) {
                val canFeed = canParseDao.getLastCanParseEntity()
                if (canFeed != null) {
                    log("CanParseEntity from database $canFeed")
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
        }, 0, 30, TimeUnit.SECONDS)
    }

    //Send Data to backend
    private fun sendDataToBackEnd(canFeed: CanParseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
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
                logHelper.log(
                    LogType.EXCEPTION,
                    "Exception on sending data to the backend using socket $ex"
                )
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
        log("CanParseEntity on data String $canParseEntity")
        val dataString =
            "^NLPR|$imei|${canParseEntity.id}|${canParseEntity.latitude}|${canParseEntity.longitude}|" +
                    "${canParseEntity.altitude}|${Util.getTimeFromLong(canParseEntity.createdDate)}|" +
                    "${Util.getDateFromLong(canParseEntity.createdDate)}|" +
                    "${canParseEntity.speed}|34|5|1|${returnIgnitionStatus(canParseEntity.ignitionStatus)}|1|" +
                    "${returnVehicleStatus(canParseEntity.ignitionStatus)}|3.9|${canParseEntity.soc}|" +
                    "${canParseEntity.odometer}|1|1|${canParseEntity.batteryVoltage}|${canParseEntity.batteryCurrent}|" +
                    "${canParseEntity.soc}|32|${canParseEntity.speed}|${canParseEntity.odometer}|${canParseEntity.rpm}|$vehicleStatus|$gearPos|100#"
        logHelper.log(LogType.INFO, "Sending data to backend $dataString")
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

    /**
     * Getting the logs from the database and writing on the file
     *
     * @param file
     */
    fun shareWriteableFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val logDate = LocalDate.now()
            val startOfDate = logDate.atStartOfDay()
            val dateLong = startOfDate.atZone(ZoneId.systemDefault()).toInstant().epochSecond
            val logToday = logDao.getTodaysData(dateLong)
            file.printWriter().use { out ->
                logToday.forEach {
                    out.println(it.toString())
                }
            }
        }
    }


}