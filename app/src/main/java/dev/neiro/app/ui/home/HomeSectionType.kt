package dev.neiro.app.ui.home

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.neiro.app.data.api.models.AlbumDto
import dev.neiro.app.data.api.models.ArtistDto
import dev.neiro.app.data.api.models.PlaylistDto
import java.util.UUID

// ── Layout & Content ─────────────────────────────────────────────────────────

enum class SectionLayout(val label: String) {
    SHELF("Shelf"),
    GRID("Grid"),
    LIST("List")
}

enum class SectionContentType(val displayName: String) {
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    TRACKS("Tracks"),
    GENRES("Genres")
}

enum class DataSource(val displayName: String) {
    SUBSONIC("Library"),
    LASTFM("Last.fm")
}

enum class LastFmPeriod(val displayName: String, val apiValue: String) {
    WEEK("7 days", "7day"),
    MONTH("1 month", "1month"),
    THREE_MONTHS("3 months", "3month"),
    SIX_MONTHS("6 months", "6month"),
    YEAR("12 months", "12month"),
    OVERALL("All time", "overall")
}

// ── Sort types (maps to OpenSubsonic getAlbumList2 ?type= param) ─────────────

enum class AlbumSortType(val displayName: String, val apiType: String) {
    RECENTLY_PLAYED("Recently Played", "recent"),
    RECENTLY_ADDED("Recently Added", "newest"),
    MOST_PLAYED("Most Played", "frequent"),
    ALPHABETICAL("A–Z by Name", "alphabeticalByName"),
    BY_ARTIST("A–Z by Artist", "alphabeticalByArtist"),
    RANDOM("Random", "random"),
    STARRED("Starred", "starred"),
    HIGHEST_RATED("Highest Rated", "highest"),
}

enum class ArtistSortType(val displayName: String) {
    ALPHABETICAL("A–Z"),
    ALBUM_COUNT("Most Albums"),
    MOST_PLAYED("Most Played"),
    RECENTLY_PLAYED("Recently Played"),
    RANDOM("Random"),
}

// ── Item shape ────────────────────────────────────────────────────────────────

enum class ItemShape(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    PORTRAIT("Portrait"),
    WIDE("Wide"),
}

// ── Item size ─────────────────────────────────────────────────────────────────

enum class ItemSize(val label: String) {
    SMALL("Small"),    // ~4-5 per row in shelf, 3 cols in grid
    MEDIUM("Medium"),  // ~2-3 per row in shelf, 2 cols in grid  [default]
    LARGE("Large"),    // 1 full-width hero card
}

// ── Section items (sealed) ───────────────────────────────────────────────────

/** LastFM artist cross-referenced with Subsonic library. subsonicId/coverArtUrl are null if not found locally. */
data class LastFmMatchedArtist(
    val name: String,
    val playCount: Long,
    val subsonicId: String?,
    val coverArtUrl: String?
)

/** LastFM album cross-referenced with Subsonic library. subsonicId is null if not found locally. */
data class LastFmMatchedAlbum(
    val name: String,
    val artistName: String,
    val playCount: Long,
    val subsonicId: String?,
    val coverArtUrl: String?
)

/** LastFM track cross-referenced with Subsonic library. subsonicId is null if not found locally. */
data class LastFmMatchedTrack(
    val name: String,
    val artistName: String,
    val playCount: Long,
    val subsonicId: String?,
    val albumId: String?,
    val coverArtUrl: String?
)

data class GenreItem(val name: String, val songCount: Int, val albumCount: Int)

sealed class SectionItems {
    data class Albums(val items: List<AlbumDto>) : SectionItems()
    data class Playlists(val items: List<PlaylistDto>) : SectionItems()
    data class Artists(val items: List<ArtistDto>) : SectionItems()
    data class LastFmTopArtists(val items: List<LastFmMatchedArtist>) : SectionItems()
    data class LastFmTopAlbums(val items: List<LastFmMatchedAlbum>) : SectionItems()
    data class LastFmTopTracks(val items: List<LastFmMatchedTrack>) : SectionItems()
    data class Genres(val items: List<GenreItem>) : SectionItems()
    fun isEmpty() = when (this) {
        is Albums           -> items.isEmpty()
        is Playlists        -> items.isEmpty()
        is Artists          -> items.isEmpty()
        is LastFmTopArtists -> items.isEmpty()
        is LastFmTopAlbums  -> items.isEmpty()
        is LastFmTopTracks  -> items.isEmpty()
        is Genres           -> items.isEmpty()
    }
}

// ── Config ───────────────────────────────────────────────────────────────────

data class HomeSectionConfig(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Section",
    val contentType: SectionContentType = SectionContentType.ALBUMS,
    val sortType: AlbumSortType = AlbumSortType.RECENTLY_ADDED,
    val layout: SectionLayout = SectionLayout.SHELF,
    val size: Int = 20,
    val enabled: Boolean = true,
    val itemShape: ItemShape = ItemShape.ROUNDED,
    val itemSize: ItemSize = ItemSize.MEDIUM,
    // Album-specific filters
    val genre: String? = null,
    val playedInLastDays: Int? = null,
    val minPlayCount: Int? = null,
    val starredOnly: Boolean = false,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    // Artist-specific
    val artistSortType: ArtistSortType = ArtistSortType.ALPHABETICAL,
    val artistGenre: String? = null,
    // Last.fm-specific
    val lastFmPeriod: LastFmPeriod = LastFmPeriod.MONTH,
    val dataSource: DataSource = DataSource.SUBSONIC
)

