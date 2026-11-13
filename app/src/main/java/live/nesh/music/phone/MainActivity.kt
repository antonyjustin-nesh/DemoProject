package live.nesh.music.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    lateinit var button: Button
    lateinit var btnFinish: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button = findViewById(R.id.btnClick)
        button.setOnClickListener {

            val channel = NotificationChannel(
                "service_channel",
                "Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val intent = Intent(this, MyService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }


        btnFinish = findViewById(R.id.btnFinish)
        btnFinish.setOnClickListener {
            stopService(Intent(this, MyService::class.java))
        }

    }

    override fun onStart() {
        super.onStart()
        Log.d("Test", "Activity 1 onStart Called ${Thread.currentThread().id}")
    }

    override fun onResume() {
        super.onResume()
        Log.d("Test", "Activity 1  onResume Called ${Thread.currentThread().id}")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d("Test", "Activity 1 onRestart Called ${Thread.currentThread().id}")
    }

    override fun onPause() {
        super.onPause()
        Log.d("Test", "Activity 1 onPause Called ${Thread.currentThread().id}")
    }

    override fun onStop() {
        super.onStop()
        Log.d("Test", "Activity 1 onStop Called ${Thread.currentThread().id}")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Test", "Activity 1 onDestroy Called ${Thread.currentThread().id}")
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

}
