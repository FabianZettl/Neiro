package dev.neiro.app.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.PlaylistDto
import dev.neiro.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlaylistSortOption(val label: String) {
    NAME_ASC("Name A→Z"),
    NAME_DESC("Name Z→A"),
    SONGS_DESC("Most Songs"),
    SONGS_ASC("Fewest Songs")
}

data class PlaylistsListUiState(
    val playlists: List<PlaylistDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlaylistsListViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _rawPlaylists = MutableStateFlow<List<PlaylistDto>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    val sortOption = MutableStateFlow(PlaylistSortOption.NAME_ASC)
    val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<PlaylistsListUiState> = combine(
        _rawPlaylists, _isLoading, _error, sortOption, searchQuery
    ) { raw, loading, error, sort, query ->
        val filtered = if (query.isBlank()) raw
        else raw.filter { it.name.contains(query, ignoreCase = true) }
        val sorted = when (sort) {
            PlaylistSortOption.NAME_ASC   -> filtered.sortedBy { it.name.lowercase() }
            PlaylistSortOption.NAME_DESC  -> filtered.sortedByDescending { it.name.lowercase() }
            PlaylistSortOption.SONGS_DESC -> filtered.sortedByDescending { it.songCount }
            PlaylistSortOption.SONGS_ASC  -> filtered.sortedBy { it.songCount }
        }
        PlaylistsListUiState(playlists = sorted, isLoading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistsListUiState(isLoading = true))

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { musicRepository.getPlaylists() }
                .onSuccess { _rawPlaylists.value = it; _isLoading.value = false }
                .onFailure { _error.value = it.message ?: it.javaClass.simpleName; _isLoading.value = false }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(name)
            load()
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(id)
            load()
        }
    }
}
