package com.nlevcluster.sender.service

import android.app.IntentService
import android.content.Intent
import com.nlevcluster.sender.util.NLDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@AndroidEntryPoint
class ConfigService : IntentService("ConfigService") {

    @Inject
    lateinit var nlDataStore: NLDataStore

    companion object {
        const val DEFAULT_IGN_ON_DELAY = 15000
        const val DEFAULT_IGN_OFF_DELAY = 60000
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val ignOnDelay = intent.getIntExtra("ign_on_delay", DEFAULT_IGN_ON_DELAY)
            val ignOffDelay = intent.getIntExtra("ign_off_delay", DEFAULT_IGN_OFF_DELAY)
            val mqttFeedUrl = intent.getStringExtra("mqtt_feed_url")

            logcat { " ignOnDelay $ignOnDelay, ignOffDelay $ignOffDelay, mqttFeedUrl $mqttFeedUrl" }
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                nlDataStore.setIgnitionOnDelay(ignOnDelay)
                nlDataStore.setIgnitionOffDelay(ignOffDelay)

                if (mqttFeedUrl != null) {
                    nlDataStore.setMqttFeedUrl(mqttFeedUrl)
                    stopService(Intent(this@ConfigService, DataSendService::class.java))
                }
            }
        }

    }
}