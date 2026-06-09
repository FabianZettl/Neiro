package dev.neiro.app.data.api.models

import com.google.gson.annotations.SerializedName

// ── Shared types ────────────────────────────────────────────────────────────

data class SubsonicError(
    val code: Int = 0,
    val message: String = ""
)

data class AlbumDto(
    val id: String = "",
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val coverArtUrl: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val song: List<SongDto> = emptyList(),
    val starred: String? = null,
    val playCount: Int? = null,
    val played: String? = null,   // ISO 8601 datetime of last play, e.g. "2024-03-15T10:30:00"
)

data class SongDto(
    val id: String = "",
    val title: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val coverArtUrl: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val year: Int? = null,
    val bitRate: Int? = null,
    val size: Long? = null,
    val discNumber: Int? = null
)

data class ArtistDto(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val coverArtUrl: String? = null,
    val albumCount: Int = 0
)

data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val entry: List<SongDto> = emptyList()
)

data class SearchResult3Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList()
)

data class LibraryStats(
    val artistCount: Int = 0,
    val albumCount: Int = 0
)

// ── getArtist ────────────────────────────────────────────────────────────────

data class ArtistWithAlbumsDto(
    val id: String = "",
    val name: String = "",
    val albumCount: Int = 0,
    val coverArt: String? = null,
    val album: List<AlbumDto>? = null
)

data class ArtistDetailResponseBody(
    val status: String = "",
    val artist: ArtistWithAlbumsDto = ArtistWithAlbumsDto(),
    val error: SubsonicError? = null
)

data class ArtistDetailApiResponse(
    @SerializedName("subsonic-response")
    val response: ArtistDetailResponseBody
)

// ── getArtists ───────────────────────────────────────────────────────────────

data class ArtistIndexEntry(
    val id: String = "",
    val name: String = "",
    val albumCount: Int = 0,
    val coverArt: String? = null
)

data class ArtistIndex(
    val name: String = "",
    val artist: List<ArtistIndexEntry> = emptyList()
)

data class ArtistsContainer(
    val index: List<ArtistIndex> = emptyList(),
    val ignoredArticles: String = ""
)

data class ArtistsResponseBody(
    val status: String = "",
    val artists: ArtistsContainer = ArtistsContainer(),
    val error: SubsonicError? = null
)

data class ArtistsApiResponse(
    @SerializedName("subsonic-response")
    val response: ArtistsResponseBody
)

// ── Ping ────────────────────────────────────────────────────────────────────

data class PingApiResponse(
    @SerializedName("subsonic-response")
    val response: PingResponseBody
)

data class PingResponseBody(
    val status: String = "",
    val version: String = "",
    val error: SubsonicError? = null
)

// ── getAlbumList2 ────────────────────────────────────────────────────────────

data class AlbumList2ApiResponse(
    @SerializedName("subsonic-response")
    val response: AlbumList2ResponseBody
)

data class AlbumList2ResponseBody(
    val status: String = "",
    val albumList2: AlbumList2Container? = null,
    val error: SubsonicError? = null
)

data class AlbumList2Container(
    val album: List<AlbumDto>? = null
)

// ── getAlbum ────────────────────────────────────────────────────────────────

data class AlbumApiResponse(
    @SerializedName("subsonic-response")
    val response: AlbumResponseBody
)

data class AlbumResponseBody(
    val status: String = "",
    val album: AlbumDto = AlbumDto(),
    val error: SubsonicError? = null
)

// ── getSong ─────────────────────────────────────────────────────────────────

data class SongApiResponse(
    @SerializedName("subsonic-response")
    val response: SongResponseBody
)

data class SongResponseBody(
    val status: String = "",
    val song: SongDto = SongDto(),
    val error: SubsonicError? = null
)

// ── search3 ─────────────────────────────────────────────────────────────────

data class Search3ApiResponse(
    @SerializedName("subsonic-response")
    val response: Search3ResponseBody
)

data class Search3ResponseBody(
    val status: String = "",
    val searchResult3: SearchResult3Dto = SearchResult3Dto(),
    val error: SubsonicError? = null
)

// ── getPlaylists ─────────────────────────────────────────────────────────────

data class PlaylistsApiResponse(
    @SerializedName("subsonic-response")
    val response: PlaylistsResponseBody
)

data class PlaylistsResponseBody(
    val status: String = "",
    val playlists: PlaylistsContainer = PlaylistsContainer(),
    val error: SubsonicError? = null
)

data class PlaylistsContainer(
    val playlist: List<PlaylistDto> = emptyList()
)

// ── getPlaylist ──────────────────────────────────────────────────────────────

data class PlaylistApiResponse(
    @SerializedName("subsonic-response")
    val response: PlaylistResponseBody
)

data class PlaylistResponseBody(
    val status: String = "",
    val playlist: PlaylistDto = PlaylistDto(),
    val error: SubsonicError? = null
)

// ── getArtistInfo2 ───────────────────────────────────────────────────────────

data class ArtistInfoDto(
    val biography: String? = null,
    val musicBrainzId: String? = null,
    val lastFmUrl: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
    @SerializedName("similarArtist") val similarArtists: List<SimilarArtistDto> = emptyList()
)

data class SimilarArtistDto(
    val id: String = "",
    val name: String = ""
)

data class ArtistInfo2ResponseBody(
    val status: String = "",
    val artistInfo2: ArtistInfoDto = ArtistInfoDto(),
    val error: SubsonicError? = null
)

data class ArtistInfo2ApiResponse(
    @SerializedName("subsonic-response")
    val response: ArtistInfo2ResponseBody
)

// ── getGenres ────────────────────────────────────────────────────────────────

data class GenreDto(
    val value: String = "",
    val songCount: Int = 0,
    val albumCount: Int = 0
)

data class GenresData(@SerializedName("genre") val genres: List<GenreDto>? = null)

data class GenresResponseBody(
    val genres: GenresData? = null,
    val status: String = "",
    val version: String = ""
)

data class GenresApiResponse(
    @SerializedName("subsonic-response") val response: GenresResponseBody? = null
)

// ── getSimilarSongs2 ──────────────────────────────────────────────────────────

data class SimilarSongsContainer(
    @SerializedName("song") val songs: List<SongDto> = emptyList()
)

data class SimilarSongsResponseBody(
    val status: String = "",
    val similarSongs2: SimilarSongsContainer = SimilarSongsContainer(),
    val error: SubsonicError? = null
)

data class SimilarSongsApiResponse(
    @SerializedName("subsonic-response") val response: SimilarSongsResponseBody
)
