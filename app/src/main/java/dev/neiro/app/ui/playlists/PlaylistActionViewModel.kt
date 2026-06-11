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

@HiltViewModel
class PlaylistActionViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistDto>>(emptyList())
    val playlists: StateFlow<List<PlaylistDto>> = _playlists.asStateFlow()

    init {
        loadPlaylists()
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            runCatching { musicRepository.getPlaylists() }
                .onSuccess { _playlists.value = it }
        }
    }

    fun addToPlaylist(playlistId: String, songIds: List<String>) {
        viewModelScope.launch {
            musicRepository.addSongsToPlaylist(playlistId, songIds)
        }
    }

    fun createAndAddToPlaylist(name: String, songIds: List<String>) {
        viewModelScope.launch {
            val ok = musicRepository.createPlaylist(name)
            if (ok) {
                // Reload playlists to find the new one, then add songs
                val updated = runCatching { musicRepository.getPlaylists() }.getOrNull() ?: return@launch
                _playlists.value = updated
                val newPlaylist = updated.lastOrNull { it.name == name } ?: return@launch
                musicRepository.addSongsToPlaylist(newPlaylist.id, songIds)
            }
        }
    }
}
