package dev.neiro.app.ui.starred

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.data.repository.LastFmRepository
import dev.neiro.app.data.repository.MusicRepository
import dev.neiro.app.player.PlayerController
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LovedTrackItem(
    val name: String,
    val artistName: String,
    val coverArtUrl: String?,
    val subsonicId: String?,
    val albumId: String?
)

data class StarredUiState(
    val tracks: List<LovedTrackItem> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val isLastFmConfigured: Boolean = false
)

@HiltViewModel
class StarredViewModel @Inject constructor(
    private val lastFmRepository: LastFmRepository,
    private val musicRepository: MusicRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(StarredUiState())
    val uiState: StateFlow<StarredUiState> = _uiState.asStateFlow()

    init {
        loadLovedTracks()
    }

    fun loadLovedTracks() {
        viewModelScope.launch {
            _uiState.value = StarredUiState(isLoading = true)
            val configured = lastFmRepository.isStatsConfigured()
            if (!configured) {
                _uiState.value = StarredUiState(isLoading = false, isLastFmConfigured = false)
                return@launch
            }
            try {
                val lovedTracks = lastFmRepository.getLovedTracksFull(500)
                if (lovedTracks.isEmpty()) {
                    _uiState.value = StarredUiState(isLoading = false, isLastFmConfigured = true, isEmpty = true)
                    return@launch
                }
                coroutineScope {
                    val items = lovedTracks.map { lfm ->
                        async {
                            val songs = runCatching {
                                musicRepository.searchSongs(lfm.name, lfm.artist.name)
                            }.getOrElse { emptyList() }
                            val best = songs.firstOrNull {
                                it.title.equals(lfm.name, ignoreCase = true)
                            } ?: songs.firstOrNull()
                            LovedTrackItem(
                                name = lfm.name,
                                artistName = lfm.artist.name,
                                coverArtUrl = best?.coverArtUrl ?: lfm.imageUrl,
                                subsonicId = best?.id,
                                albumId = best?.albumId
                            )
                        }
                    }.map { it.await() }
                    _uiState.value = StarredUiState(
                        tracks = items,
                        isLoading = false,
                        isLastFmConfigured = true,
                        isEmpty = items.isEmpty()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = StarredUiState(isLoading = false, isLastFmConfigured = true)
            }
        }
    }

    fun play(track: LovedTrackItem) {
        val songId = track.subsonicId ?: return
        viewModelScope.launch {
            val albumId = track.albumId
            if (albumId != null) {
                val songs = runCatching { musicRepository.getAlbum(albumId).song }.getOrElse { emptyList() }
                val index = songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
                if (songs.isNotEmpty()) {
                    playerController.playTrack(songs[index], songs, index)
                    return@launch
                }
            }
            val song = SongDto(id = songId, title = track.name, artist = track.artistName,
                coverArtUrl = track.coverArtUrl, duration = 0)
            playerController.playTrack(song, listOf(song), 0)
        }
    }
}
