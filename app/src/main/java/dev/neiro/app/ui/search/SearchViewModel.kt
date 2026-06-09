package dev.neiro.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.SearchResult3Dto
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.data.repository.MusicRepository
import dev.neiro.app.player.PlayerController
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playerController: PlayerController
) : ViewModel() {

    var results: SearchResult3Dto? by mutableStateOf(null)
        private set

    var isLoading: Boolean by mutableStateOf(false)
        private set

    fun search(query: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                results = repository.search(query)
            } catch (e: Exception) {
                results = null
            } finally {
                isLoading = false
            }
        }
    }

    fun clearResults() {
        results = null
    }

    fun playSong(song: SongDto, queue: List<SongDto> = listOf(song)) {
        viewModelScope.launch {
            playerController.playTrack(song, queue, queue.indexOf(song).coerceAtLeast(0))
        }
    }
}
