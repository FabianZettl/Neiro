package dev.neiro.desktop.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neiro.desktop.data.api.models.SongDto
import dev.neiro.desktop.data.prefs.DesktopPreferences
import dev.neiro.desktop.data.repository.LastFmRepository
import dev.neiro.desktop.data.repository.MusicRepository
import dev.neiro.desktop.player.DesktopPlayerController
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed class HomeError {
    object NoServer : HomeError()
    data class ApiError(val message: String?) : HomeError()
}

data class SectionContent(
    val config: HomeSectionConfig,
    val items: SectionItems = SectionItems.Albums(emptyList())
)

data class HomeUiState(
    val sections: List<SectionContent> = emptyList(),
    val isLoading: Boolean = false,
    val error: HomeError? = null
)

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val lastFmRepository: LastFmRepository,
    private val preferences: DesktopPreferences,
    private val playerController: DesktopPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(
                preferences.prefsFlow
                    .map { Triple(it.serverUrl, it.username, it.password) }
                    .distinctUntilChanged(),
                preferences.prefsFlow.map { it.homeSectionsJson }.distinctUntilChanged(),
                _refreshTrigger
            ) { serverTriple, sectionsJson, _ -> serverTriple to sectionsJson }
                .collectLatest { (serverTriple, sectionsJson) ->
                    val (serverUrl, _, _) = serverTriple
                    if (serverUrl.isBlank()) {
                        _uiState.value = HomeUiState(error = HomeError.NoServer)
                        return@collectLatest
                    }
                    delay(150)
                    val configs = sectionsJson?.toHomeSectionConfigs() ?: DEFAULT_HOME_SECTIONS
                    loadSections(configs)
                }
        }
    }

    fun loadContent() {
        _refreshTrigger.value++
    }

    private fun findBestAlbumMatch(
        lfmName: String,
        lfmArtist: String,
        subsonicAlbums: List<dev.neiro.desktop.data.api.models.AlbumDto>
    ): dev.neiro.desktop.data.api.models.AlbumDto? {
        val suffixRegex = Regex(
            """[\s\-\u2013()\[\]]*(remastered|deluxe|expanded|anniversary|edition|version|bonus|track|re-?issue|re-?master)[\s\S]*$""",
            RegexOption.IGNORE_CASE
        )
        fun normalize(s: String) = s.replace(suffixRegex, "").trim().lowercase()

        val normLfmName = normalize(lfmName)
        val normLfmArtist = normalize(lfmArtist)

        subsonicAlbums.firstOrNull {
            it.name.lowercase() == lfmName.lowercase() &&
                    (it.artist?.lowercase() ?: "") == lfmArtist.lowercase()
        }?.let { return it }

        subsonicAlbums.firstOrNull {
            normalize(it.name) == normLfmName &&
                    normalize(it.artist ?: "") == normLfmArtist
        }?.let { return it }

        subsonicAlbums.firstOrNull {
            normalize(it.name) == normLfmName
        }?.let { return it }

        return subsonicAlbums.firstOrNull { sub ->
            val normSubName = normalize(sub.name)
            val normSubArtist = normalize(sub.artist ?: "")
            val artistMatch = normSubArtist.contains(normLfmArtist) || normLfmArtist.contains(normSubArtist)
            val nameMatch = normSubName.contains(normLfmName) || normLfmName.contains(normSubName)
            artistMatch && nameMatch
        }
    }

    private suspend fun loadSections(configs: List<HomeSectionConfig>) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val hasLastFm = lastFmRepository.isStatsConfigured()
            val enabledConfigs = configs.filter { it.enabled }
            coroutineScope {
                val sections = enabledConfigs.map { config ->
                    async {
                        val useLastFm = hasLastFm && when (config.contentType) {
                            SectionContentType.ALBUMS ->
                                config.sortType == AlbumSortType.MOST_PLAYED ||
                                        config.sortType == AlbumSortType.RECENTLY_PLAYED ||
                                        config.dataSource == DataSource.LASTFM
                            SectionContentType.ARTISTS ->
                                config.artistSortType == ArtistSortType.MOST_PLAYED ||
                                        config.artistSortType == ArtistSortType.RECENTLY_PLAYED ||
                                        config.dataSource == DataSource.LASTFM
                            SectionContentType.TRACKS -> true
                            else -> false
                        }

                        val items: SectionItems = when (config.contentType) {
                            SectionContentType.ALBUMS -> {
                                if (useLastFm) {
                                    val lfmAlbums = lastFmRepository
                                        .getTopAlbums(config.lastFmPeriod.apiValue, config.size)
                                        ?.topAlbums?.albums.orEmpty()
                                    val subsonicAlbums = runCatching {
                                        musicRepository.getAlbumsByType("alphabeticalByName", 500)
                                    }.getOrElse { emptyList() }
                                    val matched = lfmAlbums.map { lfm ->
                                        val sub = findBestAlbumMatch(lfm.name, lfm.artist.name, subsonicAlbums)
                                        LastFmMatchedAlbum(
                                            name = lfm.name,
                                            artistName = lfm.artist.name,
                                            playCount = lfm.playCountInt,
                                            subsonicId = sub?.id,
                                            coverArtUrl = sub?.coverArtUrl ?: lfm.imageUrl
                                        )
                                    }
                                    SectionItems.LastFmTopAlbums(matched)
                                } else {
                                    SectionItems.Albums(
                                        runCatching { musicRepository.getAlbumsByFilter(config) }
                                            .getOrElse { emptyList() }
                                    )
                                }
                            }
                            SectionContentType.ARTISTS -> {
                                if (useLastFm) {
                                    val lfmArtists = lastFmRepository
                                        .getTopArtists(config.lastFmPeriod.apiValue, config.size)
                                        ?.topArtists?.artists.orEmpty()
                                    val subsonicArtists = runCatching { musicRepository.getAllArtists() }
                                        .getOrElse { emptyList() }
                                    val artistMap = subsonicArtists.associateBy { it.name.lowercase() }
                                    val matched = lfmArtists.map { lfm ->
                                        val sub = artistMap[lfm.name.lowercase()]
                                        LastFmMatchedArtist(
                                            name = lfm.name,
                                            playCount = lfm.playCountInt,
                                            subsonicId = sub?.id,
                                            coverArtUrl = sub?.coverArtUrl
                                        )
                                    }
                                    SectionItems.LastFmTopArtists(matched)
                                } else {
                                    val artists = runCatching {
                                        var list = musicRepository.getAllArtists()
                                        if (!config.artistGenre.isNullOrBlank()) {
                                            val ids = musicRepository.getArtistIdsByGenre(config.artistGenre)
                                            list = list.filter { it.id in ids }
                                        }
                                        list = when (config.artistSortType) {
                                            ArtistSortType.ALPHABETICAL -> list.sortedBy { it.name.lowercase() }
                                            ArtistSortType.ALBUM_COUNT -> list.sortedByDescending { it.albumCount }
                                            ArtistSortType.RANDOM -> list.shuffled()
                                            else -> list
                                        }
                                        list.take(config.size)
                                    }.getOrElse { emptyList() }
                                    SectionItems.Artists(artists)
                                }
                            }
                            SectionContentType.PLAYLISTS -> {
                                SectionItems.Playlists(
                                    runCatching { musicRepository.getPlaylists() }.getOrElse { emptyList() }
                                )
                            }
                            SectionContentType.TRACKS -> {
                                if (useLastFm) {
                                    val lfmTracks = lastFmRepository
                                        .getTopTracks(config.lastFmPeriod.apiValue, config.size)
                                        ?.topTracks?.tracks.orEmpty()
                                    val matched = lfmTracks.map { lfm ->
                                        val songs = runCatching {
                                            musicRepository.searchSongs(lfm.name, lfm.artist.name)
                                        }.getOrElse { emptyList() }
                                        val best = songs.firstOrNull {
                                            it.title.equals(lfm.name, ignoreCase = true)
                                        } ?: songs.firstOrNull()
                                        LastFmMatchedTrack(
                                            name = lfm.name,
                                            artistName = lfm.artist.name,
                                            playCount = lfm.playCountLong,
                                            subsonicId = best?.id,
                                            albumId = best?.albumId,
                                            coverArtUrl = best?.coverArtUrl
                                        )
                                    }
                                    SectionItems.LastFmTopTracks(matched)
                                } else {
                                    SectionItems.LastFmTopTracks(emptyList())
                                }
                            }
                            SectionContentType.GENRES -> {
                                SectionItems.Genres(
                                    runCatching { musicRepository.getGenres() }.getOrElse { emptyList() }
                                )
                            }
                        }
                        SectionContent(config, items)
                    }
                }.map { it.await() }
                _uiState.value = HomeUiState(sections = sections, isLoading = false)
            }
        } catch (e: Exception) {
            _uiState.value = HomeUiState(
                isLoading = false,
                error = HomeError.ApiError(e.message ?: e.javaClass.simpleName)
            )
        }
    }

    fun playTopTrack(track: LastFmMatchedTrack) {
        val songId = track.subsonicId ?: return
        viewModelScope.launch {
            val albumId = track.albumId
            if (albumId != null) {
                val songs = runCatching { musicRepository.getAlbum(albumId).song }.getOrElse { emptyList() }
                val index = songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
                if (songs.isNotEmpty()) {
                    playerController.playTrack(songs[index], songs, index)
                    return@launch
                }
            }
            val song = SongDto(
                id = songId,
                title = track.name,
                artist = track.artistName,
                coverArtUrl = track.coverArtUrl,
                duration = 0
            )
            playerController.playTrack(song, listOf(song), 0)
        }
    }
}
