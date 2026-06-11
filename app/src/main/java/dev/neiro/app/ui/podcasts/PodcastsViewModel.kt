package dev.neiro.app.ui.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.PodcastSubscription
import dev.neiro.app.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastsUiState(
    val subscriptions: List<PodcastSubscription> = emptyList(),
    val isLoading: Boolean = false,
    val addError: String? = null,
    val importMessage: String? = null
)

@HiltViewModel
class PodcastsViewModel @Inject constructor(
    private val repo: PodcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastsUiState())
    val uiState: StateFlow<PodcastsUiState> = _uiState.asStateFlow()

    val subscriptions = repo.subscriptionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFeed(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, addError = null)
            repo.addSubscription(url.trim())
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, addError = it.message) }
        }
    }

    fun removeSubscription(id: String) {
        viewModelScope.launch { repo.removeSubscription(id) }
    }

    fun importOpml(stream: java.io.InputStream) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, importMessage = null)
            val count = runCatching { repo.importOpml(stream) }.getOrElse { -1 }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                importMessage = if (count >= 0) "Imported $count podcast(s)" else "Import failed"
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(addError = null, importMessage = null)
    }
}
