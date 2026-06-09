package dev.neiro.app.ui.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.AlbumDto
import dev.neiro.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private val _uiState = MutableStateFlow(AlbumsListUiState())
    val uiState: StateFlow<AlbumsListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = AlbumsListUiState(isLoading = true)
            runCatching { musicRepository.getAlbumsByType(albumType, 500) }
                .onSuccess { _uiState.value = AlbumsListUiState(albums = it) }
                .onFailure { _uiState.value = AlbumsListUiState(error = it.message ?: it.javaClass.simpleName) }
        }
    }
}
