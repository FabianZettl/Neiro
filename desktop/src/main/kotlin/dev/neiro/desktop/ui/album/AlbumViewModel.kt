package dev.neiro.desktop.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neiro.desktop.data.api.models.AlbumDto
import dev.neiro.desktop.data.api.models.LastFmAlbumInfo
import dev.neiro.desktop.data.repository.LastFmRepository
import dev.neiro.desktop.data.repository.MusicRepository
import dev.neiro.desktop.player.DesktopPlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumUiState(
    val album: AlbumDto? = null,
    val isLoading: Boolean = true,
    val lastFmInfo: LastFmAlbumInfo? = null,
    val lovedTrackKeys: Set<String> = emptySet()
)

class AlbumViewModel(
    private val musicRepository: MusicRepository,
    private val lastFmRepository: LastFmRepository,
    private val playerController: DesktopPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    fun loadAlbum(albumId: String) {
        viewModelScope.launch {
            _uiState.value = AlbumUiState(isLoading = true)
            try {
                val album = musicRepository.getAlbum(albumId)
                _uiState.value = AlbumUiState(album = album, isLoading = false)

                // Load LastFM info concurrently
                launch {
                    val artistName = album.artist ?: return@launch
                    val info = lastFmRepository.getAlbumInfo(artistName, album.name)
                    _uiState.value = _uiState.value.copy(lastFmInfo = info)
                }
                launch {
                    val loved = lastFmRepository.getLovedTracks()
                    _uiState.value = _uiState.value.copy(lovedTrackKeys = loved)
                }
            } catch (e: Exception) {
                _uiState.value = AlbumUiState(isLoading = false)
            }
        }
    }

    fun playTrackAtIndex(index: Int) {
        val songs = _uiState.value.album?.song ?: return
        val song = songs.getOrNull(index) ?: return
        viewModelScope.launch {
            playerController.playTrack(song, songs, index)
        }
    }

    fun shufflePlay() {
        val songs = _uiState.value.album?.song ?: return
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        viewModelScope.launch {
            playerController.playTrack(shuffled[0], shuffled, 0)
        }
    }

    fun toggleStar() {
        val album = _uiState.value.album ?: return
        viewModelScope.launch {
            if (album.starred != null) {
                musicRepository.unstarAlbum(album.id)
                _uiState.value = _uiState.value.copy(
                    album = album.copy(starred = null)
                )
            } else {
                musicRepository.starAlbum(album.id)
                _uiState.value = _uiState.value.copy(
                    album = album.copy(starred = "true")
                )
            }
        }
    }
}
