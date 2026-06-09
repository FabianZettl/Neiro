package dev.neiro.app.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.neiro.app.data.api.SubsonicAuthInterceptor
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.data.prefs.NieroPrefs
import dev.neiro.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: NieroPreferences,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _controller = MutableStateFlow<MediaController?>(null)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var currentQueue: List<SongDto> = emptyList()

    // Prevents multiple simultaneous connection attempts
    @Volatile private var isConnecting = false

    init {
        scope.launch(Dispatchers.Main) {
            connectToService()
            startPositionUpdater()
        }
    }

    private fun connectToService() {
        if (_controller.value != null || isConnecting) return
        isConnecting = true

        val sessionToken = SessionToken(
            context,
            ComponentName(context, NieroMediaService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                isConnecting = false
                try {
                    val controller = future.get()
                    Log.d("NieroPlayer", "MediaController connected")
                    _controller.value = controller
                    _playerState.value = _playerState.value.copy(isConnected = true)
                    attachPlayerListener(controller)
                } catch (e: Exception) {
                    Log.e("NieroPlayer", "MediaController connection failed", e)
                    _playerState.value = _playerState.value.copy(
                        error = "Media service connection failed: ${e.message}"
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = controller.currentMediaItemIndex
                val song = currentQueue.getOrNull(index)
                _playerState.value = _playerState.value.copy(
                    currentSong = song,
                    queueIndex = index,
                    positionMs = 0L,
                    durationMs = song?.duration?.times(1000L) ?: 0L
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d("NieroPlayer", "Playback state: $stateName")
                val duration = controller.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                _playerState.value = _playerState.value.copy(durationMs = duration)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("NieroPlayer", "Playback error code=${error.errorCode} msg=${error.message}", error)
                Log.e("NieroPlayer", "Cause: ${error.cause}")
                _playerState.value = _playerState.value.copy(
                    isPlaying = false,
                    error = "Fehler ${error.errorCode}: ${error.message}"
                )
            }
        })
    }

    private suspend fun startPositionUpdater() {
        while (true) {
            delay(500)
            _controller.value?.let { controller ->
                val pos = controller.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L
                // Prefer ExoPlayer duration; fall back to API metadata (duration field in seconds)
                val exoDur = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                val metaDur = _playerState.value.currentSong?.duration?.let { it * 1000L }?.takeIf { it > 0L }
                val dur = exoDur ?: metaDur ?: _playerState.value.durationMs
                _playerState.value = _playerState.value.copy(positionMs = pos, durationMs = dur)
            }
        }
    }

    /** Awaits a ready MediaController, reconnecting if needed (10s timeout). */
    private suspend fun awaitController(): MediaController? {
        _controller.value?.let { return it }
        // Schedule reconnect on Main and wait
        withContext(Dispatchers.Main) { connectToService() }
        return withTimeoutOrNull(10_000L) {
            _controller.first { it != null }
        }
    }

    suspend fun playTrack(song: SongDto, queue: List<SongDto>, startIndex: Int = 0) {
        val prefs = preferences.prefsFlow.first()
        val controller = awaitController() ?: run {
            _playerState.value = _playerState.value.copy(
                error = "Could not connect to media service"
            )
            return
        }

        currentQueue = queue
        val mediaItems = queue.map { buildMediaItem(it, prefs) }

        // MediaController is thread-safe in Media3 — no withContext needed
        controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        controller.prepare()
        controller.play()

        _playerState.value = _playerState.value.copy(
            currentSong = song,
            queue = queue,
            queueIndex = startIndex,
            isPlaying = true,
            error = null,
            // Use API duration immediately — HTTP streams often have no Content-Length
            durationMs = song.duration * 1000L,
            positionMs = 0L
        )
    }

    fun togglePlayPause() {
        val controller = _controller.value ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun skipNext() {
        _controller.value?.seekToNextMediaItem()
    }

    fun skipPrev() {
        _controller.value?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        val controller = _controller.value ?: return
        if (!controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            Log.w("NieroPlayer", "seekTo($positionMs) ignored — SEEK command not available (stream not seekable?)")
            return
        }
        controller.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(positionMs = positionMs)
    }

    /** Inserts a song immediately after the currently playing track. */
    suspend fun playNext(song: SongDto) {
        val prefs = preferences.prefsFlow.first()
        val controller = awaitController() ?: return
        val insertAt = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertAt, buildMediaItem(song, prefs))
        val mutable = currentQueue.toMutableList()
        mutable.add(insertAt, song)
        currentQueue = mutable
        _playerState.value = _playerState.value.copy(queue = currentQueue)
    }

    /** Appends a song at the end of the current queue. */
    suspend fun addToQueue(song: SongDto) {
        val prefs = preferences.prefsFlow.first()
        val controller = awaitController() ?: return
        controller.addMediaItem(buildMediaItem(song, prefs))
        currentQueue = currentQueue + song
        _playerState.value = _playerState.value.copy(queue = currentQueue)
    }

    private fun buildMediaItem(song: SongDto, prefs: NieroPrefs): MediaItem {
        val salt = SubsonicAuthInterceptor.generateSalt()
        val token = SubsonicAuthInterceptor.md5(prefs.password + salt)
        val base = prefs.serverUrl.trimEnd('/')
        val bitrateParam = if (prefs.streamingBitrate > 0) "&maxBitRate=${prefs.streamingBitrate}" else ""
        val streamUrl =
            "$base/rest/stream?id=${song.id}&u=${prefs.username}&t=$token&s=$salt&v=1.16.1&c=neiro&f=json$bitrateParam"

        Log.d("NieroPlayer", "Stream URL (masked): $base/rest/stream?id=${song.id}&u=${prefs.username}&t=***&s=***&v=1.16.1&c=neiro$bitrateParam")

        val builder = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.coverArtUrl?.let { android.net.Uri.parse(it) })
                    .build()
            )

        // Provide the known duration so ExoPlayer can seek even on transcoded streams
        // where the HTTP response has no Content-Length (on-the-fly encoding).
        // Without this, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM is unavailable and every
        // seekTo() call is silently dropped.
        if (song.duration > 0) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(song.duration * 1000L)
                    .build()
            )
        }

        return builder.build()
    }
}
