package com.nlevcluster.sender

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nlevcluster.sender.service.DataSendService
import com.nlevcluster.sender.trip.TripStatus
import com.nlevcluster.sender.trip.service.TripProcessingService
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportDatabase
import com.nlevcluster.sender.trip.db.TripReportEntity
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private var tripDao: TripReportDao? = null

    private fun startDataSendService() {
        val intent = Intent(this, DataSendService::class.java)
        startForegroundService(intent)
        viewModel.log("Started DataSendService")
    }

    private fun startTripProcessingService() {
        val intent = Intent(this, TripProcessingService::class.java)
        startForegroundService(intent)
        viewModel.log("Started TripProcessingService")
    }

    override fun onResume() {
        super.onResume()
        requestLocationPermission { }
        startDataSendService()
        startTripProcessingService()
        //finish()

        Log.d("Trip_Report","Trip Details OnResume Called()")

        lifecycleScope.launch(Dispatchers.IO) {
            tripDao = TripReportDatabase.getInstance(applicationContext).tripReportDao()
//            val insertedId = tripDao?.insertTrip(
//                TripReportEntity(
//                    tripID = UUID.randomUUID().toString(),
//                    idleDurationMs = 600000L,
//                    averageSpeedKmph = 45f,
//                    maximumSpeedKmph = 80f,
//                    startSOC = 85.0,
//                    endSOC = 45.0,
//                    speedAlertsCount = 3,
//                    idleAlertsCount = 2,
//                    harshAlertsCount = 1,
//                    modeDuration = """[{"mode":"eco","durationMillis":600000},{"mode":"sport","durationMillis":300000}]""",
//                    regenCount = 5,
//                    startOdometer = 1000.5,
//                    endOdometer = 1205.7,
//                    startEnergy = 18000,
//                    endEnergy = 13500,
//                    startTripLocationLat = 12.949565,
//                    startTripLocationLon = 80.948485,
//                    endTripLocationLat = 13.984849,
//                    endTripLocationLon = 83.948588,
//                    tripStartTimeDurationMs = System.currentTimeMillis() - 3600000,
//                    tripEndTimeDurationMs = System.currentTimeMillis(),
//                    tripStatus = TripStatus.IN_PROGRESS
//                )
//            )?.toInt() ?: 0
//            Log.d("Trip_Report","Trip Details Inserted - $insertedId")

            val allTrips = tripDao?.getAllTrips()
            Log.d("Trip_Report","All  Trip Details Trips Size - ${allTrips?.size} - ${allTrips}")

            //val updatedReport = tripDao?.updateTripStatus(insertedId, TripStatus.COMPLETED)

//            val existing = tripDao?.getTripByTripId(insertedId)
//            val updated = existing?.copy(
//                averageSpeedKmph = 50f,
//                maximumSpeedKmph = 80f
//            )
//            val updateStatus = updated?.let { tripDao?.updateTrip(it) }
//
//            Log.d("Trip_Report","updateStatus - $updateStatus All  Trip Details After - ${tripDao?.getAllTrips()}")
//
//            val updated1 = existing?.copy(
//                tripStatus = TripStatus.COMPLETED
//            )
//            val updateStatus1 = updated1?.let { tripDao?.updateTrip(it) }
//
//            Log.d("Trip_Report","updateStatus1 - $updateStatus1 All  Trip Details After - ${tripDao?.getAllTrips()}")



            val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
            val zoneId = ZoneId.systemDefault()

            val startDateTime = LocalDateTime.parse("20-05-2025 11:00:00", dateFormatter)
            val endDateTime = LocalDateTime.parse("20-05-2025 11:20:00", dateFormatter)

            val startTimestamp = startDateTime.atZone(zoneId).toInstant().toEpochMilli()
            val endTimestamp = endDateTime.atZone(zoneId).toInstant().toEpochMilli()

            val tripList = tripDao?.getTripsByEndTimeRange(startTimestamp, endTimestamp)
            Log.d("Trip_Report","Selected Trip List Count - ${tripList?.size} Selected Trip List - $tripList")



        }


    }

    private fun requestLocationPermission(onGranted: () -> Unit) {
        PermissionX.init(this)
            .permissions(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    "You need to allow necessary permissions in Settings manually",
                    "OK",
                    "Cancel"
                )
            }
            .request { allGranted, _, _ ->
                if (allGranted) {
                    onGranted()
                } else {
                    viewModel.log("Location permission denied")
                }
            }
    }
}