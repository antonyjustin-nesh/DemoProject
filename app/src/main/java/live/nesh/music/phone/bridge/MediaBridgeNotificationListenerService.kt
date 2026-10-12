package live.nesh.music.phone.bridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow

/** Android grants MediaSessionManager visibility through this enabled listener component. */
class MediaBridgeNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        events.tryEmit(Unit)
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "Notification listener disconnected")
        events.tryEmit(Unit)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        events.tryEmit(Unit)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        events.tryEmit(Unit)
    }

    companion object {
        private const val TAG = "MediaBridgeListener"
        val events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    }
}
