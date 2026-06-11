package dev.neiro.desktop.ui.home

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.neiro.desktop.data.api.models.AlbumDto
import dev.neiro.desktop.data.api.models.ArtistDto
import dev.neiro.desktop.data.api.models.PlaylistDto
import java.util.UUID

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
    WEEK("Last 7 days", "7day"),
    MONTH("Last 30 days", "1month"),
    THREE_MONTHS("Last 90 days", "3month"),
    SIX_MONTHS("Last 180 days", "6month"),
    YEAR("Last 365 days", "12month"),
    OVERALL("All time", "overall")
}

enum class AlbumSortType(val displayName: String, val apiType: String) {
    RECENTLY_PLAYED("Recently Played", "recent"),
    RECENTLY_ADDED("Recently Added", "newest"),
    MOST_PLAYED("Most Played", "frequent"),
    ALPHABETICAL("A-Z by Name", "alphabeticalByName"),
    BY_ARTIST("A-Z by Artist", "alphabeticalByArtist"),
    RANDOM("Random", "random"),
    STARRED("Starred", "starred"),
    HIGHEST_RATED("Highest Rated", "highest"),
}

enum class ArtistSortType(val displayName: String) {
    ALPHABETICAL("A-Z"),
    ALBUM_COUNT("Most Albums"),
    MOST_PLAYED("Most Played"),
    RECENTLY_PLAYED("Recently Played"),
    RANDOM("Random"),
}

enum class ItemShape(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    PORTRAIT("Portrait"),
    WIDE("Wide"),
}

enum class ItemSize(val label: String) {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large"),
}

data class LastFmMatchedArtist(
    val name: String,
    val playCount: Long,
    val subsonicId: String?,
    val coverArtUrl: String?
)

data class LastFmMatchedAlbum(
    val name: String,
    val artistName: String,
    val playCount: Long,
    val subsonicId: String?,
    val coverArtUrl: String?
)

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
        is Albums -> items.isEmpty()
        is Playlists -> items.isEmpty()
        is Artists -> items.isEmpty()
        is LastFmTopArtists -> items.isEmpty()
        is LastFmTopAlbums -> items.isEmpty()
        is LastFmTopTracks -> items.isEmpty()
        is Genres -> items.isEmpty()
    }
}

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
    val genre: String? = null,
    val playedInLastDays: Int? = null,
    val minPlayCount: Int? = null,
    val starredOnly: Boolean = false,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val artistSortType: ArtistSortType = ArtistSortType.ALPHABETICAL,
    val artistGenre: String? = null,
    val lastFmPeriod: LastFmPeriod = LastFmPeriod.MONTH,
    val dataSource: DataSource = DataSource.SUBSONIC
)

val DEFAULT_HOME_SECTIONS = listOf(
    HomeSectionConfig("d1", "Recently Played", SectionContentType.ALBUMS, AlbumSortType.RECENTLY_PLAYED, SectionLayout.SHELF, 20, true),
    HomeSectionConfig("d2", "Recently Added", SectionContentType.ALBUMS, AlbumSortType.RECENTLY_ADDED, SectionLayout.SHELF, 20, true),
    HomeSectionConfig("d3", "Most Played", SectionContentType.ALBUMS, AlbumSortType.MOST_PLAYED, SectionLayout.SHELF, 20, true),
)

fun ItemShape.artAspectRatio(): Float = when (this) {
    ItemShape.PORTRAIT -> 2f / 3f
    ItemShape.WIDE     -> 16f / 9f
    else               -> 1f
}

fun ItemShape.clipShape(): Shape = when (this) {
    ItemShape.CIRCLE  -> CircleShape
    ItemShape.ROUNDED -> RoundedCornerShape(12.dp)
    else              -> RoundedCornerShape(8.dp)
}

fun ItemSize.cardWidth(): Dp = when (this) {
    ItemSize.SMALL  -> 120.dp
    ItemSize.MEDIUM -> 160.dp
    ItemSize.LARGE  -> 220.dp
}

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

fun String.toHomeSectionConfigs(): List<HomeSectionConfig> =
    runCatching {
        val serialized: List<SectionConfigJson> = gson.fromJson(this, jsonListType)
        serialized.map { s ->
            val (migratedContentType, migratedDataSource) = when (s.contentType) {
                "LASTFM_ARTISTS" -> "ARTISTS" to "LASTFM"
                "LASTFM_ALBUMS" -> "ALBUMS" to "LASTFM"
                else -> s.contentType to s.dataSource
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
                itemShape = ItemShape.entries.find { it.name == s.itemShape } ?: ItemShape.ROUNDED,
                itemSize = ItemSize.entries.find { it.name == s.itemSize } ?: ItemSize.MEDIUM,
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
