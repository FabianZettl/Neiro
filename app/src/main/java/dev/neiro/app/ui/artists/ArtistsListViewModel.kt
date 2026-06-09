package dev.neiro.app.ui.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.ArtistDto
import dev.neiro.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistsListUiState(
    val artists: List<ArtistDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ArtistsListViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistsListUiState())
    val uiState: StateFlow<ArtistsListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ArtistsListUiState(isLoading = true)
            runCatching { musicRepository.getAllArtists() }
                .onSuccess { _uiState.value = ArtistsListUiState(artists = it) }
                .onFailure { _uiState.value = ArtistsListUiState(error = it.message ?: it.javaClass.simpleName) }
        }
    }
}