val DEFAULT_HOME_SECTIONS = listOf(
    HomeSectionConfig("d1", "Recently Played",  SectionContentType.ALBUMS, AlbumSortType.RECENTLY_PLAYED, SectionLayout.SHELF, 20, true),
    HomeSectionConfig("d2", "Recently Added",   SectionContentType.ALBUMS, AlbumSortType.RECENTLY_ADDED,  SectionLayout.SHELF, 20, true),
    HomeSectionConfig("d3", "Most Played",      SectionContentType.ALBUMS, AlbumSortType.MOST_PLAYED,     SectionLayout.SHELF, 20, true),
    HomeSectionConfig("d4", "Playlists",        SectionContentType.PLAYLISTS, AlbumSortType.RECENTLY_ADDED, SectionLayout.SHELF, 20, false),
    HomeSectionConfig("d5", "Artists",          SectionContentType.ARTISTS,   AlbumSortType.RECENTLY_ADDED, SectionLayout.SHELF, 20, false),
)

// ── JSON serialisation ───────────────────────────────────────────────────────

private data class SectionConfigJson(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Section",
    val contentType: String = "ALBUMS",
    val sortType: String = "RECENTLY_ADDED",
    val layout: String = "SHELF",
    val size: Int = 20,
    val enabled: Boolean = true,
    val itemShape: String = "ROUNDED",
    val itemSize: String = "MEDIUM",
    val genre: String? = null,
    val playedInLastDays: Int? = null,
    val minPlayCount: Int? = null,
    val starredOnly: Boolean = false,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val artistSortType: String = "ALPHABETICAL",
    val artistGenre: String? = null,
    val lastFmPeriod: String = "MONTH",
    val dataSource: String = "SUBSONIC"
)

private val gson = Gson()
private val jsonListType = object : TypeToken<List<SectionConfigJson>>() {}.type

fun List<HomeSectionConfig>.toJson(): String = gson.toJson(map { c ->
    SectionConfigJson(
        id = c.id, title = c.title,
        contentType = c.contentType.name, sortType = c.sortType.name,
        layout = c.layout.name, size = c.size, enabled = c.enabled,
        itemShape = c.itemShape.name,
        itemSize = c.itemSize.name,
        genre = c.genre, playedInLastDays = c.playedInLastDays,
        minPlayCount = c.minPlayCount, starredOnly = c.starredOnly,
        yearFrom = c.yearFrom, yearTo = c.yearTo,
        artistSortType = c.artistSortType.name, artistGenre = c.artistGenre,
        lastFmPeriod = c.lastFmPeriod.name,
        dataSource = c.dataSource.name
    )
})

fun String.toHomeSectionConfigs(): List<HomeSectionConfig> =
    runCatching {
        val serialized: List<SectionConfigJson> = gson.fromJson(this, jsonListType)
        serialized.map { s ->
            // Migration: old LASTFM_ARTISTS/LASTFM_ALBUMS → new contentType + dataSource
            val (migratedContentType, migratedDataSource) = when (s.contentType) {
                "LASTFM_ARTISTS" -> "ARTISTS" to "LASTFM"
                "LASTFM_ALBUMS"  -> "ALBUMS"  to "LASTFM"
                else             -> s.contentType to s.dataSource
            }
            HomeSectionConfig(
                id = s.id,
                title = s.title,
                contentType = SectionContentType.entries.find { it.name == migratedContentType }
                    ?: SectionContentType.ALBUMS,
                sortType = AlbumSortType.entries.find { it.name == s.sortType }
                    ?: AlbumSortType.RECENTLY_ADDED,
                layout = SectionLayout.entries.find { it.name == s.layout }
                    ?: SectionLayout.SHELF,
                size = s.size,
                enabled = s.enabled,
                itemShape = ItemShape.entries.find { it.name == s.itemShape }
                    ?: ItemShape.ROUNDED,
                itemSize = ItemSize.entries.find { it.name == s.itemSize }
                    ?: ItemSize.MEDIUM,
                genre = s.genre,
                playedInLastDays = s.playedInLastDays,
                minPlayCount = s.minPlayCount,
                starredOnly = s.starredOnly,
                yearFrom = s.yearFrom,
                yearTo = s.yearTo,
                artistSortType = ArtistSortType.entries.find { it.name == s.artistSortType }
                    ?: ArtistSortType.ALPHABETICAL,
                artistGenre = s.artistGenre,
                lastFmPeriod = LastFmPeriod.entries.find { it.name == s.lastFmPeriod }
                    ?: LastFmPeriod.MONTH,
                dataSource = DataSource.entries.find { it.name == migratedDataSource }
                    ?: DataSource.SUBSONIC
            )
        }
    }.getOrElse { DEFAULT_HOME_SECTIONS }
