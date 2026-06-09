package dev.neiro.app.ui.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.PlaylistDto
import dev.neiro.app.data.repository.MusicRepository
import dev.neiro.app.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlist: PlaylistDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerController: PlayerController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(PlaylistDetailUiState(isLoading = true))
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PlaylistDetailUiState(isLoading = true)
            try {
                val playlist = musicRepository.getPlaylist(playlistId)
                _uiState.value = PlaylistDetailUiState(playlist = playlist, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = PlaylistDetailUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun playAll(startIndex: Int = 0) {
        val songs = _uiState.value.playlist?.entry ?: return
        if (songs.isEmpty()) return
        viewModelScope.launch {
            playerController.playTrack(songs[startIndex], songs, startIndex)
        }
    }

    fun shufflePlay() {
        val songs = _uiState.value.playlist?.entry ?: return
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        viewModelScope.launch {
            playerController.playTrack(shuffled.first(), shuffled, 0)
        }
    }
}
