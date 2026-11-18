package live.nesh.music.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class MyBroadcastReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        Log.d("Test","Broadcast Received - ${intent?.action}")
        Toast.makeText(context, "Broadcast Received - ${intent?.action}", Toast.LENGTH_SHORT).show()

        when(intent?.action) {
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                Log.d("Test","Airplane Mode Changed")
                Toast.makeText(context, "Broadcast Received!", Toast.LENGTH_SHORT).show()
            }

            Intent.ACTION_BATTERY_LOW -> {
                Log.d("Test","Battery Low")
                Toast.makeText(context, "Battery Low", Toast.LENGTH_SHORT).show()
            }

            Intent.ACTION_POWER_CONNECTED -> {
                Log.d("Test","Power Connected")
                Toast.makeText(context, "Power Connected", Toast.LENGTH_SHORT).show()
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
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
        }
    }

}