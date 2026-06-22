package dev.neiro.app.data.repository

import com.google.gson.Gson
import dev.neiro.app.data.prefs.NieroPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ConnectOkHttp

// ── Protocol models ────────────────────────────────────────────────────────────

data class DesktopSong(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArtId: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val volume: Int,
    val isShuffled: Boolean,
    val repeatMode: Int
)

sealed class DesktopState {
    data object Disconnected : DesktopState()
    data object Connected : DesktopState()          // connected but desktop is idle
    data class Playing(val song: DesktopSong) : DesktopState()
}

// ── Repository ─────────────────────────────────────────────────────────────────

@Singleton
class ConnectRepository @Inject constructor(
    private val preferences: NieroPreferences,
    @ConnectOkHttp private val http: OkHttpClient
) {
    private val gson = Gson()

    private val _state = MutableStateFlow<DesktopState>(DesktopState.Disconnected)
    val state: StateFlow<DesktopState> = _state

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var scope: CoroutineScope? = null

    /** Call once at app startup with the app-level coroutine scope. */
    fun start(appScope: CoroutineScope) {
        scope = appScope
        appScope.launch {
            preferences.prefsFlow.collect { prefs ->
                if (prefs.connectHost.isBlank() || prefs.connectToken.isBlank()) {
                    disconnect()
                } else {
                    reconnect(prefs.connectHost, prefs.connectPort, prefs.connectToken)
                }
            }
        }
    }

    private fun reconnect(host: String, port: Int, token: String) {
        disconnect()
        reconnectJob = scope?.launch(Dispatchers.IO) {
            while (true) {
                connect(host, port, token)
                // If we get here the socket closed — wait before retrying
                delay(5_000)
            }
        }
    }

    private suspend fun connect(host: String, port: Int, token: String) {
        val url = "ws://$host:$port/api/ws?token=$token"
        val request = Request.Builder().url(url).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (_state.value is DesktopState.Disconnected) {
                    _state.value = DesktopState.Connected
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseAndUpdateState(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = DesktopState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = DesktopState.Disconnected
            }
        }
        webSocket = http.newWebSocket(request, listener)
        // Block until the socket closes (we poll state to detect closure)
        while (webSocket != null && _state.value !is DesktopState.Disconnected) {
            delay(500)
        }
    }

    private fun parseAndUpdateState(json: String) {
        runCatching {
            val map = gson.fromJson(json, Map::class.java)
            val type = map["type"] as? String ?: "idle"
            if (type == "idle") {
                _state.value = DesktopState.Connected
            } else {
                val song = DesktopSong(
                    songId     = map["songId"] as? String ?: "",
                    title      = map["title"] as? String ?: "",
                    artist     = map["artist"] as? String ?: "",
                    album      = map["album"] as? String ?: "",
                    coverArtId = map["coverArtId"] as? String,
                    isPlaying  = map["isPlaying"] as? Boolean ?: false,
                    positionMs = (map["positionMs"] as? Double)?.toLong() ?: 0L,
                    durationMs = (map["durationMs"] as? Double)?.toLong() ?: 0L,
                    volume     = (map["volume"] as? Double)?.toInt() ?: 80,
                    isShuffled = map["isShuffled"] as? Boolean ?: false,
                    repeatMode = (map["repeatMode"] as? Double)?.toInt() ?: 0
                )
                _state.value = DesktopState.Playing(song)
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "disconnect")
        webSocket = null
        _state.value = DesktopState.Disconnected
    }

    // ── REST commands ──────────────────────────────────────────────────────────

    fun sendCommand(action: String, value: Long = 0L) {
        scope?.launch(Dispatchers.IO) {
            val prefs = preferences.prefsFlow.first()
            if (prefs.connectHost.isBlank()) return@launch
            val url = "http://${prefs.connectHost}:${prefs.connectPort}/api/control?token=${prefs.connectToken}"
            val body = gson.toJson(mapOf("action" to action, "value" to value))
            runCatching {
                http.newCall(
                    Request.Builder().url(url)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute().close()
            }
        }
    }

    fun castSongs(songIds: List<String>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        scope?.launch(Dispatchers.IO) {
            val prefs = preferences.prefsFlow.first()
            if (prefs.connectHost.isBlank()) return@launch
            val url = "http://${prefs.connectHost}:${prefs.connectPort}/api/cast?token=${prefs.connectToken}"
            val body = gson.toJson(mapOf(
                "songIds"        to songIds,
                "startIndex"     to startIndex,
                "startPositionMs" to startPositionMs
            ))
            runCatching {
                http.newCall(
                    Request.Builder().url(url)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute().close()
            }
        }
    }
}
