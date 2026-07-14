package dev.neiro.app.data.repository

import dev.neiro.app.data.api.SubsonicApi
import dev.neiro.app.data.api.SubsonicAuthInterceptor
import dev.neiro.app.data.api.models.AlbumDto
import dev.neiro.app.ui.home.AlbumSortType
import dev.neiro.app.ui.home.GenreItem
import dev.neiro.app.ui.home.HomeSectionConfig
import java.time.Instant
import dev.neiro.app.data.api.models.ArtistDto
import dev.neiro.app.data.api.models.ArtistInfoDto
import dev.neiro.app.data.api.models.ArtistWithAlbumsDto
import dev.neiro.app.data.api.models.LibraryStats
import dev.neiro.app.data.api.models.InternetRadioStationDto
import dev.neiro.app.data.api.models.PlaylistDto
import dev.neiro.app.data.api.models.SearchResult3Dto
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.data.api.models.StructuredLyrics
import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.data.prefs.NieroPrefs
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: SubsonicApi,
    private val preferences: NieroPreferences
) {
    private val albumCache = MemoryCache<String, AlbumDto>(ttlMs = 5 * 60_000L)
    private val albumsByTypeCache = MemoryCache<String, List<AlbumDto>>(ttlMs = 5 * 60_000L)
    private val artistCache = MemoryCache<String, ArtistWithAlbumsDto>(ttlMs = 5 * 60_000L)
    private val allArtistsCache = MemoryCache<Unit, List<ArtistDto>>(ttlMs = 5 * 60_000L)
    private val searchSongsCache = MemoryCache<String, List<SongDto>>(ttlMs = 5 * 60_000L)


    suspend fun getAlbumsByFilter(config: HomeSectionConfig): List<AlbumDto> {
        val prefs = preferences.prefsFlow.first()

        // When client-side filters are active, fetch more so we have enough after filtering
        val hasClientFilters = config.genre != null
            || config.playedInLastDays != null
            || config.minPlayCount != null
            || (config.starredOnly && config.sortType != AlbumSortType.STARRED)
            || config.yearFrom != null
            || config.yearTo != null
        val fetchSize = if (hasClientFilters) minOf(config.size * 10, 500) else config.size

        val result = api.getAlbumList2(
            type = config.sortType.apiType,
            size = fetchSize
        )

        var albums = result.response.albumList2?.album.orEmpty().map { album ->
            album.copy(coverArtUrl = album.coverArt?.let { buildCoverArtUrl(prefs, it) })
        }

        // Client-side filtering
        if (config.starredOnly && config.sortType != AlbumSortType.STARRED) {
            albums = albums.filter { it.starred != null }
        }
        if (config.genre != null) {
            albums = albums.filter { album ->
                album.allGenres().any { it.contains(config.genre, ignoreCase = true) }
            }
        }
        if (config.minPlayCount != null) {
            albums = albums.filter { (it.playCount ?: 0) >= config.minPlayCount }
        }
        if (config.playedInLastDays != null) {
            val cutoffMs = System.currentTimeMillis() -
                config.playedInLastDays.toLong() * 24 * 60 * 60 * 1000L
            albums = albums.filter { album ->
                val played = album.played ?: return@filter false
                runCatching {
                    Instant.parse(played).toEpochMilli() >= cutoffMs
                }.getOrDefault(false)
            }
        }
        if (config.yearFrom != null) {
            albums = albums.filter { it.year != null && it.year >= config.yearFrom }
        }
        if (config.yearTo != null) {
            albums = albums.filter { it.year != null && it.year <= config.yearTo }
        }

        return albums.take(config.size)
    }

    suspend fun getAlbumsByType(type: String, size: Int = 20): List<AlbumDto> {
        val cacheKey = "$type:$size"
        albumsByTypeCache.get(cacheKey)?.let { return it }
        val prefs = preferences.prefsFlow.first()
        val result = api.getAlbumList2(type = type, size = size)
        return result.response.albumList2?.album.orEmpty().map { album ->
            album.copy(coverArtUrl = album.coverArt?.let { buildCoverArtUrl(prefs, it) })
        }.also { albumsByTypeCache.put(cacheKey, it) }
    }

    suspend fun getRecentAlbums(size: Int = 20): List<AlbumDto> {
        val prefs = preferences.prefsFlow.first()
        val result = api.getAlbumList2(type = "recent", size = size)
        return result.response.albumList2?.album.orEmpty().map { album ->
            album.copy(coverArtUrl = album.coverArt?.let { buildCoverArtUrl(prefs, it) })
        }
    }

    suspend fun getAlphabeticalAlbums(size: Int = 20): List<AlbumDto> {
        val prefs = preferences.prefsFlow.first()
        val result = api.getAlbumList2(type = "alphabeticalByName", size = size)
        return result.response.albumList2?.album.orEmpty().map { album ->
            album.copy(coverArtUrl = album.coverArt?.let { buildCoverArtUrl(prefs, it) })
        }
    }

    suspend fun getLibraryStats(): LibraryStats {
        val response = api.getArtists().response
        val artists = response.artists.index.flatMap { it.artist }
        val albumCount = artists.sumOf { it.albumCount }
        return LibraryStats(artistCount = artists.size, albumCount = albumCount)
    }

    suspend fun getNewestAlbums(size: Int = 20): List<AlbumDto> {
        val prefs = preferences.prefsFlow.first()
        val result = api.getAlbumList2(type = "newest", size = size)
        return result.response.albumList2?.album.orEmpty().map { album ->
            album.copy(coverArtUrl = album.coverArt?.let { buildCoverArtUrl(prefs, it) })
        }
    }

    suspend fun getArtist(id: String): ArtistWithAlbumsDto {
        artistCache.get(id)?.let { return it }
        val prefs = preferences.prefsFlow.first()
        val artist = api.getArtist(id).response.artist
        return artist.copy(
            album = artist.album.orEmpty().map { album ->
                album.copy(coverArtUrl = album.coverArt?.let { buildCoverArtUrl(prefs, it) })
            }
        ).also { artistCache.put(id, it) }
    }

    suspend fun getArtistInfo(artistId: String): ArtistInfoDto {
        return runCatching { api.getArtistInfo2(artistId).response.artistInfo2 }
            .getOrElse { ArtistInfoDto() }
    }

    suspend fun getArtistIdsByGenre(genre: String): Set<String> {
        // Fetch alphabetically so we get all albums, then filter client-side with contains
        val result = runCatching {
            api.getAlbumList2(type = "alphabeticalByName", size = 500)
        }.getOrNull() ?: return emptySet()
        return result.response.albumList2?.album.orEmpty()
            .filter { album -> album.allGenres().any { it.contains(genre, ignoreCase = true) } }
            .mapNotNull { it.artistId }
            .toSet()
    }

    suspend fun getAllArtists(): List<ArtistDto> {
        allArtistsCache.get(Unit)?.let { return it }
        val prefs = preferences.prefsFlow.first()
        return api.getArtists().response.artists.index.flatMap { it.artist }.map { entry ->
            ArtistDto(
                id = entry.id,
                name = entry.name,
                albumCount = entry.albumCount,
                coverArt = entry.coverArt,
                coverArtUrl = entry.coverArt?.let { buildCoverArtUrl(prefs, it, size = 200) }
            )
        }.also { allArtistsCache.put(Unit, it) }
    }

    suspend fun getAlbum(id: String): AlbumDto {
        albumCache.get(id)?.let { return it }
        val prefs = preferences.prefsFlow.first()
        val result = api.getAlbum(id)
        val album = result.response.album
        val songs = album.song.map { song ->
            song.copy(coverArtUrl = song.coverArt?.let { buildCoverArtUrl(prefs, it) })
        }
        return album.copy(
            coverArtUrl = album.coverArt?.let { buildCoverArtUrl(prefs, it) },
            song = songs
        ).also { albumCache.put(id, it) }
    }

    suspend fun search(query: String): SearchResult3Dto {
        val prefs = preferences.prefsFlow.first()
        val result = api.search3(query)
        return result.response.searchResult3.let { sr ->
            sr.copy(
                album = sr.album.map { a ->
                    a.copy(coverArtUrl = a.coverArt?.let { buildCoverArtUrl(prefs, it) })
                },
                song = sr.song.map { s ->
                    s.copy(coverArtUrl = s.coverArt?.let { buildCoverArtUrl(prefs, it) })
                }
            )
        }
    }

    suspend fun getGenres(): List<GenreItem> = runCatching {
        api.getGenres().response?.genres?.genres.orEmpty()
            .sortedByDescending { it.albumCount }
            .map { GenreItem(it.value, it.songCount, it.albumCount) }
    }.getOrElse { emptyList() }

    suspend fun searchSongs(query: String, artistQuery: String = ""): List<SongDto> {
        val q = if (artistQuery.isNotBlank()) "$query $artistQuery" else query
        searchSongsCache.get(q)?.let { return it }
        return runCatching {
            val prefs = preferences.prefsFlow.first()
            api.search3(q, songCount = 5, albumCount = 0, artistCount = 0)
                .response.searchResult3.song
                .map { song -> song.copy(coverArtUrl = song.coverArt?.let { buildCoverArtUrl(prefs, it) }) }
        }.getOrElse { emptyList() }.also { searchSongsCache.put(q, it) }
    }

    suspend fun getSimilarSongs(songId: String, count: Int = 20): List<SongDto> = runCatching {
        val prefs = preferences.prefsFlow.first()
        api.getSimilarSongs2(songId, count).response.similarSongs2.songs.map { song ->
            song.copy(coverArtUrl = song.coverArt?.let { buildCoverArtUrl(prefs, it) })
        }
    }.getOrElse { emptyList() }

    suspend fun getSong(songId: String): SongDto? = runCatching {
        val prefs = preferences.prefsFlow.first()
        val response = api.getSong(songId).response
        if (response.status != "ok" || response.song.id.isBlank()) return@runCatching null
        response.song.copy(coverArtUrl = response.song.coverArt?.let { buildCoverArtUrl(prefs, it) })
    }.getOrNull()

    suspend fun getLyrics(songId: String): StructuredLyrics? = runCatching {
        val resp = api.getLyricsBySongId(songId)
        val list = resp.response.lyricsList?.structuredLyrics ?: return@runCatching null
        list.firstOrNull { it.synced } ?: list.firstOrNull()
    }.getOrNull()

    suspend fun starAlbum(albumId: String) = runCatching { api.star(albumId = albumId) }
    suspend fun unstarAlbum(albumId: String) = runCatching { api.unstar(albumId = albumId) }

    suspend fun getPlaylists(): List<PlaylistDto> {
        return api.getPlaylists().response.playlists.playlist
    }

    suspend fun createPlaylist(name: String): Boolean =
        runCatching { api.createPlaylist(name).response.status == "ok" }.getOrDefault(false)

    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Boolean =
        runCatching { api.addSongsToPlaylist(playlistId, songIds).response.status == "ok" }.getOrDefault(false)

    suspend fun removeFromPlaylist(playlistId: String, songIndex: Int): Boolean =
        runCatching { api.removeFromPlaylist(playlistId, songIndex).response.status == "ok" }.getOrDefault(false)

    suspend fun deletePlaylist(playlistId: String): Boolean =
        runCatching { api.deletePlaylist(playlistId).response.status == "ok" }.getOrDefault(false)

    suspend fun getPlaylist(id: String): PlaylistDto {
        val prefs = preferences.prefsFlow.first()
        val playlist = api.getPlaylist(id).response.playlist
        return playlist.copy(
            entry = playlist.entry.map { song ->
                song.copy(coverArtUrl = song.coverArt?.let { buildCoverArtUrl(prefs, it) })
            }
        )
    }

    suspend fun getInternetRadioStations(): List<InternetRadioStationDto> =
        runCatching {
            api.getInternetRadioStations().response.internetRadioStations?.stations.orEmpty()
        }.getOrElse { emptyList() }

    fun buildStreamUrl(songId: String, prefs: NieroPrefs): String {
        val salt = SubsonicAuthInterceptor.generateSalt()
        val token = SubsonicAuthInterceptor.md5(prefs.password + salt)
        val base = prefs.serverUrl.trimEnd('/')
        val bitrateParam = if (prefs.streamingBitrate > 0) "&maxBitRate=${prefs.streamingBitrate}" else ""
        return "$base/rest/stream?id=$songId&u=${prefs.username}&t=$token&s=$salt&v=1.16.1&c=neiro&f=json$bitrateParam"
    }

    fun buildCoverArtUrl(prefs: NieroPrefs, id: String, size: Int = 300): String {
        val salt = SubsonicAuthInterceptor.generateSalt()
        val token = SubsonicAuthInterceptor.md5(prefs.password + salt)
        val base = prefs.serverUrl.trimEnd('/')
        return "$base/rest/getCoverArt?id=$id&u=${prefs.username}&t=$token&s=$salt&v=1.16.1&c=neiro&f=json&size=$size"
    }

    suspend fun getCoverArtUrl(id: String, size: Int = 300): String {
        val prefs = preferences.prefsFlow.first()
        return buildCoverArtUrl(prefs, id, size)
    }
}
