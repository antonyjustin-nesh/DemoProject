package com.nlevcluster.sender

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.nlevcluster.sender.service.DataSendService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private fun startDataSendService() {
        val intent = Intent(this, DataSendService::class.java)
        startForegroundService(intent)
    }

    override fun onResume() {
        super.onResume()
        startDataSendService()
        finish()
    }
}