package com.nlevcluster.sender.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.nlevcluster.sender.R
import kotlinx.datetime.Clock
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtil {

    private const val LOG_DIR = "nlev-logs"

    private val dateFormat = SimpleDateFormat("ddMMyy", Locale.US)

    private fun getDateFromLong(dtLong: Long): String {
        return dateFormat.format(Date(dtLong))
    }

    fun saveLogTextToFile(context: Context, content: String, onComplete: (Uri) -> Unit) {
        val date = getDateFromLong(
            Clock.System.now().toEpochMilliseconds()
        )
        val appName = context.getString(R.string.app_name).lowercase()
        val fileName = "candata-$appName-$date.txt"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val filePath = "${Environment.DIRECTORY_DOWNLOADS}/$LOG_DIR/$date"

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, filePath)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        PrintWriter(outputStream).use { writer ->
                            writer.write(content)
                        }
                        onComplete(uri)
                    }
                }
        } else {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val nlevDownloadDir = File(downloadsDir, "$LOG_DIR/$date")

            if (!nlevDownloadDir.exists()) {
                nlevDownloadDir.mkdir()
            }

            val file = File(nlevDownloadDir, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }

            file.printWriter().use { writer ->
                writer.write(content)
                onComplete(file.toUri())
            }
        }
    }
}