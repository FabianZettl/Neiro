package dev.neiro.app.data.repository

import dev.neiro.app.data.api.LastFmApi
import dev.neiro.app.data.api.models.LastFmAlbumInfo
import dev.neiro.app.data.api.models.LastFmArtistInfo
import dev.neiro.app.data.api.models.LastFmLovedTrack
import dev.neiro.app.data.api.models.LastFmTopAlbumsResponse
import dev.neiro.app.data.api.models.LastFmTopArtistsResponse
import dev.neiro.app.data.api.models.LastFmTopTracksResponse
import dev.neiro.app.data.api.models.LastFmTrackInfo
import dev.neiro.app.data.api.models.LastFmUserInfo
import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.data.prefs.NieroPrefs
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmRepository @Inject constructor(
    private val api: LastFmApi,
    private val preferences: NieroPreferences
) {
    private suspend fun creds(): Pair<String, String> {
        val prefs = preferences.prefsFlow.first()
        return prefs.lastFmUsername to prefs.lastFmApiKey
    }

    fun isConfigured(username: String, apiKey: String) =
        username.isNotBlank() && apiKey.isNotBlank()

    suspend fun getTopArtists(period: String, limit: Int = 20): LastFmTopArtistsResponse? {
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching { api.getTopArtists(user = user, period = period, limit = limit, apiKey = key) }
            .getOrNull()
    }

    suspend fun getTopTracks(period: String, limit: Int = 20): LastFmTopTracksResponse? {
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching { api.getTopTracks(user = user, apiKey = key, period = period, limit = limit) }
            .getOrNull()
    }

    suspend fun getTopAlbums(period: String, limit: Int = 20): LastFmTopAlbumsResponse? {
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching { api.getTopAlbums(user = user, period = period, limit = limit, apiKey = key) }
            .getOrNull()
    }

    suspend fun getArtistInfo(artistName: String): LastFmArtistInfo? {
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching {
            api.getArtistInfo(artist = artistName, username = user, apiKey = key).artist
        }.getOrNull()
    }

    suspend fun getAlbumInfo(artistName: String, albumName: String): LastFmAlbumInfo? {
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching {
            api.getAlbumInfo(artist = artistName, album = albumName, username = user, apiKey = key).album
        }.getOrNull()
    }

    suspend fun getUserInfo(): LastFmUserInfo? {
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching { api.getUserInfo(user = user, apiKey = key).user }.getOrNull()
    }

    suspend fun getTrackInfo(trackName: String, artistName: String): LastFmTrackInfo? {
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching {
            api.getTrackInfo(track = trackName, artist = artistName, username = user, apiKey = key).track
        }.getOrNull()
    }

    /** Returns a set of "trackname_artistname" (lowercase) for the user's loved tracks. */
    suspend fun getLovedTracks(limit: Int = 200): Set<String> {
        val (user, key) = creds()
        if (!isConfigured(user, key)) return emptySet()
        return runCatching {
            api.getLovedTracks(user = user, apiKey = key, limit = limit)
                .lovedTracks?.tracks.orEmpty()
                .map { "${it.name.lowercase()}_${it.artist.name.lowercase()}" }
                .toSet()
        }.getOrElse { emptySet() }
    }

    /** Returns the full loved track list (with image URLs) for display. */
    suspend fun getLovedTracksFull(limit: Int = 500): List<LastFmLovedTrack> {
        val (user, key) = creds()
        if (!isConfigured(user, key)) return emptyList()
        return runCatching {
            api.getLovedTracks(user = user, apiKey = key, limit = limit)
                .lovedTracks?.tracks.orEmpty()
        }.getOrElse { emptyList() }
    }

    /** Authenticates with Last.fm using password + API secret, stores the session key. Returns true on success. */
    suspend fun authenticate(password: String, apiSecret: String): Boolean {
        val prefs = preferences.prefsFlow.first()
        val user = prefs.lastFmUsername
        val key = prefs.lastFmApiKey
        if (!isConfigured(user, key)) return false
        val sig = computeApiSig(
            mapOf(
                "api_key" to key,
                "method" to "auth.getMobileSession",
                "password" to password,
                "username" to user
            ),
            apiSecret
        )
        val sessionKey = runCatching {
            api.getMobileSession(username = user, password = password, apiKey = key, apiSig = sig).session?.key
        }.getOrNull() ?: return false
        preferences.savePrefs(prefs.copy(lastFmApiSecret = apiSecret, lastFmSessionKey = sessionKey))
        return true
    }

    /** Loves or unloves a track. Returns true on success. */
    suspend fun loveTrack(trackName: String, artistName: String, love: Boolean): Boolean {
        val prefs = preferences.prefsFlow.first()
        val key = prefs.lastFmApiKey
        val secret = prefs.lastFmApiSecret
        val sk = prefs.lastFmSessionKey
        if (key.isBlank() || secret.isBlank() || sk.isBlank()) return false
        val method = if (love) "track.love" else "track.unlove"
        val sig = computeApiSig(
            mapOf(
                "api_key" to key,
                "artist" to artistName,
                "method" to method,
                "sk" to sk,
                "track" to trackName
            ),
            secret
        )
        return runCatching {
            api.loveTrack(
                method = method,
                track = trackName,
                artist = artistName,
                sessionKey = sk,
                apiKey = key,
                apiSig = sig
            )
            true
        }.getOrElse { false }
    }

    fun isSessionActive(): Boolean {
        // Synchronous check — use in ViewModel init after prefs are loaded
        return false // overridden by isSessionConfigured()
    }

    /** True if read-only stats (top artists/albums/tracks) are available. Only needs username + API key. */
    suspend fun isStatsConfigured(): Boolean {
        val prefs = preferences.prefsFlow.first()
        return prefs.lastFmUsername.isNotBlank() && prefs.lastFmApiKey.isNotBlank()
    }

    /** True if write operations (love/unlove) are available. Needs a session key. */
    suspend fun isSessionConfigured(): Boolean {
        val prefs = preferences.prefsFlow.first()
        return prefs.lastFmSessionKey.isNotBlank()
    }

    private fun computeApiSig(params: Map<String, String>, secret: String): String {
        val str = params.entries.sortedBy { it.key }.joinToString("") { "${it.key}${it.value}" } + secret
        return MessageDigest.getInstance("MD5")
            .digest(str.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    // Helper to update prefs without losing other fields
    private suspend fun NieroPrefs.save() = preferences.savePrefs(this)
}
