package live.nesh.music.phone.bridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID

/** One RFCOMM client connection. Reconnection policy belongs to the foreground service. */
class SppClient(private val context: Context) {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    suspend fun connect(settings: BridgeSettings): BluetoothSocket = withContext(Dispatchers.IO) {
        check(hasRequiredPermissions(context)) { "bluetooth_permissions_missing" }
        check(settings.isComplete) { "invalid_cluster_address" }
        val bluetoothAdapter = checkNotNull(adapter) { "bluetooth_not_supported" }
        check(bluetoothAdapter.isEnabled) { "bluetooth_disabled" }
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(settings.clusterAddress)
        check(device.bondState == BluetoothDevice.BOND_BONDED) { "device_not_bonded" }
        Log.i(TAG, "Connecting RFCOMM to ${device.address} (${device.name ?: "unknown"})")
        device.createRfcommSocketToServiceRecord(CLUSTER_MEDIA_SERVICE_UUID).also { socket ->
            try {
                socket.connect()
                Log.i(TAG, "RFCOMM connected to ${device.address}")
            } catch (error: IOException) {
                Log.w(TAG, "RFCOMM connect failed for ${device.address}: ${error.message}")
                socket.close()
                throw error
            }
        }
    }

    suspend fun run(
        socket: BluetoothSocket,
        outbound: Channel<ByteArray>,
        onIncomingPayload: suspend (String) -> Unit,
    ) = coroutineScope {
        val writer = launch(Dispatchers.IO) {
            try {
                val output = socket.outputStream
                for (payload in outbound) {
                    output.write(ByteBuffer.allocate(FRAME_HEADER_BYTES).putInt(payload.size).array())
                    output.write(payload)
                    output.flush()
                }
            } finally {
                runCatching { socket.outputStream.close() }
            }
        }
        try {
            val input = socket.inputStream
            try {
                while (true) {
                    val payloadSize = withContext(Dispatchers.IO) { input.readFrameSize() }
                    if (payloadSize !in 1..MAX_FRAME_BYTES) throw IOException("Invalid inbound frame size: $payloadSize")
                    val payload = ByteArray(payloadSize)
                    withContext(Dispatchers.IO) { input.readFully(payload) }
                    onIncomingPayload(String(payload, Charsets.UTF_8))
                }
            } finally {
                runCatching { input.close() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            writer.cancel()
            runCatching { socket.close() }
        }
    }

    companion object {
        private const val TAG = "MediaBridgeSpp"
        val CLUSTER_MEDIA_SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val FRAME_HEADER_BYTES = 4
        private const val MAX_FRAME_BYTES = 64 * 1024

        fun hasRequiredPermissions(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun java.io.InputStream.readFrameSize(): Int {
        val header = ByteArray(FRAME_HEADER_BYTES)
        readFully(header)
        return ByteBuffer.wrap(header).int
    }

    private fun java.io.InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) throw EOFException()
            offset += read
        }
    }
}
