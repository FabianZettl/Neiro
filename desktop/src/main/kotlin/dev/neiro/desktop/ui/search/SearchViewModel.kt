package dev.neiro.desktop.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neiro.desktop.data.api.models.SearchResult3Dto
import dev.neiro.desktop.data.repository.MusicRepository
import dev.neiro.desktop.player.DesktopPlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: SearchResult3Dto? = null,
    val isLoading: Boolean = false
)

class SearchViewModel(
    private val musicRepository: MusicRepository,
    private val playerController: DesktopPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = null, isLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val results = musicRepository.search(query)
                _uiState.value = _uiState.value.copy(results = results, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun playSong(songId: String) {
        viewModelScope.launch {
            val results = _uiState.value.results ?: return@launch
            val songs = results.song
            val index = songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
            if (songs.isNotEmpty()) {
                playerController.playTrack(songs[index], songs, index)
            }
        }
    }
}
