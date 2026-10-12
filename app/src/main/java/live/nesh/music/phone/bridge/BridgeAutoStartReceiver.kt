package live.nesh.music.phone.bridge

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts the bridge after boot, or when the phone's Bluetooth link to the saved cluster comes up,
 * but only if the user left the bridge switched on.
 */
class BridgeAutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connectedAddress = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> null
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                @Suppress("DEPRECATION") // The typed overload needs API 33.
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.address ?: return
            }
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = BridgeSettingsRepository(context).settings.first()
                val isCluster = connectedAddress == null || connectedAddress.equals(settings.clusterAddress, ignoreCase = true)
                if (settings.autoStart && settings.isComplete && isCluster) {
                    Log.i(TAG, "Auto-starting media bridge (${intent.action})")
                    MediaBridgeForegroundService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "MediaBridgeAutoStart"
    }
}
