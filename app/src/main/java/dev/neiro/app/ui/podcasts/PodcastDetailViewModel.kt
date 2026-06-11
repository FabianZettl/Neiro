package dev.neiro.app.ui.podcasts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.PodcastEpisode
import dev.neiro.app.data.api.models.PodcastSubscription
import dev.neiro.app.data.repository.PodcastRepository
import dev.neiro.app.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastDetailUiState(
    val subscription: PodcastSubscription? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: PodcastRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val subscriptionId: String = checkNotNull(savedStateHandle["subscriptionId"])

    private val _uiState = MutableStateFlow(PodcastDetailUiState(isLoading = true))
    val uiState: StateFlow<PodcastDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val sub = repo.subscriptionsFlow.first().find { it.id == subscriptionId }
            if (sub == null) {
                _uiState.value = PodcastDetailUiState(isLoading = false, error = "Podcast not found")
                return@launch
            }
            val episodes = repo.fetchEpisodes(sub)
            _uiState.value = PodcastDetailUiState(subscription = sub, episodes = episodes, isLoading = false)
        }
    }

    fun playEpisode(episode: PodcastEpisode) {
        val sub = _uiState.value.subscription ?: return
        viewModelScope.launch {
            playerController.playPodcastEpisode(episode, sub)
        }
    }
}
