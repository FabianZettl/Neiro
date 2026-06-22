package dev.neiro.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


data class NieroPrefs(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val streamingBitrate: Int = 0,  // 0 = original
    val dynamicColor: Boolean = true,
    val accentColorHex: String = "#E5484D",
    val lastFmUsername: String = "",
    val lastFmSessionKey: String = "",
    // Neiro Connect — desktop companion
    val connectHost: String = "",
    val connectPort: Int = 7373,
    val connectToken: String = ""
)

@Singleton
class NieroPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val prefsFlow: Flow<NieroPrefs> = dataStore.data.map { prefs ->
        NieroPrefs(
            serverUrl = prefs[SERVER_URL_KEY] ?: "",
            username = prefs[USERNAME_KEY] ?: "",
            password = prefs[PASSWORD_KEY] ?: "",
            streamingBitrate = prefs[BITRATE_KEY] ?: 0,
            dynamicColor = prefs[DYNAMIC_COLOR_KEY] ?: true,
            accentColorHex = prefs[ACCENT_COLOR_KEY] ?: "#E5484D",
            lastFmUsername = prefs[LASTFM_USERNAME_KEY] ?: "",
            lastFmSessionKey = prefs[LASTFM_SESSION_KEY] ?: "",
            connectHost = prefs[CONNECT_HOST_KEY] ?: "",
            connectPort = prefs[CONNECT_PORT_KEY] ?: 7373,
            connectToken = prefs[CONNECT_TOKEN_KEY] ?: ""
        )
    }

    val homeSectionsJson: Flow<String?> = dataStore.data.map { it[HOME_SECTIONS_KEY] }

    val themeModeFlow: Flow<String> = dataStore.data.map { it[THEME_MODE_KEY] ?: "DARK" }

    val podcastSubscriptionsJson: Flow<String?> = dataStore.data.map { it[PODCAST_SUBSCRIPTIONS_KEY] }

    suspend fun getPodcastSubscriptions(): String? =
        dataStore.data.map { it[PODCAST_SUBSCRIPTIONS_KEY] }.first()

    suspend fun savePodcastSubscriptions(json: String) {
        dataStore.edit { it[PODCAST_SUBSCRIPTIONS_KEY] = json }
    }

    suspend fun savePrefs(nieroPrefs: NieroPrefs) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = nieroPrefs.serverUrl
            prefs[USERNAME_KEY] = nieroPrefs.username
            prefs[PASSWORD_KEY] = nieroPrefs.password
            prefs[BITRATE_KEY] = nieroPrefs.streamingBitrate
            prefs[DYNAMIC_COLOR_KEY] = nieroPrefs.dynamicColor
            prefs[ACCENT_COLOR_KEY] = nieroPrefs.accentColorHex
            prefs[LASTFM_USERNAME_KEY] = nieroPrefs.lastFmUsername
            prefs[LASTFM_SESSION_KEY] = nieroPrefs.lastFmSessionKey
            // Connect info is managed exclusively via saveConnectInfo / clearConnectInfo
        }
    }

    suspend fun saveConnectInfo(host: String, port: Int, token: String) {
        dataStore.edit { prefs ->
            prefs[CONNECT_HOST_KEY] = host
            prefs[CONNECT_PORT_KEY] = port
            prefs[CONNECT_TOKEN_KEY] = token
        }
    }

    suspend fun clearConnectInfo() {
        dataStore.edit { prefs ->
            prefs[CONNECT_HOST_KEY] = ""
            prefs[CONNECT_PORT_KEY] = 7373
            prefs[CONNECT_TOKEN_KEY] = ""
        }
    }

    suspend fun saveHomeSections(json: String) {
        dataStore.edit { it[HOME_SECTIONS_KEY] = json }
    }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    companion object {
        val SERVER_URL_KEY = stringPreferencesKey("server_url")
        val USERNAME_KEY = stringPreferencesKey("username")
        val PASSWORD_KEY = stringPreferencesKey("password")
        val BITRATE_KEY = intPreferencesKey("streaming_bitrate")
        val HOME_SECTIONS_KEY = stringPreferencesKey("home_sections")
        val THEME_MODE_KEY    = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        val ACCENT_COLOR_KEY  = stringPreferencesKey("accent_color")
        val LASTFM_USERNAME_KEY = stringPreferencesKey("lastfm_username")
        val LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
        val PODCAST_SUBSCRIPTIONS_KEY = stringPreferencesKey("podcast_subscriptions")
        val CONNECT_HOST_KEY = stringPreferencesKey("connect_host")
        val CONNECT_PORT_KEY = intPreferencesKey("connect_port")
        val CONNECT_TOKEN_KEY = stringPreferencesKey("connect_token")
    }
}
