package live.nesh.music.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class MyBroadcastReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        val action = intent?.action
        val extrasText = getExtrasAsString(intent)

        // Full details in Logcat
        Log.d("Test", "Broadcast Received - $action")
        Log.d("Test", "Extras:\n$extrasText")


        Toast.makeText(context, "Broadcast Received - $action", Toast.LENGTH_SHORT).show()


        when(action) {
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val isAirplaneModeOn = intent.getBooleanExtra("state", false)
                if (isAirplaneModeOn) {
                    Log.d("Test", "Airplane Mode is ON")
                    Toast.makeText(context, "Airplane Mode is ON", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("Test", "Airplane Mode is OFF")
                    Toast.makeText(context, "Airplane Mode is OFF", Toast.LENGTH_SHORT).show()
                }
            }

            Intent.ACTION_BATTERY_LOW -> {
                Log.d("Test","Battery Low")
                Toast.makeText(context, "Battery Low", Toast.LENGTH_SHORT).show()
            }

            Intent.ACTION_POWER_CONNECTED -> {
//                adb shell cmd battery reset
                Log.d("Test","Power Connected")
                Toast.makeText(context, "Power Connected", Toast.LENGTH_SHORT).show()
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
//                adb shell cmd battery unplug
                Log.d("Test","Power Disconnected")
                Toast.makeText(context, "Power Disconnected", Toast.LENGTH_SHORT).show()
            }

            Intent.ACTION_SCREEN_OFF -> {
                Log.d("Test","Screen off")
                Toast.makeText(context, "Screen off", Toast.LENGTH_SHORT).show()
            }

            Intent.ACTION_SCREEN_ON -> {
                Log.d("Test","Screen on")
                Toast.makeText(context, "Screen on", Toast.LENGTH_SHORT).show()
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("Test","Boot Completed")
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