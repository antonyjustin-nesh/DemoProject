package live.nesh.music.phone.bridge

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

object BridgeProtocol {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /**
     * Encodes a full MEDIA_STATE frame. [artwork] identifies the current art; its data is only
     * included when [includeArtData] is set, i.e. when the cluster hasn't received it yet.
     */
    fun mediaState(snapshot: MediaSnapshot, artwork: EncodedArtwork?, includeArtData: Boolean): String =
        json.encodeToString(
            CompanionMediaState(
                type = MEDIA_STATE_TYPE,
                title = snapshot.title,
                artist = snapshot.artist,
                album = snapshot.album,
                duration = snapshot.durationMs,
                position = snapshot.positionMs,
                playbackState = playbackName(snapshot.playbackState),
                albumArt = artwork?.base64?.takeIf { includeArtData },
                albumArtId = artwork?.id,
                queue = snapshot.queue?.map {
                    CompanionQueueItem(it.id, it.title, it.artist, it.album, it.durationMs)
                },
                currentTrackId = snapshot.queue
                    ?.firstOrNull { it.title == snapshot.title && it.artist == snapshot.artist }?.id
                    ?: snapshot.title.takeIf { it.isNotBlank() },
                sourcePackage = snapshot.packageName.ifBlank { null },
                sourceAppName = snapshot.appName.ifBlank { null },
                albumArtist = snapshot.albumArtist.ifBlank { null },
                genre = snapshot.genre.ifBlank { null },
                trackNumber = snapshot.trackNumber.takeIf { it > 0L },
                trackCount = snapshot.trackCount.takeIf { it > 0L },
                year = snapshot.year.takeIf { it > 0L },
                playbackSpeed = snapshot.playbackSpeed.takeIf { it > 0f && it != 1f },
            ),
        )

    fun parseCommand(payload: String): Result<IncomingCommand> = runCatching {
        json.decodeFromString(IncomingCommand.serializer(), payload).also {
            require(it.type.equals("COMMAND", ignoreCase = true)) { "type must be COMMAND" }
        }
    }

    private fun playbackName(state: Int): String = when (state) {
        3 -> "PLAYING"
        2 -> "PAUSED"
        6 -> "BUFFERING"
        1 -> "STOPPED"
        else -> "NONE"
    }
}

@Serializable
private data class CompanionMediaState(
    val type: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val position: Long,
    val playbackState: String,
    val albumArt: String? = null,
    val albumArtId: String? = null,
    val queue: List<CompanionQueueItem>? = null,
    val currentTrackId: String? = null,
    val sourcePackage: String? = null,
    val sourceAppName: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val trackNumber: Long? = null,
    val trackCount: Long? = null,
    val year: Long? = null,
    val playbackSpeed: Float? = null,
)

@Serializable
private data class CompanionQueueItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
)

private const val MEDIA_STATE_TYPE = "MEDIA_STATE"

data class EncodedArtwork(val id: String, val base64: String)

object ArtworkEncoder {
    private const val MAX_EDGE = 320
    private const val MAX_BYTES = 100 * 1024

    fun encode(bitmap: Bitmap?): EncodedArtwork? {
        if (bitmap == null || bitmap.isRecycled) return null
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        try {
            for (quality in 85 downTo 35 step 10) {
                val output = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val bytes = output.toByteArray()
                if (bytes.size <= MAX_BYTES) {
                    val id = bytes.contentHashCode().toUInt().toString(16)
                    return EncodedArtwork(id, Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
            }
            return null
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
    }
}
