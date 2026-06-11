package dev.neiro.app.ui.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.InternetRadioStationDto
import dev.neiro.app.data.repository.MusicRepository
import dev.neiro.app.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RadioUiState(
    val stations: List<InternetRadioStationDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RadioUiState(isLoading = true)
            runCatching { musicRepository.getInternetRadioStations() }
                .onSuccess { _uiState.value = RadioUiState(stations = it) }
                .onFailure { _uiState.value = RadioUiState(error = it.message ?: it.javaClass.simpleName) }
        }
    }

    fun play(station: InternetRadioStationDto) {
        viewModelScope.launch {
            playerController.playRadio(station)
        }
    }
}
