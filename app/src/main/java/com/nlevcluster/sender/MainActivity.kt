package com.nlevcluster.sender

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.nlevcluster.sender.database.util.Util
import com.nlevcluster.sender.mqtt.MQTTClient
import com.nlevcluster.sender.service.DataSendService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.datetime.Clock
import logcat.logcat
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var mqttClient: MQTTClient

    companion object {
        const val SUBSCRIBE_TOPIC = "collector/cloud"
        const val SUBSCRIBE_PROCESSOR_TOPIC = "processor/cloud"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        connectMqttClient()

        val intent = Intent(this, DataSendService::class.java)
        startForegroundService(intent)


        findViewById<Button>(R.id.shareLogs).setOnClickListener {
            shareLogFile()
        }

        var version = BuildConfig.VERSION_NAME
        if (BuildConfig.IS_PRODUCTION) {
            version += when (BuildConfig.TABLET) {
                "4123" -> " Prod Tablet 1"
                "3283" -> " Prod Tablet 2"
                else -> " Prod Tablet 3"
            }
        }
        findViewById<TextView>(R.id.version).text = version

    }

    /**
     * Connecting MQTT client
     *
     */
    private fun connectMqttClient() {
        viewModel.log("Connecting MQTT client to the server")
        mqttClient.connect("", "", object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                viewModel.log("MQTT client connection succeeded")
                topicSubscription(SUBSCRIBE_PROCESSOR_TOPIC)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                viewModel.log("MQTT client connection failure with exception $exception")
            }
        })
    }

    fun topicSubscription(topic: String) {
        try {
            mqttClient.subscribe(
                mqttClient.mqttAndroidClient,
                topic,
                1,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        viewModel.log("MQTT subscription successful with topic is the $topic")
                    }

                    override fun onFailure(
                        asyncActionToken: IMqttToken?,
                        exception: Throwable?
                    ) {
                        logcat { "MQTT subscription unsuccessful with topic $topic with exception $exception" }
                    }

                })
        } catch (ex: Exception) {
            logcat { "Exception while subscribing to topic $topic. Detailed exception $ex" }
        }
    }

    override fun onDestroy() {
        mqttClient.disconnect()
        super.onDestroy()
    }

    /**
     * Share logs files for debugging purpose
     *
     */
    private fun shareLogFile() {
        val file = File(
            this.cacheDir.absolutePath + "/candata-${
                Util.getDateFromLong(
                    Clock.System.now().toEpochMilliseconds()
                )
            }.txt"
        );
        if (!file.exists()) {
            file.createNewFile()
        }
        viewModel.shareWriteableFile(file)
        val emailIntent = Intent(Intent.ACTION_SEND)
        emailIntent.type = "text/plain"
        emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("support@nesh.live"))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Can Data")
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Getting can data")
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = FileProvider.getUriForFile(
            this,
            this.applicationContext.packageName + ".provider",
            file
        );
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri)
        startActivity(Intent.createChooser(emailIntent, ""))
    }

}