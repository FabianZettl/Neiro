package dev.neiro.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.SubsonicAuthInterceptor
import dev.neiro.app.data.api.models.PingApiResponse
import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.data.prefs.NieroPrefs
import dev.neiro.app.data.repository.LastFmRepository
import dev.neiro.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Testing : ConnectionState()
    data class Success(val serverVersion: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val streamingBitrate: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val dynamicColor: Boolean = true,
    val accentColorHex: String = "#E5484D",
    val lastFmUsername: String = "",
    val lastFmApiKey: String = "",
    val lastFmApiSecret: String = "",
    val lastFmPassword: String = "",
    val lastFmSessionKey: String = "",
    val lastFmAuthState: ConnectionState = ConnectionState.Idle
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: NieroPreferences,
    private val lastFmRepository: LastFmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Plain client — no interceptor — so we test with the form values as-is
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        viewModelScope.launch {
            preferences.prefsFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    serverUrl = prefs.serverUrl,
                    username = prefs.username,
                    password = prefs.password,
                    streamingBitrate = prefs.streamingBitrate,
                    dynamicColor = prefs.dynamicColor,
                    accentColorHex = prefs.accentColorHex,
                    lastFmUsername = prefs.lastFmUsername,
                    lastFmApiKey = prefs.lastFmApiKey,
                    lastFmApiSecret = prefs.lastFmApiSecret,
                    lastFmSessionKey = prefs.lastFmSessionKey,
                    lastFmAuthState = if (prefs.lastFmSessionKey.isNotBlank())
                        ConnectionState.Success("Connected") else ConnectionState.Idle
                )
            }
        }
        viewModelScope.launch {
            preferences.themeModeFlow.collect { mode ->
                _uiState.value = _uiState.value.copy(
                    themeMode = runCatching { ThemeMode.valueOf(mode) }.getOrElse { ThemeMode.DARK }
                )
            }
        }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
        viewModelScope.launch { preferences.saveThemeMode(mode.name) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(dynamicColor = enabled)
        viewModelScope.launch { preferences.savePrefs(_uiState.value.toNieroPrefs()) }
    }

    fun onAccentColorChange(hex: String) {
        _uiState.value = _uiState.value.copy(accentColorHex = hex)
        viewModelScope.launch { preferences.savePrefs(_uiState.value.toNieroPrefs()) }
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

    fun connectToLastFm() {
        val state = _uiState.value
        if (state.lastFmUsername.isBlank() || state.lastFmApiKey.isBlank() ||
            state.lastFmApiSecret.isBlank() || state.lastFmPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(
                lastFmAuthState = ConnectionState.Error("Fill in username, API key, API secret and password")
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(lastFmAuthState = ConnectionState.Testing)
            // Save credentials first so repository can read them
            preferences.savePrefs(_uiState.value.toNieroPrefs())
            val ok = lastFmRepository.authenticate(state.lastFmPassword, state.lastFmApiSecret)
            _uiState.value = _uiState.value.copy(
                lastFmPassword = "",  // clear password from UI after auth
                lastFmAuthState = if (ok) ConnectionState.Success("Connected to Last.fm")
                                  else ConnectionState.Error("Authentication failed — check credentials")
            )
            if (ok) {
                // Reload session key into state
                val prefs = preferences.prefsFlow.first()
                _uiState.value = _uiState.value.copy(lastFmSessionKey = prefs.lastFmSessionKey)
            }
        }
    }

    fun disconnectLastFm() {
        viewModelScope.launch {
            preferences.savePrefs(_uiState.value.toNieroPrefs().copy(lastFmSessionKey = ""))
            _uiState.value = _uiState.value.copy(
                lastFmSessionKey = "",
                lastFmAuthState = ConnectionState.Idle
            )
        }
    }

    private fun SettingsUiState.toNieroPrefs() = NieroPrefs(
        serverUrl = serverUrl,
        username = username,
        password = password,
        streamingBitrate = streamingBitrate,
        dynamicColor = dynamicColor,
        accentColorHex = accentColorHex,
        lastFmUsername = lastFmUsername,
        lastFmApiKey = lastFmApiKey,
        lastFmApiSecret = lastFmApiSecret,
        lastFmSessionKey = lastFmSessionKey
    )

    /** Normalises a URL typed by the user: trims whitespace, adds https:// if no scheme present. */
    private fun normaliseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    /** Saves settings to DataStore AND pings the server with the current form values. */
    fun connectAndTest() {
        viewModelScope.launch {
            // Normalise URL before doing anything else
            val normalisedUrl = normaliseUrl(_uiState.value.serverUrl)
            _uiState.value = _uiState.value.copy(serverUrl = normalisedUrl)

            val state = _uiState.value

            if (state.serverUrl.isBlank() || state.username.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error("Server URL and username are required")
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Testing)

            // Persist settings first so the rest of the app picks them up
            preferences.savePrefs(
                NieroPrefs(
                    serverUrl = state.serverUrl.trimEnd('/'),
                    username = state.username,
                    password = state.password,
                    streamingBitrate = state.streamingBitrate,
                    dynamicColor = state.dynamicColor,
                    accentColorHex = state.accentColorHex,
                    lastFmUsername = state.lastFmUsername,
                    lastFmApiKey = state.lastFmApiKey,
                    lastFmApiSecret = state.lastFmApiSecret,
                    lastFmSessionKey = state.lastFmSessionKey
                )
            )

            // Ping the server using the form values directly — bypasses interceptor cache
            val salt = SubsonicAuthInterceptor.generateSalt()
            val token = SubsonicAuthInterceptor.md5(state.password + salt)
            val base = state.serverUrl.trimEnd('/')
            val pingUrl =
                "$base/rest/ping?u=${state.username}&t=$token&s=$salt&v=1.16.1&c=neiro&f=json"

            _uiState.value = _uiState.value.copy(
                connectionState = try {
                    val body = withContext(Dispatchers.IO) {
                        pingClient.newCall(Request.Builder().url(pingUrl).build())
                            .execute()
                            .use { it.body?.string() ?: "" }
                    }
                    val parsed = Gson().fromJson(body, PingApiResponse::class.java)
                    val resp = parsed.response
                    if (resp.status == "ok") {
                        ConnectionState.Success(resp.version)
                    } else {
                        ConnectionState.Error(resp.error?.message ?: "Authentication failed (check username/password)")
                    }
                } catch (e: Exception) {
                    ConnectionState.Error(e.localizedMessage ?: "Could not reach server")
                }
            )
        }
    }

    /** Saves only server credentials (used by onboarding). Does not ping. */
    suspend fun saveServerSettings(serverUrl: String, username: String, password: String) {
        val existing = preferences.prefsFlow.first()
        preferences.savePrefs(existing.copy(serverUrl = serverUrl, username = username, password = password))
    }
}
