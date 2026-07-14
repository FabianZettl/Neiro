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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Testing : ConnectionState()
    data class Success(val serverVersion: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/** Pending choice after a QR sync found different Home layouts on both devices. */
data class HomeLayoutConflict(
    val desktopHomeSectionsJson: String,
    val host: String,
    val port: String,
    val token: String
)

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
    val lastFmPassword: String = "",
    val lastFmSessionKey: String = "",
    val lastFmAuthState: ConnectionState = ConnectionState.Idle,
    val desktopSyncState: ConnectionState = ConnectionState.Idle,
    val connectHost: String = "",
    val homeLayoutConflict: HomeLayoutConflict? = null,
    val homeLayoutPushResult: String? = null
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
                    lastFmSessionKey = prefs.lastFmSessionKey,
                    lastFmAuthState = if (prefs.lastFmSessionKey.isNotBlank())
                        ConnectionState.Success("Connected") else ConnectionState.Idle,
                    connectHost = prefs.connectHost
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

    fun onLastFmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(lastFmPassword = value, lastFmAuthState = ConnectionState.Idle)
    }

    fun connectToLastFm() {
        val state = _uiState.value
        if (state.lastFmUsername.isBlank() || state.lastFmPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(
                lastFmAuthState = ConnectionState.Error("Enter your Last.fm username and password")
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(lastFmAuthState = ConnectionState.Testing)
            val ok = lastFmRepository.authenticate(state.lastFmUsername, state.lastFmPassword)
            _uiState.value = _uiState.value.copy(
                lastFmPassword = "",
                lastFmAuthState = if (ok) ConnectionState.Success("Connected to Last.fm")
                                  else ConnectionState.Error("Authentication failed — check credentials")
            )
            if (ok) {
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

    /**
     * Called when the QR scanner reads a neiro-sync:// URL from the desktop app.
     * Parses host/port/token and POSTs the current server credentials to the desktop.
     * Also extracts connectPort + connectToken for Neiro Connect and saves them.
     */
    fun onDesktopQrScanned(url: String) {
        val uri = Uri.parse(url)
        val host  = uri.getQueryParameter("host")  ?: return
        val port  = uri.getQueryParameter("port")  ?: return
        val token = uri.getQueryParameter("token") ?: return
        // Neiro Connect params — optional (desktop may not have Connect enabled)
        val connectPort  = uri.getQueryParameter("connectPort")?.toIntOrNull() ?: 7373
        val connectToken = uri.getQueryParameter("connectToken") ?: ""
        val s = _uiState.value
        if (s.serverUrl.isBlank() || s.username.isBlank()) {
            _uiState.value = _uiState.value.copy(
                desktopSyncState = ConnectionState.Error("Configure your server first")
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(desktopSyncState = ConnectionState.Testing)
            val sectionsJson = preferences.homeSectionsJson.first() ?: ""
            val payload = Gson().toJson(mapOf(
                "serverUrl"       to s.serverUrl,
                "username"        to s.username,
                "password"        to s.password,
                "homeSectionsJson" to sectionsJson
            ))
            var conflict: HomeLayoutConflict? = null
            _uiState.value = _uiState.value.copy(
                desktopSyncState = try {
                    withContext(Dispatchers.IO) {
                        val request = Request.Builder()
                            .url("http://$host:$port/sync?token=$token")
                            .post(payload.toRequestBody("application/json".toMediaType()))
                            .build()
                        pingClient.newCall(request).execute().use { resp ->
                            if (resp.isSuccessful) {
                                // Desktop returns its own (pre-overwrite) Home layout so we can
                                // detect a conflict with what we just sent it.
                                val ackBody = resp.body?.string().orEmpty()
                                val ack = runCatching {
                                    Gson().fromJson(ackBody, Map::class.java)
                                }.getOrNull()
                                val desktopSections = ack?.get("homeSectionsJson") as? String ?: ""
                                val hasConflict = ack?.get("conflict") as? Boolean ?: false
                                if (hasConflict) {
                                    conflict = HomeLayoutConflict(desktopSections, host, port, token)
                                }
                                ConnectionState.Success("Desktop connected!")
                            } else {
                                ConnectionState.Error("Desktop rejected the connection (${resp.code})")
                            }
                        }
                    }
                } catch (e: Exception) {
                    ConnectionState.Error(e.localizedMessage ?: "Could not reach desktop")
                },
                homeLayoutConflict = conflict
            )
            // Persist Neiro Connect info so ConnectRepository can start the WebSocket
            if (connectToken.isNotBlank()) {
                preferences.saveConnectInfo(host, connectPort, connectToken)
            }
        }
    }

    /** User chose to keep this phone's Home layout — tell the desktop to adopt it. */
    fun resolveHomeLayoutKeepMobile() {
        val conflict = _uiState.value.homeLayoutConflict ?: return
        _uiState.value = _uiState.value.copy(homeLayoutConflict = null)
        viewModelScope.launch {
            val sectionsJson = preferences.homeSectionsJson.first() ?: ""
            postResolve(conflict.host, conflict.port, conflict.token, keepMobile = true, mobileJson = sectionsJson)
        }
    }

    /** User chose to keep the desktop's Home layout — adopt it locally and tell the desktop to keep its own. */
    fun resolveHomeLayoutKeepDesktop() {
        val conflict = _uiState.value.homeLayoutConflict ?: return
        _uiState.value = _uiState.value.copy(homeLayoutConflict = null)
        viewModelScope.launch {
            preferences.saveHomeSections(conflict.desktopHomeSectionsJson)
            postResolve(conflict.host, conflict.port, conflict.token, keepMobile = false)
        }
    }

    private suspend fun postResolve(host: String, port: String, token: String, keepMobile: Boolean, mobileJson: String = "") {
        val body = Gson().toJson(mapOf("keepMobile" to keepMobile, "mobileHomeSectionsJson" to mobileJson))
        runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("http://$host:$port/resolve?token=$token")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                pingClient.newCall(request).execute().close()
            }
        }
    }

    /** Manually push this phone's current Home layout to the paired desktop over Neiro Connect. */
    fun pushHomeLayoutToDesktop() {
        viewModelScope.launch {
            val prefs = preferences.prefsFlow.first()
            if (prefs.connectHost.isBlank()) {
                _uiState.value = _uiState.value.copy(homeLayoutPushResult = "Kein Desktop gekoppelt")
                return@launch
            }
            val sectionsJson = preferences.homeSectionsJson.first() ?: ""
            val body = Gson().toJson(mapOf("homeSectionsJson" to sectionsJson))
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("http://${prefs.connectHost}:${prefs.connectPort}/api/homeLayout?token=${prefs.connectToken}")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    pingClient.newCall(request).execute().use { it.isSuccessful }
                }
            }.getOrDefault(false)
            _uiState.value = _uiState.value.copy(
                homeLayoutPushResult = if (ok) "Home-Layout gesendet" else "Fehlgeschlagen"
            )
            delay(3000)
            _uiState.value = _uiState.value.copy(homeLayoutPushResult = null)
        }
    }

    /** Saves only server credentials (used by onboarding). Does not ping. */
    suspend fun saveServerSettings(serverUrl: String, username: String, password: String) {
        val existing = preferences.prefsFlow.first()
        preferences.savePrefs(existing.copy(serverUrl = serverUrl, username = username, password = password))
    }
}
