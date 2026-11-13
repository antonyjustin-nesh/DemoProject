package live.nesh.music.phone

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MyService : Service() {
    private val channelId = "service_channel"
    private val notificationId = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        startForeground(
            notificationId,
            createNotification("Starting...")
        )

        Thread {
            try {
                // Download simulation
                for (progress in 0..100 step 10) {

                    updateNotification("Downloading update: $progress%")

                    Thread.sleep(1000)
                }

                // Installation simulation
                updateNotification("Installing update...")
                Thread.sleep(3000)

                updateNotification("Update completed!")

            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }

        }.start()

        return START_STICKY
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("OTA Update")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        NotificationManagerCompat.from(this)
            .notify(notificationId, createNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "OTA Update",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}