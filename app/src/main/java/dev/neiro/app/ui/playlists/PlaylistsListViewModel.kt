package dev.neiro.app.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.PlaylistDto
import dev.neiro.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsListUiState(
    val playlists: List<PlaylistDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlaylistsListViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsListUiState())
    val uiState: StateFlow<PlaylistsListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PlaylistsListUiState(isLoading = true)
            runCatching { musicRepository.getPlaylists() }
                .onSuccess { _uiState.value = PlaylistsListUiState(playlists = it) }
                .onFailure { _uiState.value = PlaylistsListUiState(error = it.message ?: it.javaClass.simpleName) }
        }
    }
}
