package com.nlevcluster.sender.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.nlevcluster.sender.R
import com.nlevcluster.sender.service.DataSendService

const val SenderNotificationChannelId = 2
private const val SenderNotificationChannelID = "SenderNotificationChannel"

fun DataSendService.createSenderNotification(): Notification {
    val channel = NotificationChannel(
        SenderNotificationChannelID,
        SenderNotificationChannelID,
        NotificationManager.IMPORTANCE_NONE,
    ).apply {
        description = getString(R.string.data_sending)
    }
    // Register the channel with the system
    val notificationManager: NotificationManager? =
        getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    notificationManager?.createNotificationChannel(channel)

    return NotificationCompat.Builder(
        this,
        SenderNotificationChannelID,
    )
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.data_sending))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
}