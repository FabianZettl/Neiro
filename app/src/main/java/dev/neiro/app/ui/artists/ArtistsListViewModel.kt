package dev.neiro.app.ui.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.ArtistDto
import dev.neiro.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ArtistSortOption(val label: String) {
    NAME_ASC("Name A→Z"),
    NAME_DESC("Name Z→A"),
    ALBUMS_DESC("Most Albums"),
    ALBUMS_ASC("Fewest Albums")
}

data class ArtistsListUiState(
    val artists: List<ArtistDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ArtistsListViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _rawArtists = MutableStateFlow<List<ArtistDto>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    val sortOption = MutableStateFlow(ArtistSortOption.NAME_ASC)
    val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ArtistsListUiState> = combine(
        _rawArtists, _isLoading, _error, sortOption, searchQuery
    ) { raw, loading, error, sort, query ->
        val filtered = if (query.isBlank()) raw
        else raw.filter { it.name.contains(query, ignoreCase = true) }
        val sorted = when (sort) {
            ArtistSortOption.NAME_ASC    -> filtered.sortedBy { it.name.lowercase() }
            ArtistSortOption.NAME_DESC   -> filtered.sortedByDescending { it.name.lowercase() }
            ArtistSortOption.ALBUMS_DESC -> filtered.sortedByDescending { it.albumCount }
            ArtistSortOption.ALBUMS_ASC  -> filtered.sortedBy { it.albumCount }
        }
        ArtistsListUiState(artists = sorted, isLoading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistsListUiState(isLoading = true))

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { musicRepository.getAllArtists() }
                .onSuccess { _rawArtists.value = it; _isLoading.value = false }
                .onFailure { _error.value = it.message ?: it.javaClass.simpleName; _isLoading.value = false }
        }
    }
}
