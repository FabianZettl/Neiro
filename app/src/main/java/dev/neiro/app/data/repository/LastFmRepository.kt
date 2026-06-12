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
    private val topArtistsCache = MemoryCache<String, LastFmTopArtistsResponse>(ttlMs = 15 * 60_000L)
    private val topAlbumsCache  = MemoryCache<String, LastFmTopAlbumsResponse>(ttlMs = 15 * 60_000L)
    private val topTracksCache  = MemoryCache<String, LastFmTopTracksResponse>(ttlMs = 15 * 60_000L)
    private val lovedTracksCache = MemoryCache<Unit, Set<String>>(ttlMs = 5 * 60_000L)
    private val artistInfoCache = MemoryCache<String, LastFmArtistInfo>(ttlMs = 30 * 60_000L)
    private val albumInfoCache  = MemoryCache<String, LastFmAlbumInfo>(ttlMs = 30 * 60_000L)

    private suspend fun creds(): Pair<String, String> {
        val prefs = preferences.prefsFlow.first()
        return prefs.lastFmUsername to prefs.lastFmApiKey
    }

    fun isConfigured(username: String, apiKey: String) =
        username.isNotBlank() && apiKey.isNotBlank()

    suspend fun getTopArtists(period: String, limit: Int = 20): LastFmTopArtistsResponse? {
        val cacheKey = "$period:$limit"
        topArtistsCache.get(cacheKey)?.let { return it }
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching { api.getTopArtists(user = user, period = period, limit = limit, apiKey = key) }
            .getOrNull()?.also { topArtistsCache.put(cacheKey, it) }
    }

    suspend fun getTopTracks(period: String, limit: Int = 20): LastFmTopTracksResponse? {
        val cacheKey = "$period:$limit"
        topTracksCache.get(cacheKey)?.let { return it }
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching { api.getTopTracks(user = user, apiKey = key, period = period, limit = limit) }
            .getOrNull()?.also { topTracksCache.put(cacheKey, it) }
    }

    suspend fun getTopAlbums(period: String, limit: Int = 20): LastFmTopAlbumsResponse? {
        val cacheKey = "$period:$limit"
        topAlbumsCache.get(cacheKey)?.let { return it }
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching { api.getTopAlbums(user = user, period = period, limit = limit, apiKey = key) }
            .getOrNull()?.also { topAlbumsCache.put(cacheKey, it) }
    }

    suspend fun getArtistInfo(artistName: String): LastFmArtistInfo? {
        artistInfoCache.get(artistName)?.let { return it }
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching {
            api.getArtistInfo(artist = artistName, username = user, apiKey = key).artist
        }.getOrNull()?.also { artistInfoCache.put(artistName, it) }
    }

    suspend fun getAlbumInfo(artistName: String, albumName: String): LastFmAlbumInfo? {
        val cacheKey = "$artistName::$albumName"
        albumInfoCache.get(cacheKey)?.let { return it }
        val (user, key) = creds()
        if (!isConfigured(user, key)) return null
        return runCatching {
            api.getAlbumInfo(artist = artistName, album = albumName, username = user, apiKey = key).album
        }.getOrNull()?.also { albumInfoCache.put(cacheKey, it) }
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
        lovedTracksCache.get(Unit)?.let { return it }
        val (user, key) = creds()
        if (!isConfigured(user, key)) return emptySet()
        return runCatching {
            api.getLovedTracks(user = user, apiKey = key, limit = limit)
                .lovedTracks?.tracks.orEmpty()
                .map { "${it.name.lowercase()}_${it.artist.name.lowercase()}" }
                .toSet()
        }.getOrElse { emptySet() }.also { lovedTracksCache.put(Unit, it) }
    }

    /** Call after loving/unloving a track so the cache reflects the new state. */
    fun invalidateLovedTracks() = lovedTracksCache.invalidate(Unit)

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
