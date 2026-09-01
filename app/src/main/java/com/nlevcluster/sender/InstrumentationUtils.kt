package com.nlevcluster.sender

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import live.nesh.cluster.telematics.model.CpuCoreInfo
import live.nesh.cluster.telematics.model.InstrumentationInfo
import live.nesh.cluster.telematics.model.MemoryInfo
import java.io.File

object InstrumentationUtils {

    private const val TAG = "InstrumentationUtils"
    private const val THERMAL_PATH = "/sys/class/thermal"

    /* ---------------------------------- PUBLIC API ---------------------------------- */

    fun buildInstrumentationFeed(
        context: Context,
        bootTimeMs: Long = 0L,
        avgResponseMs: Long = 0L,
        maxResponseMs: Long = 0L,
        telemetryLossPercent: Float = 0f
    ): InstrumentationInfo {

        return InstrumentationInfo(
            bootTimeMs = bootTimeMs,
            avgResponseTimeMs = avgResponseMs,
            maxResponseTimeMs = maxResponseMs,
            cpuInfo = collectCpuInfo(),
            memoryInfo = collectMemoryInfo(context),
            telemetryLossPercent = telemetryLossPercent,
            deviceTemperatureC = getCpuTemperature(),
            timestamp = System.currentTimeMillis() / 1000
        )
    }

    /* ---------------------------------- CPU INFO ---------------------------------- */

    private fun collectCpuInfo(): List<CpuCoreInfo> {
        val coreCount = Runtime.getRuntime().availableProcessors()

        return (0 until coreCount).map { core ->
            val currentFreq = readFreq(
                "/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_cur_freq",
                "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq"
            )

            val maxFreq = readFreq(
                "/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq"
            )

            val usagePercent =
                if (currentFreq > 0 && maxFreq > 0)
                    ((currentFreq.toFloat() / maxFreq) * 100).toInt()
                else 0

            CpuCoreInfo(
                core = "CPU ${core + 1}",
                usagePercent = usagePercent,
                currentFreqKHz = currentFreq,
                maxFreqKHz = maxFreq
            )
        }
    }

    /* ---------------------------------- MEMORY INFO ---------------------------------- */

    private fun collectMemoryInfo(context: Context): List<MemoryInfo> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val totalMemoryBytes = mi.totalMem
        val availableMemoryBytes = mi.availMem

        return listOf(
            MemoryInfo(unit = "Bytes", totalRAM = totalMemoryBytes),
            MemoryInfo(unit = "Bytes", availableRAM = availableMemoryBytes),
            MemoryInfo(unit = "Bytes", usedRAM = totalMemoryBytes - availableMemoryBytes)
        )
    }

    /* ---------------------------------- TEMPERATURE ---------------------------------- */

    private fun getCpuTemperature(): Float {
        val thermalDir = File(THERMAL_PATH)
        if (!thermalDir.exists()) return 0f

        val zones = thermalDir.listFiles { file ->
            file.name.startsWith("thermal_zone")
        } ?: return 0f

        var tempSum = 0f
        var count = 0

        zones.forEach { zone ->
            val type = readText(File(zone, "type"))
            val temp = readText(File(zone, "temp"))?.toFloatOrNull()

            if (type?.startsWith("cpu", ignoreCase = true) == true && temp != null) {
                tempSum += (temp / 1000f)
                count++
            }
        }

        return if (count > 0) tempSum / count else 0f
    }

    /* ---------------------------------- FILE HELPERS ---------------------------------- */

    private fun readFreq(vararg paths: String): Long {
        for (path in paths) {
            readText(File(path))?.toLongOrNull()?.let { return it }
        }
        return 0L
    }

    private fun readText(file: File): String? {
        return try {
            if (file.exists()) file.readText().trim() else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ${file.path}")
            null
        }
    }
}
