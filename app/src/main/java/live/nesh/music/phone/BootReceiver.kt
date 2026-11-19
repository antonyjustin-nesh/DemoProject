package live.nesh.music.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        val extrasText = getExtrasAsString(intent)

        // Full details in Logcat
        Log.d("Test", "Broadcast Received - $action")
        Log.d("Test", "Extras:\n$extrasText")

        Toast.makeText(context, "Broadcast Received - $action", Toast.LENGTH_SHORT).show()


        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("Test", "Boot Completed")
                Toast.makeText(context, "Boot Completed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getExtrasAsString(intent: Intent?): String {
        val extras = intent?.extras
        if (extras == null || extras.isEmpty) return "No extras"

        return extras.keySet().joinToString("\n") { key ->
            @Suppress("DEPRECATION")
            val value = extras.get(key)
            "$key = $value (${value?.javaClass?.simpleName})"
        }
    }
}