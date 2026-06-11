package dev.neiro.desktop.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neiro.desktop.data.api.models.AlbumDto
import dev.neiro.desktop.data.api.models.ArtistWithAlbumsDto
import dev.neiro.desktop.data.api.models.ArtistInfoDto
import dev.neiro.desktop.data.repository.LastFmRepository
import dev.neiro.desktop.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArtistUiState(
    val artist: ArtistWithAlbumsDto? = null,
    val artistInfo: ArtistInfoDto? = null,
    val isLoading: Boolean = true
)

class ArtistViewModel(
    private val artistId: String,
    private val musicRepository: MusicRepository,
    private val lastFmRepository: LastFmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    init {
        loadArtist()
    }

    private fun loadArtist() {
        viewModelScope.launch {
            _uiState.value = ArtistUiState(isLoading = true)
            try {
                val artist = musicRepository.getArtist(artistId)
                _uiState.value = ArtistUiState(artist = artist, isLoading = false)

                launch {
                    val info = musicRepository.getArtistInfo(artistId)
                    _uiState.value = _uiState.value.copy(artistInfo = info)
                }
            } catch (e: Exception) {
                _uiState.value = ArtistUiState(isLoading = false)
            }
        }
    }
}
