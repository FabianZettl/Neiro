package dev.neiro.app.ui.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.AlbumDto
import dev.neiro.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AlbumViewMode { GRID_2, GRID_3, LIST }

enum class AlbumSortOption(val label: String) {
    DEFAULT("Default"),
    NAME_ASC("Name A→Z"),
    NAME_DESC("Name Z→A"),
    ARTIST_ASC("Artist"),
    YEAR_DESC("Year ↓"),
    YEAR_ASC("Year ↑")
}

data class AlbumsListUiState(
    val albums: List<AlbumDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AlbumsListViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val albumType: String = savedStateHandle["albumType"] ?: "alphabeticalByName"

    private val _rawAlbums = MutableStateFlow<List<AlbumDto>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    val sortOption = MutableStateFlow(AlbumSortOption.DEFAULT)
    val viewMode   = MutableStateFlow(AlbumViewMode.GRID_2)
    val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<AlbumsListUiState> = combine(
        _rawAlbums, _isLoading, _error, sortOption, searchQuery
    ) { raw, loading, error, sort, query ->
        val filtered = if (query.isBlank()) raw
        else raw.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.artist?.contains(query, ignoreCase = true) == true
        }
        val sorted = when (sort) {
            AlbumSortOption.DEFAULT    -> filtered
            AlbumSortOption.NAME_ASC   -> filtered.sortedBy { it.name.lowercase() }
            AlbumSortOption.NAME_DESC  -> filtered.sortedByDescending { it.name.lowercase() }
            AlbumSortOption.ARTIST_ASC -> filtered.sortedBy { it.artist?.lowercase() ?: "" }
            AlbumSortOption.YEAR_DESC  -> filtered.sortedByDescending { it.year ?: 0 }
            AlbumSortOption.YEAR_ASC   -> filtered.sortedBy { it.year ?: Int.MAX_VALUE }
        }
        AlbumsListUiState(albums = sorted, isLoading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumsListUiState(isLoading = true))

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { musicRepository.getAlbumsByType(albumType, 500) }
                .onSuccess { _rawAlbums.value = it; _isLoading.value = false }
                .onFailure { _error.value = it.message ?: it.javaClass.simpleName; _isLoading.value = false }
        }
    }
}
