package live.nesh.music.phone.bridge

import android.graphics.Bitmap
import android.media.session.PlaybackState
import kotlinx.serialization.Serializable

data class BridgeSettings(
    val clusterAddress: String = "",
    val clusterName: String = "",
    /** True while the user wants the bridge running; drives auto-start on boot/ACL connect. */
    val autoStart: Boolean = false,
) {
    val isComplete: Boolean get() = DEVICE_ADDRESS.matches(clusterAddress)

    companion object {
        val DEVICE_ADDRESS = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    }
}

data class QueueEntry(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
)

data class MediaSnapshot(
    val sessionId: String = "",
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val trackNumber: Long = 0L,
    val trackCount: Long = 0L,
    val year: Long = 0L,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val playbackState: Int = PlaybackState.STATE_NONE,
    val artwork: Bitmap? = null,
    val queue: List<QueueEntry>? = null,
)

sealed interface MediaEvent {
    data class Snapshot(val value: MediaSnapshot) : MediaEvent
    data class Position(val sessionId: String, val positionMs: Long, val playbackState: Int) : MediaEvent
}

sealed interface BridgeConnectionState {
    data object Stopped : BridgeConnectionState
    data object Connecting : BridgeConnectionState
    data object Connected : BridgeConnectionState
    data class Retrying(val delaySeconds: Long, val reason: String) : BridgeConnectionState
    data class Blocked(val reason: String) : BridgeConnectionState
}

@Serializable
data class IncomingCommand(
    val type: String,
    val command: String,
    val position: Long? = null,
)
