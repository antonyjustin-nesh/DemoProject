package com.nlevcluster.sender

import android.content.Intent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.nlevcluster.sender.service.DataSendService
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    private fun startDataSendService() {
        val intent = Intent(this, DataSendService::class.java)
        startForegroundService(intent)
        viewModel.log("Started DataSendService")
    }

    override fun onResume() {
        super.onResume()
        requestLocationPermission {

        }
        startDataSendService()
        finish()
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