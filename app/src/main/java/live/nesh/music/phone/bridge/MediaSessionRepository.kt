package live.nesh.music.phone.bridge

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Observes active local media sessions. Notification-listener access is required by Android. */
class MediaSessionRepository(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mediaSessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _snapshot = MutableStateFlow(MediaSnapshot())
    private val _events = MutableSharedFlow<MediaEvent>(extraBufferCapacity = 32)
    val snapshot: StateFlow<MediaSnapshot> = _snapshot.asStateFlow()
    val events: SharedFlow<MediaEvent> = _events.asSharedFlow()

    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private val appLabels = mutableMapOf<String, String>()

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        selectController(sessions.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishSnapshot()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishSnapshot()
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = publishSnapshot()
        override fun onSessionDestroyed() = selectController(emptyList())
    }

    fun start(listenerComponent: ComponentName) {
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent, handler)
            val sessions = mediaSessionManager.getActiveSessions(listenerComponent)
            Log.i(TAG, "Media session observer started; activeSessions=${sessions.size}")
            selectController(sessions)
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification listener access is not enabled", error)
            clearController()
        }
    }

    fun refresh(listenerComponent: ComponentName) {
        try {
            selectController(mediaSessionManager.getActiveSessions(listenerComponent))
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to refresh active media sessions", error)
        }
    }

    fun execute(command: IncomingCommand): Boolean {
        val current = controller ?: return false.also { Log.w(TAG, "Ignoring ${command.command}: no active session") }
        val playback = current.playbackState
        val action = when (command.command.uppercase()) {
            "PLAY" -> PlaybackState.ACTION_PLAY
            "PAUSE" -> PlaybackState.ACTION_PAUSE
            "NEXT" -> PlaybackState.ACTION_SKIP_TO_NEXT
            "PREVIOUS" -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
            "SEEK" -> PlaybackState.ACTION_SEEK_TO
            else -> return false.also { Log.w(TAG, "Ignoring unknown command ${command.command}") }
        }
        if (command.command.equals("SEEK", true) && (command.position == null || command.position < 0L)) {
            return false.also { Log.w(TAG, "Ignoring invalid SEEK position") }
        }
        if (playback != null && playback.actions and action == 0L) {
            return false.also { Log.w(TAG, "Ignoring unsupported command ${command.command}") }
        }
        return try {
            when (command.command.uppercase()) {
                "PLAY" -> current.transportControls.play()
                "PAUSE" -> current.transportControls.pause()
                "NEXT" -> current.transportControls.skipToNext()
                "PREVIOUS" -> current.transportControls.skipToPrevious()
                "SEEK" -> current.transportControls.seekTo(command.position!!)
            }
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Transport command failed: ${command.command}", error)
            false
        }
    }

    private fun selectController(sessions: List<MediaController>) {
        val candidates = sessions.filter(::isMediaController)
        val selected = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull()
        if (selected?.sessionToken == controller?.sessionToken) {
            publishSnapshot()
            return
        }
        clearController(publishEmpty = false)
        controller = selected
        Log.i(
            TAG,
            "Selected media controller package=${selected?.packageName ?: "none"} " +
                "candidates=${candidates.size} totalSessions=${sessions.size}",
        )
        selected?.registerCallback(controllerCallback, handler)
        publishSnapshot()
    }

    private fun clearController(publishEmpty: Boolean = true) {
        positionJob?.cancel()
        positionJob = null
        controller?.unregisterCallback(controllerCallback)
        controller = null
        if (publishEmpty) publishSnapshot()
    }

    /**
     * Allow every media app, while excluding sessions such as phone calls that do not expose
     * metadata or media transport actions. This lets the companion work with installed players
     * beyond the original five-app allowlist.
     */
    private fun isMediaController(candidate: MediaController): Boolean {
        val metadata = candidate.metadata
        if (metadata?.getText(MediaMetadata.METADATA_KEY_TITLE) != null ||
            metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) != null
        ) return true
        val actions = candidate.playbackState?.actions ?: return false
        return actions and MEDIA_TRANSPORT_ACTIONS != 0L
    }

    private fun publishSnapshot() {
        val current = controller
        val metadata = current?.metadata
        val playback = current?.playbackState
        val packageName = current?.packageName.orEmpty()
        val next = MediaSnapshot(
            sessionId = current?.sessionToken?.toString().orEmpty(),
            packageName = packageName,
            appName = appLabel(packageName),
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: "",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            albumArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            genre = metadata?.getString(MediaMetadata.METADATA_KEY_GENRE).orEmpty(),
            trackNumber = metadata?.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER) ?: 0L,
            trackCount = metadata?.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS) ?: 0L,
            year = metadata?.getLong(MediaMetadata.METADATA_KEY_YEAR) ?: 0L,
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
            positionMs = currentPosition(playback, metadata),
            playbackSpeed = playback?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            playbackState = playback?.state ?: PlaybackState.STATE_NONE,
            artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            queue = current?.queue?.take(MAX_QUEUE_ENTRIES)?.map { item ->
                QueueEntry(
                    id = item.description.mediaId?.takeIf { it.isNotBlank() } ?: item.queueId.toString(),
                    title = item.description.title?.toString().orEmpty(),
                    artist = item.description.subtitle?.toString().orEmpty(),
                    album = item.description.description?.toString().orEmpty(),
                    durationMs = 0L,
                )
            },
        )
        _snapshot.value = next
        Log.i(
            TAG,
            "Media snapshot package=${next.packageName} title=${next.title} state=${next.playbackState} " +
                "duration=${next.durationMs} queue=${next.queue?.size ?: 0}",
        )
        _events.tryEmit(MediaEvent.Snapshot(next))
        restartPositionTicker(next)
    }

    private fun appLabel(packageName: String): String {
        if (packageName.isBlank()) return ""
        return appLabels.getOrPut(packageName) {
            runCatching {
                val packageManager = appContext.packageManager
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
        }
    }

    private fun restartPositionTicker(value: MediaSnapshot) {
        positionJob?.cancel()
        if (value.playbackState != PlaybackState.STATE_PLAYING) return
        positionJob = scope.launch {
            while (isActive) {
                delay(POSITION_INTERVAL_MS)
                val latest = _snapshot.value
                if (latest.sessionId.isBlank() || latest.playbackState != PlaybackState.STATE_PLAYING) break
                _events.emit(MediaEvent.Position(latest.sessionId, currentPosition(controller?.playbackState, controller?.metadata), latest.playbackState))
            }
        }
    }

    private fun currentPosition(playback: PlaybackState?, metadata: MediaMetadata?): Long {
        val position = playback?.position?.coerceAtLeast(0L) ?: 0L
        if (playback?.state != PlaybackState.STATE_PLAYING) return position
        val elapsed = (SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime).coerceAtLeast(0L)
        val live = position + (elapsed * playback.playbackSpeed).toLong()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }
        return if (duration == null) live else live.coerceAtMost(duration)
    }

    override fun close() {
        mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
        clearController(publishEmpty = false)
        scope.coroutineContext[Job]?.cancel()
    }

    private companion object {
        const val TAG = "MediaBridgeSession"
        const val POSITION_INTERVAL_MS = 1_000L
        const val MAX_QUEUE_ENTRIES = 100
        val MEDIA_TRANSPORT_ACTIONS = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO
    }
}
