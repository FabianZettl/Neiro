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
import dev.neiro.app.data.api.models.InternetRadioStationDto
import dev.neiro.app.data.api.models.PodcastEpisode
import dev.neiro.app.data.api.models.PodcastSubscription
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.data.prefs.NieroPrefs
import dev.neiro.app.data.repository.ConnectRepository
import dev.neiro.app.data.repository.MusicRepository
import dev.neiro.app.di.ApplicationScope
import dev.neiro.app.widget.updateNieroWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val musicRepository: MusicRepository,
    private val connectRepository: ConnectRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _controller = MutableStateFlow<MediaController?>(null)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var currentQueue: List<SongDto> = emptyList()

    // Prevents multiple simultaneous connection attempts
    @Volatile private var isConnecting = false

    // Tracks the server-side time offset applied to the current stream URL.
    // When seekTo(T) is called we rebuild the stream URL with &timeOffset=T/1000
    // so the server starts sending audio from T. ExoPlayer then reports position
    // starting from 0; we add this offset so the UI shows the correct absolute time.
    @Volatile private var seekOffsetMs = 0L

    // True while we're replacing the current MediaItem due to a seek.
    // Prevents onMediaItemTransition from resetting position/offset on that event.
    @Volatile private var seekInProgress = false

    // AutoDJ — prevent concurrent fetch jobs
    @Volatile private var autoDjRunning = false

    private var sleepTimerJob: Job? = null

    var autoDjEnabled: Boolean = true

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
        // Sync initial shuffle/repeat state from player
        _playerState.value = _playerState.value.copy(
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
        )
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                val song = _playerState.value.currentSong ?: return
                scope.launch {
                    try { updateNieroWidget(context, song.title, song.artist, isPlaying, song.coverArtUrl) }
                    catch (e: Exception) { Log.w("NieroPlayer", "Widget update failed: ${e.message}") }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playerState.value = _playerState.value.copy(shuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _playerState.value = _playerState.value.copy(
                    repeatMode = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        else -> RepeatMode.OFF
                    }
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (seekInProgress) {
                    seekInProgress = false
                    return
                }
                seekOffsetMs = 0L
                val index = controller.currentMediaItemIndex
                val song = currentQueue.getOrNull(index)
                _playerState.value = _playerState.value.copy(
                    currentSong = song,
                    queueIndex = index,
                    positionMs = 0L,
                    durationMs = song?.duration?.times(1000L) ?: 0L
                )
                // AutoDJ: when only 1 song remains in the queue, fetch similar songs
                val remaining = controller.mediaItemCount - index - 1
                if (autoDjEnabled && remaining <= 1 && song != null) {
                    triggerAutoDj(song)
                }
                // Update home screen widget
                if (song != null) {
                    scope.launch {
                        try { updateNieroWidget(context, song.title, song.artist, controller.isPlaying, song.coverArtUrl) }
                        catch (e: Exception) { Log.w("NieroPlayer", "Widget update failed: ${e.message}") }
                    }
                }
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
                // After a timeOffset seek, ExoPlayer reports the REMAINING duration,
                // not the full track duration. Always prefer API metadata.
                val apiDur = _playerState.value.currentSong?.duration?.let { it * 1000L }
                    ?.takeIf { it > 0L }
                val exoDur = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                _playerState.value = _playerState.value.copy(durationMs = apiDur ?: exoDur ?: 0L)
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
                // Use ExoPlayer directly when available (same process — more accurate).
                val rawPos = NieroPlayerHolder.player?.currentPosition?.takeIf { it != C.TIME_UNSET }
                    ?: controller.currentPosition.takeIf { it != C.TIME_UNSET }
                    ?: 0L
                // Add server-side seek offset so position reflects absolute track time.
                val pos = rawPos + seekOffsetMs
                // Always prefer API duration; after a timeOffset seek ExoPlayer reports
                // the remaining duration (totalDuration - offset), which is misleading.
                val metaDur = _playerState.value.currentSong?.duration?.let { it * 1000L }?.takeIf { it > 0L }
                val exoDur = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                val dur = metaDur ?: exoDur ?: _playerState.value.durationMs
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
        // In remote mode, cast to desktop instead of playing locally
        if (connectRepository.remoteMode.value) {
            connectRepository.castSongs(queue.map { it.id }, startIndex)
            return
        }

        val prefs = preferences.prefsFlow.first()
        currentQueue = queue
        val mediaItems = queue.map { buildMediaItem(it, prefs) }

        val controller = awaitController() ?: run {
            _playerState.value = _playerState.value.copy(
                error = "Could not connect to media service"
            )
            return
        }

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
            positionMs = 0L,
            isLiveStream = false
        )
    }

    fun togglePlayPause() {
        val controller = _controller.value ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun pause() { _controller.value?.let { if (it.isPlaying) it.pause() } }
    fun play()  { _controller.value?.let { if (!it.isPlaying) it.play() } }

    fun skipNext() {
        _controller.value?.seekToNextMediaItem()
    }

    fun skipPrev() {
        _controller.value?.seekToPreviousMediaItem()
    }

    fun seekToQueueItem(index: Int) {
        val controller = _controller.value ?: return
        seekOffsetMs = 0L
        controller.seekTo(index, 0L)
    }

    fun toggleShuffle() {
        val controller = _controller.value ?: return
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle
        _playerState.value = _playerState.value.copy(shuffleEnabled = newShuffle)
    }

    fun cycleRepeat() {
        val controller = _controller.value ?: return
        val next = when (_playerState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        controller.repeatMode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        _playerState.value = _playerState.value.copy(repeatMode = next)
    }

    fun seekTo(positionMs: Long) {
        // Seeking transcoded HTTP streams by byte range doesn't work because the server
        // doesn't send Content-Length. Instead, rebuild the stream URL with the Subsonic
        // `timeOffset` parameter so the server starts sending audio from the desired second.
        // ExoPlayer then plays from position 0 of the new stream; we add seekOffsetMs to
        // the reported position in startPositionUpdater so the UI shows the correct time.
        seekOffsetMs = positionMs
        _playerState.value = _playerState.value.copy(positionMs = positionMs)

        val exo = NieroPlayerHolder.player ?: run {
            // No ExoPlayer available — nothing we can do
            return
        }
        val song = _playerState.value.currentSong ?: return
        val index = _playerState.value.queueIndex
        val wasPlaying = _playerState.value.isPlaying

        scope.launch(Dispatchers.Main) {
            val prefs = preferences.prefsFlow.first()
            val newItem = buildMediaItem(song, prefs, timeOffsetSecs = (positionMs / 1000).toInt())
            seekInProgress = true
            exo.replaceMediaItem(index, newItem)
            // replaceMediaItem resets ExoPlayer's position to 0 of the new item — exactly
            // what we want since the server now starts from timeOffset.
            if (wasPlaying) exo.play()
        }
    }

    fun toggleAutoDj() {
        autoDjEnabled = !autoDjEnabled
        _playerState.value = _playerState.value.copy(autoDjEnabled = autoDjEnabled)
    }

    private fun triggerAutoDj(seedSong: SongDto) {
        if (autoDjRunning) return
        autoDjRunning = true
        scope.launch(Dispatchers.Main) {
            try {
                val similar = musicRepository.getSimilarSongs(seedSong.id, count = 10)
                if (similar.isEmpty()) return@launch
                val prefs = preferences.prefsFlow.first()
                val controller = _controller.value ?: return@launch
                // Filter out songs already in the queue to avoid duplicates
                val currentIds = currentQueue.map { it.id }.toSet()
                val toAdd = similar.filter { it.id !in currentIds }.take(5)
                toAdd.forEach { song ->
                    controller.addMediaItem(buildMediaItem(song, prefs))
                }
                currentQueue = currentQueue + toAdd
                _playerState.value = _playerState.value.copy(queue = currentQueue)
                Log.d("NieroPlayer", "AutoDJ added ${toAdd.size} similar songs")
            } catch (e: Exception) {
                Log.e("NieroPlayer", "AutoDJ failed", e)
            } finally {
                autoDjRunning = false
            }
        }
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

    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        val endMs = System.currentTimeMillis() + durationMs
        _playerState.value = _playerState.value.copy(sleepTimerEndMs = endMs)
        sleepTimerJob = scope.launch {
            delay(durationMs)
            val controller = _controller.value
            if (controller?.isPlaying == true) controller.pause()
            _playerState.value = _playerState.value.copy(sleepTimerEndMs = null)
            sleepTimerJob = null
        }
    }

    suspend fun playRadio(station: InternetRadioStationDto) {
        val controller = awaitController() ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaId("radio_${station.id}")
            .setMimeType("audio/mpeg")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist("Internet Radio")
                    .build()
            )
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        val pseudoSong = SongDto(
            id = "radio_${station.id}",
            title = station.name,
            artist = "Internet Radio",
            duration = 0
        )
        currentQueue = listOf(pseudoSong)
        _playerState.value = _playerState.value.copy(
            currentSong = pseudoSong,
            queue = listOf(pseudoSong),
            queueIndex = 0,
            isPlaying = true,
            error = null,
            durationMs = 0L,
            positionMs = 0L,
            isLiveStream = true
        )
    }

    suspend fun playPodcastEpisode(episode: PodcastEpisode, subscription: PodcastSubscription) {
        val controller = awaitController() ?: return
        val artUrl = episode.imageUrl ?: subscription.imageUrl
        val mediaItem = MediaItem.Builder()
            .setUri(episode.audioUrl)
            .setMediaId("podcast_${episode.guid.hashCode()}")
            .setMimeType(episode.mimeType ?: "audio/mpeg")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(subscription.title)
                    .setAlbumTitle(subscription.title)
                    .setArtworkUri(artUrl?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        val pseudoSong = SongDto(
            id           = "podcast_${episode.guid.hashCode()}",
            title        = episode.title,
            artist       = subscription.title,
            duration     = episode.durationSeconds?.toInt() ?: 0,
            coverArtUrl  = artUrl
        )
        currentQueue = listOf(pseudoSong)
        _playerState.value = _playerState.value.copy(
            currentSong  = pseudoSong,
            queue        = listOf(pseudoSong),
            queueIndex   = 0,
            isPlaying    = true,
            error        = null,
            durationMs   = (episode.durationSeconds ?: 0L) * 1000L,
            positionMs   = 0L,
            isLiveStream = false
        )
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _playerState.value = _playerState.value.copy(sleepTimerEndMs = null)
    }

    private fun buildMediaItem(song: SongDto, prefs: NieroPrefs, timeOffsetSecs: Int = 0): MediaItem {
        val salt = SubsonicAuthInterceptor.generateSalt()
        val token = SubsonicAuthInterceptor.md5(prefs.password + salt)
        val base = prefs.serverUrl.trimEnd('/')
        val bitrateParam = if (prefs.streamingBitrate > 0) "&maxBitRate=${prefs.streamingBitrate}" else ""
        val offsetParam = if (timeOffsetSecs > 0) "&timeOffset=$timeOffsetSecs" else ""
        val streamUrl =
            "$base/rest/stream?id=${song.id}&u=${prefs.username}&t=$token&s=$salt&v=1.16.1&c=neiro&f=json$bitrateParam$offsetParam"

        Log.d("NieroPlayer", "Stream URL (masked): $base/rest/stream?id=${song.id}&u=${prefs.username}&t=***&s=***&v=1.16.1&c=neiro$bitrateParam${if (timeOffsetSecs > 0) "&timeOffset=$timeOffsetSecs" else ""}")

        // CastPlayer requires mimeType; derive from Subsonic contentType or suffix, default audio/mpeg
        val mimeType = song.contentType
            ?: when (song.suffix?.lowercase()) {
                "flac"          -> "audio/flac"
                "ogg", "oga"    -> "audio/ogg"
                "opus"          -> "audio/opus"
                "aac", "m4a"    -> "audio/aac"
                "wav"           -> "audio/wav"
                "wma"           -> "audio/x-ms-wma"
                else            -> "audio/mpeg"
            }

        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(song.id)
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.coverArtUrl?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
    }
}
