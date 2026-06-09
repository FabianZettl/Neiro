package dev.neiro.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.data.repository.LastFmRepository
import dev.neiro.app.data.repository.MusicRepository
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
import javax.inject.Inject

sealed class HomeError {
    data object NoServer : HomeError()
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val lastFmRepository: LastFmRepository,
    private val preferences: NieroPreferences
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
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val enabledConfigs = configs.filter { it.enabled }
            coroutineScope {
                val sections = enabledConfigs.map { config ->
                    async {
                        val items: SectionItems = when (config.contentType) {
                            SectionContentType.ALBUMS -> {
                                val albums = runCatching {
                                    musicRepository.getAlbumsByFilter(config)
                                }.getOrElse { emptyList() }
                                SectionItems.Albums(albums)
                            }
                            SectionContentType.PLAYLISTS -> {
                                val playlists = runCatching {
                                    musicRepository.getPlaylists()
                                }.getOrElse { emptyList() }
                                SectionItems.Playlists(playlists)
                            }
                            SectionContentType.ARTISTS -> {
                                val artists = runCatching {
                                    var list = musicRepository.getAllArtists()
                                    if (!config.artistGenre.isNullOrBlank()) {
                                        val artistIds = musicRepository.getArtistIdsByGenre(config.artistGenre)
                                        list = list.filter { it.id in artistIds }
                                    }
                                    list = when (config.artistSortType) {
                                        ArtistSortType.ALPHABETICAL -> list.sortedBy { it.name.lowercase() }
                                        ArtistSortType.ALBUM_COUNT  -> list.sortedByDescending { it.albumCount }
                                        ArtistSortType.RANDOM       -> list.shuffled()
                                    }
                                    list.take(config.size)
                                }.getOrElse { emptyList() }
                                SectionItems.Artists(artists)
                            }
                            SectionContentType.LASTFM_ARTISTS -> {
                                val lfmArtists = lastFmRepository
                                    .getTopArtists(config.lastFmPeriod.apiValue, config.size)
                                    ?.topArtists?.artists.orEmpty()
                                // Cross-reference with Subsonic library for images + navigation
                                val subsonicArtists = runCatching { musicRepository.getAllArtists() }.getOrElse { emptyList() }
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
                            }
                            SectionContentType.LASTFM_ALBUMS -> {
                                val lfmAlbums = lastFmRepository
                                    .getTopAlbums(config.lastFmPeriod.apiValue, config.size)
                                    ?.topAlbums?.albums.orEmpty()
                                // Cross-reference: fetch 500 albums alphabetically and match by name+artist
                                val subsonicAlbums = runCatching { musicRepository.getAlbumsByType("alphabeticalByName", 500) }.getOrElse { emptyList() }
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
}
