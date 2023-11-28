package com.nlevcluster.sender.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import com.nlevcluster.sender.data.constants.Constants

class CollectLogBroadcastReceiver(
    private val onReceive: () -> Unit
) : BroadcastReceiver() {
    private var registered = false

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent != null && intent.getStringExtra(Constants.PACKAGE_NAME) == context.packageName) {
            onReceive.invoke()
        }
    }

    fun register(context: Context) {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_COLLECT_LOGS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    this,
                    filter,
                    AppCompatActivity.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    this,
                    filter
                )
            }
        }
        registered = true
    }

    fun unregister(context: Context) {
        if (registered) {
            context.unregisterReceiver(this)
            registered = false
        }
    }

    companion object {
        const val ACTION_COLLECT_LOGS = "com.nlev.ACTION_COLLECT_LOG"
    }

}
