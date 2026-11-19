package live.nesh.music.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    lateinit var broadcastReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("Test","Activity - A onCreate Called")

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnBindService = findViewById<Button>(R.id.btnBindService)
        val btnUnBindService = findViewById<Button>(R.id.btnUnBindService)
        val btnGetNumber = findViewById<Button>(R.id.btnGetNumber)

        broadcastReceiver = MyBroadcastReceiver()

        // START SERVICE
        btnStart.setOnClickListener {
            val intent = Intent(this, MyBroadcastReceiver::class.java)
            intent.setAction("com.nesh.demo.MY_BROADCAST")
            sendBroadcast(intent)
        }

        btnStop.setOnClickListener {
        }

    }

    override fun onStart() {
        super.onStart()
        Log.d("Test","Activity - A onStart Called")
/*        adb shell am broadcast -a com.nesh.demo.MY_BROADCAST -p com.nesh.demo1*/ /*working*/
/*        adb shell am broadcast -n com.nesh.demo1/live.nesh.music.phone.MyBroadcastReceiver -a com.nesh.demo.MY_BROADCAST*/   /*not working*/


        val intentFilter = IntentFilter("com.nesh.demo.MY_BROADCAST")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }

    }

    override fun onPause() {
        super.onPause()
        Log.d("Test","Activity - A onPause Called")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d("Test","Activity - A onRestart Called")
    }

    override fun onResume() {
        super.onResume()
        Log.d("Test","Activity - A onResume Called")
    }

    override fun onStop() {
        super.onStop()
        Log.d("Test","Activity - A onStop Called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Test","Activity - A onDestroy Called")
        unregisterReceiver(broadcastReceiver)
    }

}
