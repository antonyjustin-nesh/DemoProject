package live.nesh.music.phone.bridge

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import live.nesh.music.phone.MainActivity
import live.nesh.music.phone.R

class MediaBridgeForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settingsRepository: BridgeSettingsRepository
    private lateinit var mediaSessions: MediaSessionRepository
    private lateinit var sppClient: SppClient
    private val outbound = Channel<ByteArray>(capacity = 16)
    private var bridgeJob: Job? = null

    // Album art is encoded once per bitmap and its data sent once per connection; frames in
    // between carry only albumArtId. Guarded by sendLock.
    private val sendLock = Mutex()
    private var encodedFor: Bitmap? = null
    private var encoded: EncodedArtwork? = null
    private var artIdOnCluster: String? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = BridgeSettingsRepository(applicationContext)
        mediaSessions = MediaSessionRepository(applicationContext)
        sppClient = SppClient(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        if (bridgeJob?.isActive != true) {
            bridgeJob = serviceScope.launch { startBridge() }
        }
        return START_STICKY
    }

    private suspend fun startBridge() {
        val settings = settingsRepository.settings.first()
        Log.i(TAG, "Starting bridge for cluster ${settings.clusterAddress}")
        val blocked = when {
            !settings.isComplete -> getString(R.string.blocked_no_cluster)
            !MediaBridgeAccess.hasNotificationAccess(this) -> getString(R.string.blocked_notification_access)
            !SppClient.hasRequiredPermissions(this) -> getString(R.string.blocked_bluetooth_permission)
            else -> null
        }
        if (blocked != null) {
            BridgeRuntime.state.value = BridgeConnectionState.Blocked(blocked)
            stopSelf()
            return
        }
        val listenerComponent = ComponentName(this, MediaBridgeNotificationListenerService::class.java)
        mediaSessions.start(listenerComponent)
        serviceScope.launch {
            mediaSessions.events.collectLatest { event ->
                when (event) {
                    is MediaEvent.Snapshot -> {
                        BridgeRuntime.snapshot.value = event.value
                        enqueueSnapshot(event.value, "media session update")
                    }
                    is MediaEvent.Position -> enqueueSnapshot(
                        mediaSessions.snapshot.value.copy(
                            positionMs = event.positionMs,
                            playbackState = event.playbackState,
                        ),
                    )
                }
            }
        }
        serviceScope.launch {
            MediaBridgeNotificationListenerService.events.collect { mediaSessions.refresh(listenerComponent) }
        }
        connectLoop(settings)
    }

    private suspend fun connectLoop(settings: BridgeSettings) {
        var attempt = 0
        while (serviceScope.coroutineContext[Job]?.isActive == true) {
            if (!SppClient.hasRequiredPermissions(this)) {
                BridgeRuntime.state.value = BridgeConnectionState.Blocked(getString(R.string.blocked_bluetooth_permission))
                stopSelf()
                return
            }
            try {
                BridgeRuntime.state.value = BridgeConnectionState.Connecting
                val socket = sppClient.connect(settings)
                attempt = 0
                BridgeRuntime.state.value = BridgeConnectionState.Connected
                sendLock.withLock {
                    while (outbound.tryReceive().isSuccess) Unit
                    artIdOnCluster = null
                }
                enqueueSnapshot(mediaSessions.snapshot.value, "SPP connected initial state")
                sppClient.run(socket, outbound) { handleInbound(it) }
                throw IllegalStateException("remote_disconnected")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val seconds = RETRY_SECONDS[minOf(attempt, RETRY_SECONDS.lastIndex)]
                attempt++
                Log.w(TAG, "SPP connection ended (${error.message}); retrying in ${seconds}s")
                BridgeRuntime.state.value = BridgeConnectionState.Retrying(seconds, error.message ?: error.javaClass.simpleName)
                delay(seconds * 1_000L)
            }
        }
    }

    private fun handleInbound(payload: String) {
        val command = BridgeProtocol.parseCommand(payload).getOrElse { error ->
            Log.w(TAG, "Ignoring invalid cluster command", error)
            return
        }
        Log.i(TAG, "Received cluster command=${command.command} position=${command.position}")
        mediaSessions.execute(command)
    }

    private suspend fun enqueueSnapshot(snapshot: MediaSnapshot, reason: String? = null): Unit = sendLock.withLock {
        if (snapshot.artwork !== encodedFor) {
            encoded = withContext(Dispatchers.Default) { ArtworkEncoder.encode(snapshot.artwork) }
            encodedFor = snapshot.artwork
        }
        val artwork = encoded
        val includeArt = artwork != null && artwork.id != artIdOnCluster
        val payload = BridgeProtocol.mediaState(snapshot, artwork, includeArt).toByteArray(Charsets.UTF_8)
        if (reason != null) {
            Log.i(
                TAG,
                "Sending $reason package=${snapshot.packageName} title=${snapshot.title} " +
                    "state=${snapshot.playbackState} queue=${snapshot.queue?.size ?: 0} bytes=${payload.size}",
            )
        }
        if (outbound.trySend(payload).isSuccess) {
            if (includeArt) artIdOnCluster = artwork?.id
        } else {
            Log.w(TAG, "Dropping outbound media bridge frame; queue is full")
        }
    }

    override fun onDestroy() {
        BridgeRuntime.state.value = BridgeConnectionState.Stopped
        BridgeRuntime.snapshot.value = MediaSnapshot()
        outbound.close()
        mediaSessions.close()
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.media_bridge_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle(getString(R.string.media_bridge_notification_title))
        .setContentText(getString(R.string.media_bridge_notification_text))
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .setOngoing(true)
        .build()

    companion object {
        private const val TAG = "MediaBridgeService"
        private const val CHANNEL_ID = "media_bridge"
        private const val NOTIFICATION_ID = 4101
        private val RETRY_SECONDS = longArrayOf(1, 2, 4, 8, 16, 32, 60)

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, MediaBridgeForegroundService::class.java))
            } catch (error: ForegroundServiceStartNotAllowedException) {
                // Android 12+ may refuse a start from the background; the user can start it from the app.
                Log.w(TAG, "Foreground service start not allowed from background", error)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaBridgeForegroundService::class.java))
        }
    }
}
