package dev.neiro.app.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.AlbumDto
import dev.neiro.app.data.api.models.LastFmAlbumInfo
import dev.neiro.app.data.repository.LastFmRepository
import dev.neiro.app.data.repository.MusicRepository
import dev.neiro.app.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumUiState(
    val album: AlbumDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastFmInfo: LastFmAlbumInfo? = null,
    val lovedTrackKeys: Set<String> = emptySet()   // "trackname_artistname" lowercase
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val lastFmRepository: LastFmRepository,
    private val playerController: PlayerController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val albumId: String = checkNotNull(savedStateHandle["albumId"])

    private val _uiState = MutableStateFlow(AlbumUiState(isLoading = true))
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    init {
        loadAlbum()
    }

    private fun loadAlbum() {
        viewModelScope.launch {
            _uiState.value = AlbumUiState(isLoading = true)
            try {
                val album = musicRepository.getAlbum(albumId)
                _uiState.value = AlbumUiState(album = album, isLoading = false)
                // Load Last.fm data in background (non-blocking)
                val artistName = album.artist ?: return@launch
                val lastFmInfo = lastFmRepository.getAlbumInfo(artistName, album.name)
                val lovedKeys = lastFmRepository.getLovedTracks(200)
                _uiState.value = _uiState.value.copy(lastFmInfo = lastFmInfo, lovedTrackKeys = lovedKeys)
            } catch (e: Exception) {
                _uiState.value = AlbumUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun toggleStar() {
        val album = _uiState.value.album ?: return
        val isStarred = album.starred != null
        // Optimistic update
        _uiState.value = _uiState.value.copy(
            album = album.copy(starred = if (isStarred) null else "2024-01-01T00:00:00")
        )
        viewModelScope.launch {
            if (isStarred) {
                musicRepository.unstarAlbum(album.id)
            } else {
                musicRepository.starAlbum(album.id)
            }
        }
    }

    fun playTrackAtIndex(index: Int) {
        val songs = _uiState.value.album?.song ?: return
        if (songs.isEmpty()) return
        viewModelScope.launch {
            playerController.playTrack(songs[index], songs, index)
        }
    }

    fun shufflePlay() {
        val songs = _uiState.value.album?.song ?: return
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        viewModelScope.launch {
            playerController.playTrack(shuffled.first(), shuffled, 0)
        }
    }
}
