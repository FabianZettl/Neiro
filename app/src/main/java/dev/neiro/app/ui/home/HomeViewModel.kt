package dev.neiro.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.api.models.SongDto
import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.data.repository.LastFmRepository
import dev.neiro.app.data.repository.MusicRepository
import dev.neiro.app.data.repository.PodcastRepository
import dev.neiro.app.player.PlayerController
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

sealed class HomeError {
    data object NoServer : HomeError()
    data class ApiError(val message: String?) : HomeError()
}

data class SectionContent(
    val config: HomeSectionConfig,
    val items: SectionItems = SectionItems.Albums(emptyList()),
    val isLoading: Boolean = true
)

data class HomeUiState(
    val sections: List<SectionContent> = emptyList(),
    val isLoading: Boolean = false,
    val error: HomeError? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val lastFmRepository: LastFmRepository,
    private val podcastRepository: PodcastRepository,
    private val preferences: NieroPreferences,
    private val playerController: PlayerController
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
                preferences.homeSectionsJson,
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
        // Auto-refresh "recently played" sections when a new album starts playing
        viewModelScope.launch {
            playerController.playerState
                .map { it.currentSong?.albumId }
                .distinctUntilChanged()
                .collect { albumId ->
                    if (albumId != null) {
                        viewModelScope.launch {
                            delay(4000) // give the server time to register the play
                            _refreshTrigger.value++
                        }
                    }
                }
        }
    }

    fun loadContent() {
        _refreshTrigger.value++
    }

    private fun findBestAlbumMatch(lfmName: String, lfmArtist: String, subsonicAlbums: List<dev.neiro.app.data.api.models.AlbumDto>): dev.neiro.app.data.api.models.AlbumDto? {
        val suffixRegex = Regex(
            """[\s\-–()\[\]]*(remastered|deluxe|expanded|anniversary|edition|version|bonus|track|re-?issue|re-?master)[\s\S]*$""",
            RegexOption.IGNORE_CASE
        )
        fun normalize(s: String) = s.replace(suffixRegex, "").trim().lowercase()

        val normLfmName = normalize(lfmName)
        val normLfmArtist = normalize(lfmArtist)

        // 1. Exact match
        subsonicAlbums.firstOrNull {
            it.name.lowercase() == lfmName.lowercase() &&
            (it.artist?.lowercase() ?: "") == lfmArtist.lowercase()
        }?.let { return it }

        // 2. Normalized name + artist match
        subsonicAlbums.firstOrNull {
            normalize(it.name) == normLfmName &&
            normalize(it.artist ?: "") == normLfmArtist
        }?.let { return it }

        // 3. Normalized name match (ignore artist — compilations, VA etc.)
        subsonicAlbums.firstOrNull {
            normalize(it.name) == normLfmName
        }?.let { return it }

        // 4. Contains match: one normalized name contains the other, artist roughly matches
        return subsonicAlbums.firstOrNull { sub ->
            val normSubName = normalize(sub.name)
            val normSubArtist = normalize(sub.artist ?: "")
            val artistMatch = normSubArtist.contains(normLfmArtist) || normLfmArtist.contains(normSubArtist)
            val nameMatch = normSubName.contains(normLfmName) || normLfmName.contains(normSubName)
            artistMatch && nameMatch
        }
    }

    private suspend fun loadSections(configs: List<HomeSectionConfig>) {
        val hasLastFm = lastFmRepository.isStatsConfigured()
        val enabledConfigs = configs.filter { it.enabled }

        // Show empty placeholders immediately — sections fill in as they load
        val placeholders = enabledConfigs.map { SectionContent(it) }
        _uiState.value = HomeUiState(sections = placeholders, isLoading = true, error = null)

        val mutex = Mutex()
        try {
            coroutineScope {
                enabledConfigs.forEachIndexed { index, config ->
                    launch {
                        val items = runCatching {
                            fetchSectionItems(config, hasLastFm)
                        }.getOrElse { SectionItems.Albums(emptyList()) }
                        mutex.withLock {
                            val updated = _uiState.value.sections.toMutableList()
                            if (index < updated.size) {
                                updated[index] = SectionContent(config, items, isLoading = false)
                                _uiState.value = _uiState.value.copy(sections = updated)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.value = HomeUiState(
                isLoading = false,
                error = HomeError.ApiError(e.message ?: e.javaClass.simpleName)
            )
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    private suspend fun fetchSectionItems(config: HomeSectionConfig, hasLastFm: Boolean): SectionItems {
        val useLastFm = hasLastFm && when (config.contentType) {
            SectionContentType.ALBUMS ->
                config.sortType == AlbumSortType.MOST_PLAYED ||
                config.dataSource == DataSource.LASTFM
            SectionContentType.ARTISTS ->
                config.artistSortType == ArtistSortType.MOST_PLAYED ||
                config.dataSource == DataSource.LASTFM
            SectionContentType.TRACKS -> true
            SectionContentType.LOVED_TRACKS -> true
            else -> false
        }

        return when (config.contentType) {
            SectionContentType.ALBUMS -> {
                if (useLastFm) {
                    val lfmAlbums = lastFmRepository
                        .getTopAlbums(config.lastFmPeriod.apiValue, config.size * 3)
                        ?.topAlbums?.albums.orEmpty()
                    val subsonicAlbums = runCatching { musicRepository.getAlbumsByType("alphabeticalByName", 500) }.getOrElse { emptyList() }
                    val matched = lfmAlbums.mapNotNull { lfm ->
                        val sub = findBestAlbumMatch(lfm.name, lfm.artist.name, subsonicAlbums)
                            ?: return@mapNotNull null
                        LastFmMatchedAlbum(
                            name = lfm.name, artistName = lfm.artist.name,
                            playCount = lfm.playCountInt,
                            subsonicId = sub.id,
                            coverArtUrl = sub.coverArtUrl ?: lfm.imageUrl
                        )
                    }.take(config.size)
                    SectionItems.LastFmTopAlbums(matched)
                } else {
                    SectionItems.Albums(runCatching { musicRepository.getAlbumsByFilter(config) }.getOrElse { emptyList() })
                }
            }
            SectionContentType.ARTISTS -> {
                if (useLastFm) {
                    val lfmArtists = lastFmRepository
                        .getTopArtists(config.lastFmPeriod.apiValue, config.size * 3)
                        ?.topArtists?.artists.orEmpty()
                    val subsonicArtists = runCatching { musicRepository.getAllArtists() }.getOrElse { emptyList() }
                    val artistMap = subsonicArtists.associateBy { it.name.lowercase() }
                    val matched = lfmArtists.mapNotNull { lfm ->
                        val sub = artistMap[lfm.name.lowercase()]
                            ?: return@mapNotNull null
                        LastFmMatchedArtist(name = lfm.name, playCount = lfm.playCountInt, subsonicId = sub.id, coverArtUrl = sub.coverArtUrl)
                    }.take(config.size)
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
                            ArtistSortType.ALBUM_COUNT  -> list.sortedByDescending { it.albumCount }
                            ArtistSortType.RANDOM       -> list.shuffled()
                            else -> list
                        }
                        list.take(config.size)
                    }.getOrElse { emptyList() }
                    SectionItems.Artists(artists)
                }
            }
            SectionContentType.PLAYLISTS -> {
                SectionItems.Playlists(runCatching { musicRepository.getPlaylists() }.getOrElse { emptyList() })
            }
            SectionContentType.TRACKS -> {
                if (useLastFm) {
                    val lfmTracks = lastFmRepository
                        .getTopTracks(config.lastFmPeriod.apiValue, config.size * 3)
                        ?.topTracks?.tracks.orEmpty()
                        .take(config.size * 2)
                    // Parallel search, max 5 concurrent requests to avoid server overload
                    val semaphore = Semaphore(5)
                    val matched = coroutineScope {
                        lfmTracks.map { lfm ->
                            async {
                                semaphore.withPermit {
                                    val songs = runCatching { musicRepository.searchSongs(lfm.name, lfm.artist.name) }.getOrElse { emptyList() }
                                    val best = songs.firstOrNull { it.title.equals(lfm.name, ignoreCase = true) } ?: songs.firstOrNull()
                                        ?: return@withPermit null
                                    LastFmMatchedTrack(
                                        name = lfm.name, artistName = lfm.artist.name,
                                        playCount = lfm.playCountLong,
                                        subsonicId = best.id,
                                        albumId = best.albumId,
                                        coverArtUrl = best.coverArtUrl
                                    )
                                }
                            }
                        }.awaitAll().filterNotNull().take(config.size)
                    }
                    SectionItems.LastFmTopTracks(matched)
                } else {
                    SectionItems.LastFmTopTracks(emptyList())
                }
            }
            SectionContentType.LOVED_TRACKS -> {
                if (hasLastFm) {
                    val lovedTracks = lastFmRepository.getLovedTracksFull(50)
                    // Parallel search, max 5 concurrent requests to avoid server overload
                    val semaphore = Semaphore(5)
                    val matched = coroutineScope {
                        lovedTracks.map { lfm ->
                            async {
                                semaphore.withPermit {
                                    val songs = runCatching { musicRepository.searchSongs(lfm.name, lfm.artist.name) }.getOrElse { emptyList() }
                                    val best = songs.firstOrNull { it.title.equals(lfm.name, ignoreCase = true) } ?: songs.firstOrNull()
                                    LastFmMatchedTrack(
                                        name = lfm.name, artistName = lfm.artist.name,
                                        playCount = 0L,
                                        subsonicId = best?.id,
                                        albumId = best?.albumId,
                                        coverArtUrl = best?.coverArtUrl ?: lfm.imageUrl
                                    )
                                }
                            }
                        }.awaitAll().filter { it.subsonicId != null }
                    }
                    SectionItems.LastFmTopTracks(matched)
                } else {
                    SectionItems.LastFmTopTracks(emptyList())
                }
            }
            SectionContentType.GENRES -> {
                SectionItems.Genres(runCatching { musicRepository.getGenres() }.getOrElse { emptyList() })
            }
            SectionContentType.PODCASTS -> {
                SectionItems.Podcasts(runCatching { podcastRepository.getLatestEpisodes(config.size) }.getOrElse { emptyList() })
            }
        }
    }

    /** Play a single matched track (from Top Tracks list). Loads the full album queue via albumId if available. */
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
            // Fallback: build a minimal SongDto and play just this track
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

    fun playPodcastEpisode(item: dev.neiro.app.data.api.models.PodcastEpisodeWithPodcast) {
        viewModelScope.launch {
            playerController.playPodcastEpisode(item.episode, item.subscription)
        }
    }
}
