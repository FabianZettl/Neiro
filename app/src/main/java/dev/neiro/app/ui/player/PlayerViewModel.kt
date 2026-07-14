package dev.neiro.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.LastFmTrackInfo
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.data.api.models.StructuredLyrics
import dev.neiro.app.data.repository.ConnectRepository
import dev.neiro.app.data.repository.DesktopState
import dev.neiro.app.data.repository.LastFmRepository
import dev.neiro.app.data.repository.MusicRepository
import dev.neiro.app.player.PlayerController
import dev.neiro.app.player.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LastFmTrackState(
    val info: LastFmTrackInfo? = null,
    val isLoved: Boolean = false,
    val isLoading: Boolean = false,
    val hasSession: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val lastFmRepository: LastFmRepository,
    private val musicRepository: MusicRepository,
    private val connectRepository: ConnectRepository
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.playerState
    val desktopState: StateFlow<DesktopState> = connectRepository.state
    val isDesktopConnectionPaused: StateFlow<Boolean> = connectRepository.manuallyPaused
    val isRemoteMode: StateFlow<Boolean> = connectRepository.remoteMode

    /**
     * Cover art URL for the song currently playing on desktop (for remote mode NowPlaying).
     * desktopState ticks roughly once a second while playing (position updates) — the
     * buildCoverArtUrl call embeds a fresh random auth token each time, so without this
     * distinctUntilChanged Coil would never get a cache hit and re-download the same image.
     */
    val desktopCoverArtUrl: StateFlow<String?> = desktopState
        .map { (it as? DesktopState.Playing)?.song?.coverArtId }
        .distinctUntilChanged()
        .map { id -> id?.let { musicRepository.getCoverArtUrl(it, size = 800) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _lastFmState = MutableStateFlow(LastFmTrackState())
    val lastFmState: StateFlow<LastFmTrackState> = _lastFmState.asStateFlow()

    private val _lyrics = MutableStateFlow<StructuredLyrics?>(null)
    val lyrics: StateFlow<StructuredLyrics?> = _lyrics.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    fun toggleLyrics() { _showLyrics.value = !_showLyrics.value }

    private var lastFmJob: Job? = null
    private var lastLoadedSongId: String? = null

    init {
        viewModelScope.launch {
            playerState.collect { state ->
                val song = state.currentSong
                if (song?.id != lastLoadedSongId) {
                    lastLoadedSongId = song?.id
                    if (song != null) loadLastFmTrackInfo(song)
                    else _lastFmState.value = LastFmTrackState()
                }
            }
        }
        viewModelScope.launch {
            playerController.playerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collect { songId ->
                    _lyrics.value = null
                    _showLyrics.value = false
                    if (songId != null) {
                        _lyrics.value = musicRepository.getLyrics(songId)
                    }
                }
        }
    }

    private fun loadLastFmTrackInfo(song: SongDto) {
        lastFmJob?.cancel()
        lastFmJob = viewModelScope.launch {
            val hasSession = lastFmRepository.isSessionConfigured()
            _lastFmState.value = LastFmTrackState(isLoading = true, hasSession = hasSession)
            val artistName = song.artist ?: return@launch
            val info = lastFmRepository.getTrackInfo(song.title, artistName)
            _lastFmState.value = LastFmTrackState(
                info = info,
                isLoved = info?.isLoved ?: false,
                isLoading = false,
                hasSession = hasSession
            )
        }
    }

    fun toggleLove() {
        val song = playerState.value.currentSong ?: return
        val artistName = song.artist ?: return
        val currentLoved = _lastFmState.value.isLoved
        // Optimistic update
        _lastFmState.value = _lastFmState.value.copy(isLoved = !currentLoved)
        viewModelScope.launch {
            val ok = lastFmRepository.loveTrack(song.title, artistName, !currentLoved)
            if (ok) {
                lastFmRepository.invalidateLovedTracks()
            } else {
                // Revert on failure
                _lastFmState.value = _lastFmState.value.copy(isLoved = currentLoved)
            }
        }
    }

    fun togglePlayPause() {
        if (connectRepository.remoteMode.value) {
            val playing = (desktopState.value as? DesktopState.Playing)?.song?.isPlaying ?: false
            sendCommandToDesktop(if (playing) "pause" else "play")
        } else {
            playerController.togglePlayPause()
        }
    }

    fun skipNext() {
        if (connectRepository.remoteMode.value) sendCommandToDesktop("next")
        else playerController.skipNext()
    }

    fun skipPrev() {
        if (connectRepository.remoteMode.value) sendCommandToDesktop("prev")
        else playerController.skipPrev()
    }

    fun seekTo(positionMs: Long) {
        if (connectRepository.remoteMode.value) sendCommandToDesktop("seek", positionMs)
        else playerController.seekTo(positionMs)
    }

    fun seekToQueueItem(index: Int) = playerController.seekToQueueItem(index)
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeat() = playerController.cycleRepeat()
    fun toggleAutoDj() = playerController.toggleAutoDj()
    fun playNext(song: SongDto) = viewModelScope.launch { playerController.playNext(song) }
    fun addToQueue(song: SongDto) = viewModelScope.launch { playerController.addToQueue(song) }
    fun setSleepTimer(durationMs: Long) = playerController.setSleepTimer(durationMs)
    fun cancelSleepTimer() = playerController.cancelSleepTimer()

    // ── Neiro Connect — desktop remote control ────────────────────────────────

    fun pauseDesktopConnection() = connectRepository.pauseConnection()
    fun resumeDesktopConnection() = connectRepository.resumeConnection()

    /** Enter remote mode: cast current local queue to desktop and pause local playback. */
    fun enterRemoteMode() {
        val state = playerState.value
        if (state.queue.isNotEmpty()) {
            connectRepository.castSongs(state.queue.map { it.id }, state.queueIndex, state.positionMs)
        }
        connectRepository.enterRemoteMode()
        playerController.pause()
    }

    /** Exit remote mode: stop controlling desktop, resume local playback. */
    fun exitRemoteMode() {
        connectRepository.exitRemoteMode()
        sendCommandToDesktop("pause")
        // Resume local Android playback
        if (playerState.value.currentSong != null) {
            playerController.play()
        }
    }

    fun sendCommandToDesktop(action: String, value: Long = 0L) =
        connectRepository.sendCommand(action, value)

    /** Cast the current local queue to the desktop. */
    fun castQueueToDesktop() {
        val state = playerState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        connectRepository.castSongs(
            songIds      = queue.map { it.id },
            startIndex   = state.queueIndex,
            startPositionMs = state.positionMs
        )
    }

    /** Transfer desktop playback to this device. */
    fun transferFromDesktop() {
        val ds = desktopState.value
        if (ds !is DesktopState.Playing) return
        val songId = ds.song.songId
        val posMs  = ds.song.positionMs
        connectRepository.sendCommand("pause")
        viewModelScope.launch {
            val song = musicRepository.getSong(songId) ?: return@launch
            delay(300) // give desktop time to pause
            playerController.playTrack(song, listOf(song), 0)
            if (posMs > 0) {
                delay(500)
                playerController.seekTo(posMs)
            }
        }
    }
}
