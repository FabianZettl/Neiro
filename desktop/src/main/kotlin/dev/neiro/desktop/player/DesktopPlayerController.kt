package dev.neiro.desktop.player

import dev.neiro.desktop.data.api.models.SongDto
import dev.neiro.desktop.data.prefs.DesktopPreferences
import dev.neiro.desktop.data.prefs.NieroPrefs
import dev.neiro.desktop.data.repository.MusicRepository
import dev.neiro.desktop.data.api.SubsonicAuthInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

class DesktopPlayerController(
    private val preferences: DesktopPreferences,
    private val musicRepository: MusicRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _playerState = MutableStateFlow(PlayerState(isConnected = true))
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var currentQueue: List<SongDto> = emptyList()
    private var currentIndex: Int = 0

    @Volatile private var seekOffsetMs = 0L
    @Volatile private var autoDjRunning = false
    var autoDjEnabled: Boolean = true

    // vlcj player — lazy so we don't crash if VLC is not installed
    private val factory: MediaPlayerFactory? by lazy {
        try {
            MediaPlayerFactory()
        } catch (e: Exception) {
            System.err.println("vlcj: could not create MediaPlayerFactory — is VLC installed? ${e.message}")
            null
        }
    }

    private val mediaPlayer: MediaPlayer? by lazy {
        try {
            factory?.mediaPlayers()?.newMediaPlayer()?.also { player ->
                player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                    override fun finished(mediaPlayer: MediaPlayer) {
                        onTrackFinished()
                    }

                    override fun error(mediaPlayer: MediaPlayer) {
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false,
                            error = "Playback error"
                        )
                    }

                    override fun playing(mediaPlayer: MediaPlayer) {
                        _playerState.value = _playerState.value.copy(isPlaying = true, error = null)
                    }

                    override fun paused(mediaPlayer: MediaPlayer) {
                        _playerState.value = _playerState.value.copy(isPlaying = false)
                    }

                    override fun stopped(mediaPlayer: MediaPlayer) {
                        _playerState.value = _playerState.value.copy(isPlaying = false)
                    }
                })
            }
        } catch (e: Exception) {
            System.err.println("vlcj: could not create MediaPlayer: ${e.message}")
            null
        }
    }

    init {
        scope.launch {
            startPositionUpdater()
        }
    }

    private fun onTrackFinished() {
        val state = _playerState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                // Replay current track
                val song = state.currentSong ?: return
                scope.launch {
                    val prefs = preferences.prefsFlow.first()
                    val url = buildStreamUrl(song.id, prefs)
                    seekOffsetMs = 0L
                    mediaPlayer?.media()?.play(url)
                }
            }
            RepeatMode.ALL -> {
                val nextIndex = (currentIndex + 1) % currentQueue.size
                playAtIndex(nextIndex)
            }
            RepeatMode.OFF -> {
                if (currentIndex < currentQueue.size - 1) {
                    playAtIndex(currentIndex + 1)
                } else {
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                }
            }
        }

        // AutoDJ
        val remaining = currentQueue.size - currentIndex - 1
        val currentSong = state.currentSong
        if (autoDjEnabled && remaining <= 1 && currentSong != null) {
            triggerAutoDj(currentSong)
        }
    }

    private fun playAtIndex(index: Int) {
        val song = currentQueue.getOrNull(index) ?: return
        currentIndex = index
        seekOffsetMs = 0L
        scope.launch {
            val prefs = preferences.prefsFlow.first()
            val url = buildStreamUrl(song.id, prefs)
            _playerState.value = _playerState.value.copy(
                currentSong = song,
                queueIndex = index,
                positionMs = 0L,
                durationMs = song.duration * 1000L,
                isPlaying = true,
                error = null
            )
            mediaPlayer?.media()?.play(url)
        }
    }

    suspend fun playTrack(song: SongDto, queue: List<SongDto>, startIndex: Int = 0) {
        currentQueue = queue
        currentIndex = startIndex
        seekOffsetMs = 0L

        val prefs = preferences.prefsFlow.first()
        val url = buildStreamUrl(song.id, prefs)

        _playerState.value = _playerState.value.copy(
            currentSong = song,
            queue = queue,
            queueIndex = startIndex,
            isPlaying = true,
            error = null,
            durationMs = song.duration * 1000L,
            positionMs = 0L
        )

        mediaPlayer?.media()?.play(url)
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.status().isPlaying) {
            player.controls().pause()
        } else {
            player.controls().play()
        }
        _playerState.value = _playerState.value.copy(isPlaying = !_playerState.value.isPlaying)
    }

    fun skipNext() {
        val nextIndex = currentIndex + 1
        if (nextIndex < currentQueue.size) {
            playAtIndex(nextIndex)
        }
    }

    fun skipPrev() {
        val pos = _playerState.value.positionMs
        if (pos > 3000 || currentIndex == 0) {
            // Restart current track
            seekTo(0L)
        } else {
            playAtIndex(currentIndex - 1)
        }
    }

    fun seekTo(positionMs: Long) {
        seekOffsetMs = positionMs
        _playerState.value = _playerState.value.copy(positionMs = positionMs)
        val song = _playerState.value.currentSong ?: return
        val wasPlaying = _playerState.value.isPlaying

        scope.launch {
            val prefs = preferences.prefsFlow.first()
            val url = buildStreamUrl(song.id, prefs, timeOffsetSecs = (positionMs / 1000).toInt())
            mediaPlayer?.media()?.play(url)
            if (!wasPlaying) {
                mediaPlayer?.controls()?.pause()
            }
        }
    }

    fun seekToQueueItem(index: Int) {
        if (index in currentQueue.indices) {
            playAtIndex(index)
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_playerState.value.shuffleEnabled
        if (newShuffle) {
            // Shuffle queue, keep current at front
            val current = currentQueue.getOrNull(currentIndex)
            val rest = currentQueue.toMutableList()
            if (current != null) rest.remove(current)
            val shuffled = if (current != null) listOf(current) + rest.shuffled() else rest.shuffled()
            currentQueue = shuffled
            currentIndex = 0
            _playerState.value = _playerState.value.copy(
                queue = currentQueue,
                queueIndex = 0,
                shuffleEnabled = true
            )
        } else {
            _playerState.value = _playerState.value.copy(shuffleEnabled = false)
        }
    }

    fun cycleRepeat() {
        val next = when (_playerState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playerState.value = _playerState.value.copy(repeatMode = next)
    }

    fun toggleAutoDj() {
        autoDjEnabled = !autoDjEnabled
        _playerState.value = _playerState.value.copy(autoDjEnabled = autoDjEnabled)
    }

    fun setVolume(volume: Float) {
        val vol = (volume * 100).toInt().coerceIn(0, 200)
        mediaPlayer?.audio()?.setVolume(vol)
    }

    suspend fun playNext(song: SongDto) {
        val insertAt = (currentIndex + 1).coerceAtMost(currentQueue.size)
        val mutable = currentQueue.toMutableList()
        mutable.add(insertAt, song)
        currentQueue = mutable
        _playerState.value = _playerState.value.copy(queue = currentQueue)
    }

    suspend fun addToQueue(song: SongDto) {
        currentQueue = currentQueue + song
        _playerState.value = _playerState.value.copy(queue = currentQueue)
    }

    private fun triggerAutoDj(seedSong: SongDto) {
        if (autoDjRunning) return
        autoDjRunning = true
        scope.launch {
            try {
                val similar = musicRepository.getSimilarSongs(seedSong.id, count = 10)
                if (similar.isEmpty()) return@launch
                val prefs = preferences.prefsFlow.first()
                val currentIds = currentQueue.map { it.id }.toSet()
                val toAdd = similar.filter { it.id !in currentIds }.take(5).map { song ->
                    song.copy(coverArtUrl = song.coverArt?.let { musicRepository.buildCoverArtUrl(prefs, it) })
                }
                currentQueue = currentQueue + toAdd
                _playerState.value = _playerState.value.copy(queue = currentQueue)
            } catch (e: Exception) {
                // silently ignore AutoDJ errors
            } finally {
                autoDjRunning = false
            }
        }
    }

    private suspend fun startPositionUpdater() {
        while (true) {
            delay(500)
            val player = mediaPlayer ?: continue
            val rawPos = try {
                player.status().time().takeIf { it >= 0L } ?: continue
            } catch (e: Exception) { continue }

            val pos = rawPos + seekOffsetMs
            val dur = _playerState.value.currentSong?.duration?.let { it * 1000L }
                ?: try { player.status().length().takeIf { it > 0L } } catch (e: Exception) { null }
                ?: _playerState.value.durationMs

            _playerState.value = _playerState.value.copy(positionMs = pos, durationMs = dur)
        }
    }

    private fun buildStreamUrl(songId: String, prefs: NieroPrefs, timeOffsetSecs: Int = 0): String {
        val salt = SubsonicAuthInterceptor.generateSalt()
        val token = SubsonicAuthInterceptor.md5(prefs.password + salt)
        val base = prefs.serverUrl.trimEnd('/')
        val bitrateParam = if (prefs.streamingBitrate > 0) "&maxBitRate=${prefs.streamingBitrate}" else ""
        val offsetParam = if (timeOffsetSecs > 0) "&timeOffset=$timeOffsetSecs" else ""
        return "$base/rest/stream?id=$songId&u=${prefs.username}&t=$token&s=$salt&v=1.16.1&c=neiro&f=json$bitrateParam$offsetParam"
    }

    fun release() {
        try {
            mediaPlayer?.release()
            factory?.release()
        } catch (e: Exception) {
            // ignore
        }
    }
}
