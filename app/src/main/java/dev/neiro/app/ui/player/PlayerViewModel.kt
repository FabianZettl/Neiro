package dev.neiro.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.LastFmTrackInfo
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.data.repository.LastFmRepository
import dev.neiro.app.player.PlayerController
import dev.neiro.app.player.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val lastFmRepository: LastFmRepository
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.playerState

    private val _lastFmState = MutableStateFlow(LastFmTrackState())
    val lastFmState: StateFlow<LastFmTrackState> = _lastFmState.asStateFlow()

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
            if (!ok) {
                // Revert on failure
                _lastFmState.value = _lastFmState.value.copy(isLoved = currentLoved)
            }
        }
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun skipNext() = playerController.skipNext()
    fun skipPrev() = playerController.skipPrev()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun seekToQueueItem(index: Int) = playerController.seekToQueueItem(index)
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeat() = playerController.cycleRepeat()
    fun toggleAutoDj() = playerController.toggleAutoDj()
    fun playNext(song: SongDto) = viewModelScope.launch { playerController.playNext(song) }
    fun addToQueue(song: SongDto) = viewModelScope.launch { playerController.addToQueue(song) }
}
