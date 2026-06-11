package dev.neiro.desktop.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neiro.desktop.data.prefs.DesktopPreferences
import dev.neiro.desktop.data.prefs.NieroPrefs
import dev.neiro.desktop.data.repository.LastFmRepository
import dev.neiro.desktop.data.repository.MusicRepository
import dev.neiro.desktop.sync.SyncCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Testing : ConnectionState()
    data class Success(val serverVersion: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val streamingBitrate: Int = 0,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val lastFmUsername: String = "",
    val lastFmApiKey: String = "",
    val lastFmApiSecret: String = "",
    val lastFmPassword: String = "",
    val lastFmSessionKey: String = "",
    val lastFmAuthState: ConnectionState = ConnectionState.Idle,
    val syncCode: String = ""
)

class SettingsViewModel(
    private val preferences: DesktopPreferences,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.prefsFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    serverUrl = prefs.serverUrl,
                    username = prefs.username,
                    password = prefs.password,
                    streamingBitrate = prefs.streamingBitrate,
                    lastFmUsername = prefs.lastFmUsername,
                    lastFmApiKey = prefs.lastFmApiKey,
                    lastFmApiSecret = prefs.lastFmApiSecret,
                    lastFmSessionKey = prefs.lastFmSessionKey,
                    syncCode = if (prefs.serverUrl.isNotBlank() && prefs.username.isNotBlank())
                        SyncCode.encode(prefs.serverUrl, prefs.username, prefs.password)
                    else ""
                )
            }
        }
    }

    fun onServerUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(serverUrl = value, connectionState = ConnectionState.Idle)
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, connectionState = ConnectionState.Idle)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, connectionState = ConnectionState.Idle)
    }

    fun onBitrateChange(bitrate: Int) {
        _uiState.value = _uiState.value.copy(streamingBitrate = bitrate)
        saveCurrentPrefs()
    }

    fun onLastFmUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(lastFmUsername = value, lastFmAuthState = ConnectionState.Idle)
    }

    fun onLastFmApiKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(lastFmApiKey = value, lastFmAuthState = ConnectionState.Idle)
    }

    fun onLastFmApiSecretChange(value: String) {
        _uiState.value = _uiState.value.copy(lastFmApiSecret = value, lastFmAuthState = ConnectionState.Idle)
    }

    fun onLastFmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(lastFmPassword = value, lastFmAuthState = ConnectionState.Idle)
    }

    fun connectAndTest() {
        val state = _uiState.value
        _uiState.value = state.copy(connectionState = ConnectionState.Testing)
        viewModelScope.launch {
            // First save the prefs so the interceptor picks them up
            val prefs = preferences.prefsFlow.first().copy(
                serverUrl = state.serverUrl,
                username = state.username,
                password = state.password,
                streamingBitrate = state.streamingBitrate
            )
            preferences.savePrefs(prefs)

            val (ok, version) = musicRepository.pingServer()
            _uiState.value = _uiState.value.copy(
                connectionState = if (ok)
                    ConnectionState.Success(version)
                else
                    ConnectionState.Error(version)
            )
        }
    }

    fun disconnectLastFm() {
        viewModelScope.launch {
            val prefs = preferences.prefsFlow.first().copy(
                lastFmSessionKey = "",
                lastFmApiSecret = ""
            )
            preferences.savePrefs(prefs)
            _uiState.value = _uiState.value.copy(
                lastFmSessionKey = "",
                lastFmApiSecret = "",
                lastFmAuthState = ConnectionState.Idle
            )
        }
    }

    fun connectToLastFm(lastFmRepository: LastFmRepository) {
        val state = _uiState.value
        _uiState.value = state.copy(lastFmAuthState = ConnectionState.Testing)
        viewModelScope.launch {
            // Save lastfm username + key first
            val prefs = preferences.prefsFlow.first().copy(
                lastFmUsername = state.lastFmUsername,
                lastFmApiKey = state.lastFmApiKey,
                lastFmApiSecret = state.lastFmApiSecret
            )
            preferences.savePrefs(prefs)

            val ok = lastFmRepository.authenticate(state.lastFmPassword, state.lastFmApiSecret)
            _uiState.value = _uiState.value.copy(
                lastFmAuthState = if (ok)
                    ConnectionState.Success("")
                else
                    ConnectionState.Error("Authentication failed. Check credentials.")
            )
        }
    }

    fun generateSyncCode() {
        viewModelScope.launch {
            val state = _uiState.value
            val sectionsJson = preferences.prefsFlow.value.homeSectionsJson
            val code = SyncCode.encode(state.serverUrl, state.username, state.password, sectionsJson)
            _uiState.value = state.copy(syncCode = code)
        }
    }

    private fun saveCurrentPrefs() {
        viewModelScope.launch {
            val current = preferences.prefsFlow.first()
            val state = _uiState.value
            preferences.savePrefs(
                current.copy(
                    serverUrl = state.serverUrl,
                    username = state.username,
                    password = state.password,
                    streamingBitrate = state.streamingBitrate
                )
            )
        }
    }
}
