package live.nesh.music.phone.bridge

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow

object BridgeRuntime {
    val state = MutableStateFlow<BridgeConnectionState>(BridgeConnectionState.Stopped)

    /** What the bridge is currently sending, for the in-app preview. */
    val snapshot = MutableStateFlow(MediaSnapshot())
}

object MediaBridgeAccess {
    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val component = ComponentName(context, MediaBridgeNotificationListenerService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }
}
