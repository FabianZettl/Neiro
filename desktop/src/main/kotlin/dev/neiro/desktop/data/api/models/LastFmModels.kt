package dev.neiro.desktop.data.api.models

import com.google.gson.annotations.SerializedName

data class LastFmImage(
    @SerializedName("#text") val url: String = "",
    @SerializedName("size") val size: String = ""
)

fun List<LastFmImage>.bestUrl(): String? =
    sortedByDescending { it.size.length }.firstOrNull { it.url.isNotBlank() }?.url

data class LastFmArtist(
    @SerializedName("name") val name: String = "",
    @SerializedName("playcount") val playCount: String = "0",
    @SerializedName("listeners") val listeners: String = "0",
    @SerializedName("rank") val rank: String? = null,
    @SerializedName("url") val url: String = "",
    @SerializedName("image") val images: List<LastFmImage> = emptyList()
) {
    val playCountInt get() = playCount.toLongOrNull() ?: 0L
    val listenersInt get() = listeners.toLongOrNull() ?: 0L
    val imageUrl get() = images.bestUrl()
}

data class LastFmTopArtistsMeta(
    @SerializedName("artist") val artists: List<LastFmArtist> = emptyList()
)

data class LastFmTopArtistsResponse(
    @SerializedName("topartists") val topArtists: LastFmTopArtistsMeta? = null
)

data class LastFmAlbum(
    @SerializedName("name") val name: String = "",
    @SerializedName("playcount") val playCount: String = "0",
    @SerializedName("rank") val rank: String? = null,
    @SerializedName("url") val url: String = "",
    @SerializedName("artist") val artist: LastFmAlbumArtist = LastFmAlbumArtist(),
    @SerializedName("image") val images: List<LastFmImage> = emptyList()
) {
    val playCountInt get() = playCount.toLongOrNull() ?: 0L
    val imageUrl get() = images.bestUrl()
}

data class LastFmAlbumArtist(
    @SerializedName("name") val name: String = "",
    @SerializedName("url") val url: String = ""
)

data class LastFmTopAlbumsMeta(
    @SerializedName("album") val albums: List<LastFmAlbum> = emptyList()
)

data class LastFmTopAlbumsResponse(
    @SerializedName("topalbums") val topAlbums: LastFmTopAlbumsMeta? = null
)

data class LastFmArtistStats(
    @SerializedName("listeners") val listeners: String = "0",
    @SerializedName("playcount") val playCount: String = "0",
    @SerializedName("userplaycount") val userPlayCount: String = "0"
)

data class LastFmTag(
    @SerializedName("name") val name: String = "",
    @SerializedName("url") val url: String = ""
)

data class LastFmTagList(
    @SerializedName("tag") val tags: List<LastFmTag> = emptyList()
)

data class LastFmArtistInfo(
    @SerializedName("name") val name: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("stats") val stats: LastFmArtistStats = LastFmArtistStats(),
    @SerializedName("image") val images: List<LastFmImage> = emptyList(),
    @SerializedName("tags") val tags: LastFmTagList = LastFmTagList()
)

data class LastFmArtistInfoResponse(
    @SerializedName("artist") val artist: LastFmArtistInfo? = null
)

data class LastFmAlbumInfo(
    @SerializedName("name") val name: String = "",
    @SerializedName("artist") val artist: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("listeners") val listeners: String = "0",
    @SerializedName("playcount") val playCount: String = "0",
    @SerializedName("userplaycount") val userPlayCount: String = "0",
    @SerializedName("image") val images: List<LastFmImage> = emptyList(),
    @SerializedName("tags") val tags: LastFmTagList = LastFmTagList()
)

data class LastFmAlbumInfoResponse(
    @SerializedName("album") val album: LastFmAlbumInfo? = null
)

data class LastFmUserInfo(
    @SerializedName("name") val name: String = "",
    @SerializedName("playcount") val playCount: String = "0",
    @SerializedName("image") val images: List<LastFmImage> = emptyList(),
    @SerializedName("url") val url: String = ""
)

data class LastFmUserInfoResponse(
    @SerializedName("user") val user: LastFmUserInfo? = null
)

data class LastFmTrackInfo(
    @SerializedName("name") val name: String = "",
    @SerializedName("userloved") val userLoved: String = "0",
    @SerializedName("userplaycount") val userPlayCount: String = "0",
    @SerializedName("playcount") val playCount: String = "0",
    @SerializedName("listeners") val listeners: String = "0"
) {
    val isLoved get() = userLoved == "1"
    val userPlayCountLong get() = userPlayCount.toLongOrNull() ?: 0L
}

data class LastFmTrackInfoResponse(
    @SerializedName("track") val track: LastFmTrackInfo? = null
)

data class LastFmLovedTrackArtist(
    @SerializedName("name") val name: String = ""
)

data class LastFmLovedTrack(
    @SerializedName("name") val name: String = "",
    @SerializedName("artist") val artist: LastFmLovedTrackArtist = LastFmLovedTrackArtist()
)

data class LastFmLovedTracksMeta(
    @SerializedName("track") val tracks: List<LastFmLovedTrack> = emptyList()
)

data class LastFmLovedTracksResponse(
    @SerializedName("lovedtracks") val lovedTracks: LastFmLovedTracksMeta? = null
)

data class LastFmTopTrack(
    @SerializedName("name") val name: String = "",
    @SerializedName("playcount") val playCount: String = "0",
    @SerializedName("artist") val artist: LastFmLovedTrackArtist = LastFmLovedTrackArtist()
) {
    val playCountLong get() = playCount.toLongOrNull() ?: 0L
}

data class LastFmTopTracksMeta(@SerializedName("track") val tracks: List<LastFmTopTrack> = emptyList())

data class LastFmTopTracksResponse(@SerializedName("toptracks") val topTracks: LastFmTopTracksMeta? = null)

data class LastFmSession(
    @SerializedName("name") val name: String = "",
    @SerializedName("key") val key: String = ""
)

data class LastFmSessionResponse(
    @SerializedName("session") val session: LastFmSession? = null
)

data class LastFmStatusResponse(
    @SerializedName("status") val status: String = ""
)
