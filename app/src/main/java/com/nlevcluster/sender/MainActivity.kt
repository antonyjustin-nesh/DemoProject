package com.nlevcluster.sender

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.nlevcluster.sender.data.constants.Constants
import com.nlevcluster.sender.database.util.Util
import com.nlevcluster.sender.service.DataSendService
import com.nlevcluster.sender.util.CollectLogBroadcastReceiver
import com.nlevcluster.sender.util.FileUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.io.File

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    private var collectLogBroadcastReceiver: CollectLogBroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, DataSendService::class.java)
        startService(intent)

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

        collectLogBroadcastReceiver = CollectLogBroadcastReceiver {
            saveLogFileInDownloads()
            viewModel.log("Received broadcast for saving logs into a file")
        }

        collectLogBroadcastReceiver?.register(this)

        viewModel.log("App is started. Going to background")
        if (BuildConfig.DEBUG) {
            Toast.makeText(this, "App is started. Going to background", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveLogFileInDownloads() {
        lifecycleScope.launch(Dispatchers.IO) {
            val todayLogs = viewModel.getTodayLogs().joinToString("\n")
            FileUtil.saveLogTextToFile(this@MainActivity, todayLogs) {
                val logCollectedIntent = Intent(Constants.ACTION_LOGS_COLLECTED)
                logCollectedIntent.putExtra(Constants.PACKAGE_NAME, packageName)
                sendBroadcast(logCollectedIntent)
            }
        }
    }


    override fun onDestroy() {
        collectLogBroadcastReceiver?.unregister(this)
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


    override fun onResume() {
        super.onResume()
        moveTaskToBack(true)
    }

}