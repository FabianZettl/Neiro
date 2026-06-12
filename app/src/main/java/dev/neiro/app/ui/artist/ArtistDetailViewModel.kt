package dev.neiro.app.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.ArtistInfoDto
import dev.neiro.app.data.api.models.ArtistWithAlbumsDto
import dev.neiro.app.data.api.models.LastFmArtistInfo
import dev.neiro.app.data.api.models.LastFmTopTrack
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.data.repository.LastFmRepository
import dev.neiro.app.data.repository.MusicRepository
import dev.neiro.app.player.PlayerController
import dev.neiro.app.player.playQueue
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
    val lastFmInfo: LastFmArtistInfo? = null,
    val topTracks: List<LastFmTopTrack> = emptyList()
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val lastFmRepository: LastFmRepository,
    private val playerController: PlayerController,
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
                    // Load Last.fm info + top tracks in background (non-blocking)
                    val lastFmInfoDeferred = async { lastFmRepository.getArtistInfo(artist.name) }
                    val topTracksDeferred  = async { lastFmRepository.getTopTracksForArtist(artist.name) }
                    _uiState.value = _uiState.value.copy(
                        lastFmInfo = lastFmInfoDeferred.await(),
                        topTracks  = topTracksDeferred.await()
                    )
                }
                .onFailure { _uiState.value = ArtistDetailUiState(error = it.message ?: it.javaClass.simpleName) }
        }
    }

    fun playTopTrack(track: LastFmTopTrack) {
        val artistName = _uiState.value.artist?.name ?: return
        viewModelScope.launch {
            val songs = runCatching { musicRepository.searchSongs(track.name, artistName) }.getOrElse { emptyList() }
            val best = songs.firstOrNull { it.title.equals(track.name, ignoreCase = true) } ?: songs.firstOrNull()
            if (best != null) {
                // Load the full album as queue if available, else play just this track
                val queue = if (best.albumId != null)
                    runCatching { musicRepository.getAlbum(best.albumId).song }.getOrElse { listOf(best) }
                else listOf(best)
                playerController.playQueue(queue, queue.indexOfFirst { it.id == best.id }.coerceAtLeast(0))
            }
        }
    }
}
