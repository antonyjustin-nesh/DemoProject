package live.nesh.music.phone

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import live.nesh.music.phone.bridge.BridgeConnectionState
import live.nesh.music.phone.bridge.MediaBridgeUiState
import live.nesh.music.phone.bridge.MediaBridgeViewModel
import live.nesh.music.phone.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MediaBridgeViewModel by viewModels()

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshAccess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.version.text = "v" + BuildConfig.VERSION_NAME
        binding.chooseCluster.setOnClickListener { chooseCluster() }
        binding.grantBluetooth.setOnClickListener { requestPermissions() }
        binding.grantNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.toggleBridge.setOnClickListener {
            if (viewModel.uiState.value.isRunning) viewModel.stopBridge() else viewModel.startBridge()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may be coming back from the notification-access or permission screens.
        viewModel.refreshAccess()
    }

    private fun render(state: MediaBridgeUiState) {
        binding.status.text = when (val connection = state.connectionState) {
            BridgeConnectionState.Stopped -> getString(R.string.status_stopped)
            BridgeConnectionState.Connecting -> getString(R.string.status_connecting)
            BridgeConnectionState.Connected -> getString(R.string.status_connected)
            is BridgeConnectionState.Retrying -> getString(R.string.status_retrying, connection.delaySeconds.toInt())
            is BridgeConnectionState.Blocked -> connection.reason
        }

        val snapshot = state.snapshot
        binding.trackTitle.text = snapshot.title.ifBlank { getString(R.string.nothing_playing) }
        binding.trackArtist.text = listOf(snapshot.artist, snapshot.album).filter(String::isNotBlank).joinToString(" · ")
        binding.trackSource.text = listOfNotNull(
            snapshot.appName.ifBlank { null },
            when (snapshot.playbackState) {
                PlaybackState.STATE_PLAYING -> getString(R.string.state_playing)
                PlaybackState.STATE_PAUSED -> getString(R.string.state_paused)
                else -> null
            },
        ).joinToString(" · ")
        if (snapshot.artwork != null) {
            binding.artwork.setImageBitmap(snapshot.artwork)
        } else {
            binding.artwork.setImageDrawable(null)
        }

        binding.clusterName.text = if (state.settings.isComplete) {
            "${state.settings.clusterName.ifBlank { "Cluster" }} (${state.settings.clusterAddress})"
        } else {
            getString(R.string.no_cluster_selected)
        }

        binding.grantBluetooth.isVisible = !state.bluetoothPermissionGranted
        binding.grantNotificationAccess.isVisible = !state.notificationAccessGranted
        binding.setupLabel.isVisible = !state.bluetoothPermissionGranted || !state.notificationAccessGranted

        binding.toggleBridge.setText(if (state.isRunning) R.string.stop_bridge else R.string.start_bridge)
        binding.toggleBridge.isEnabled = state.isRunning || state.canStart
    }

    private fun requestPermissions() {
        val permissions = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionRequest.launch(permissions.toTypedArray())
    }

    @SuppressLint("MissingPermission")
    private fun chooseCluster() {
        if (!viewModel.uiState.value.bluetoothPermissionGranted) {
            requestPermissions()
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val devices = adapter?.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
        if (devices.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.no_paired_devices)
                .setPositiveButton(R.string.open_bluetooth_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val labels = devices.map { "${it.name ?: "Unknown"}\n${it.address}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_cluster_title)
            .setItems(labels) { _, which ->
                val device = devices[which]
                viewModel.saveCluster(device.address, device.name.orEmpty())
            }
            .show()
    }
}
