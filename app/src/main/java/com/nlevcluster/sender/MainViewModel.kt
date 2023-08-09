package com.nlevcluster.sender

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nlevcluster.sender.database.dao.CanParseDao
import com.nlevcluster.sender.database.dao.LogDao
import com.nlevcluster.sender.database.entity.CanParseEntity
import com.nlevcluster.sender.database.util.Util
import dagger.hilt.android.lifecycle.HiltViewModel
import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.model.Ignition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.io.File
import java.io.PrintWriter
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
     * Send data to backend every 10 seconds on background
     */
    fun sendData() {
        val scheduledExecutorService: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor()
        scheduledExecutorService.scheduleWithFixedDelay({
            viewModelScope.launch(Dispatchers.IO) {
                val canFeed = canParseDao.getLastCanParseEntity()
                if (canFeed != null) {
                    log("CanParseEntity from database $canFeed")
                    viewModelScope.launch(Dispatchers.IO) {
                        val socket = java.net.Socket("qa.nesh.live", 7788)
                        val outputStream = PrintWriter(socket.getOutputStream())
                        outputStream.println(getDataString(canFeed))
                        outputStream.flush()
                        outputStream.close()
                        socket.close()
                    }
                }
            }
        }, 0, 10, TimeUnit.SECONDS)
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
        return "^NLPR|350544503177043|${canParseEntity.id}|${canParseEntity.latitude}|${canParseEntity.longitude}|" +
                "${canParseEntity.altitude}|${Util.getTimeFromLong(canParseEntity.createdDate)}|" +
                "${Util.getDateFromLong(canParseEntity.createdDate)}|" +
                "${canParseEntity.speed}|34|5|1|${returnIgnitionStatus(canParseEntity.ignitionStatus)}|1|" +
                "${returnVehicleStatus(canParseEntity.ignitionStatus)}|3.9|${canParseEntity.soc}|" +
                "${canParseEntity.odometer}|1|1|${canParseEntity.batteryVoltage}|${canParseEntity.batteryCurrent}|" +
                "${canParseEntity.soc}|32|${canParseEntity.speed}|152|${canParseEntity.rpm}|2|0|100#"
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
            val logWhole = logDao.getAll()
            file.printWriter().use { out ->
                logWhole.forEach {
                    out.println(it.toString())
                }
            }
        }
    }


}