package live.nesh.music.phone

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    var myService = BoundServiceExample()
    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder
        ) {
            Log.d("Test","service connected")
            var mServiceBinder = service as BoundServiceExample.MyServiceBinder
            myService = mServiceBinder.getService()

        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d("Test","service dis connected")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, BoundServiceExample::class.java)

        Log.d("Test","Activity - A onCreate Called")

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnBindService = findViewById<Button>(R.id.btnBindService)
        val btnUnBindService = findViewById<Button>(R.id.btnUnBindService)
        val btnGetNumber = findViewById<Button>(R.id.btnGetNumber)


        // START SERVICE
        btnStart.setOnClickListener {
            startService(intent)
        }

        btnStop.setOnClickListener {
            stopService(intent)
        }

        btnBindService.setOnClickListener {
            bindService(intent,  serviceConnection, BIND_AUTO_CREATE)
        }

        btnUnBindService.setOnClickListener {
            unbindService(serviceConnection)
        }

        btnGetNumber.setOnClickListener {
            val receivedNumber = myService.getNumber()
            Log.d("Test","$receivedNumber")
        }

    }

    override fun onStart() {
        super.onStart()
        Log.d("Test","Activity - A onStart Called")
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
    }

}
