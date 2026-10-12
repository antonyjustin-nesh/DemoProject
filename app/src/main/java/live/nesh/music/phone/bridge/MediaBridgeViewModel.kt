package live.nesh.music.phone.bridge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import live.nesh.music.phone.R

data class MediaBridgeUiState(
    val settings: BridgeSettings = BridgeSettings(),
    val notificationAccessGranted: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
    val connectionState: BridgeConnectionState = BridgeConnectionState.Stopped,
    val snapshot: MediaSnapshot = MediaSnapshot(),
) {
    val canStart: Boolean get() = settings.isComplete && notificationAccessGranted && bluetoothPermissionGranted
    val isRunning: Boolean get() = connectionState !is BridgeConnectionState.Stopped &&
        connectionState !is BridgeConnectionState.Blocked
}

class MediaBridgeViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = BridgeSettingsRepository(application)
    private val access = MutableStateFlow(currentAccess())

    val uiState: StateFlow<MediaBridgeUiState> = combine(
        settingsRepository.settings,
        access,
        BridgeRuntime.state,
        BridgeRuntime.snapshot,
    ) { settings, (notification, bluetooth), connection, snapshot ->
        MediaBridgeUiState(settings, notification, bluetooth, connection, snapshot)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaBridgeUiState())

    fun refreshAccess() {
        access.value = currentAccess()
    }

    fun saveCluster(address: String, name: String) {
        val wasRunning = uiState.value.isRunning
        viewModelScope.launch {
            settingsRepository.saveCluster(address, name)
            // The service reads the address once at start, so restart it to pick up the new one.
            if (wasRunning) {
                MediaBridgeForegroundService.stop(getApplication())
                MediaBridgeForegroundService.start(getApplication())
            }
        }
    }

    fun startBridge() {
        val state = uiState.value
        val blocked = when {
            !state.settings.isComplete -> R.string.blocked_no_cluster
            !state.notificationAccessGranted -> R.string.blocked_notification_access
            !state.bluetoothPermissionGranted -> R.string.blocked_bluetooth_permission
            else -> null
        }
        if (blocked != null) {
            BridgeRuntime.state.value = BridgeConnectionState.Blocked(getApplication<Application>().getString(blocked))
            return
        }
        viewModelScope.launch { settingsRepository.setAutoStart(true) }
        MediaBridgeForegroundService.start(getApplication())
    }

    fun stopBridge() {
        viewModelScope.launch { settingsRepository.setAutoStart(false) }
        MediaBridgeForegroundService.stop(getApplication())
    }

    private fun currentAccess(): Pair<Boolean, Boolean> = Pair(
        MediaBridgeAccess.hasNotificationAccess(getApplication()),
        SppClient.hasRequiredPermissions(getApplication()),
    )
}
