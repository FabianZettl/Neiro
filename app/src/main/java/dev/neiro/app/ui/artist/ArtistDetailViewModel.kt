package dev.neiro.app.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.ArtistInfoDto
import dev.neiro.app.data.api.models.ArtistWithAlbumsDto
import dev.neiro.app.data.api.models.LastFmArtistInfo
import dev.neiro.app.data.repository.LastFmRepository
import dev.neiro.app.data.repository.MusicRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDetailUiState(
    val artist: ArtistWithAlbumsDto? = null,
    val artistInfo: ArtistInfoDto = ArtistInfoDto(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastFmInfo: LastFmArtistInfo? = null
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val lastFmRepository: LastFmRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _uiState = MutableStateFlow(ArtistDetailUiState(isLoading = true))
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ArtistDetailUiState(isLoading = true)
            val artistDeferred = async { runCatching { musicRepository.getArtist(artistId) } }
            val artistInfoDeferred = async { musicRepository.getArtistInfo(artistId) }
            val artistResult = artistDeferred.await()
            val artistInfo = artistInfoDeferred.await()
            artistResult
                .onSuccess { artist ->
                    _uiState.value = ArtistDetailUiState(artist = artist, artistInfo = artistInfo)
                    // Load Last.fm info in background (non-blocking)
                    val lastFmInfo = lastFmRepository.getArtistInfo(artist.name)
                    _uiState.value = _uiState.value.copy(lastFmInfo = lastFmInfo)
                }
                .onFailure { _uiState.value = ArtistDetailUiState(error = it.message ?: it.javaClass.simpleName) }
        }
    }
}
