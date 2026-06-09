package dev.neiro.app.data.api

import dev.neiro.app.data.api.models.AlbumApiResponse
import dev.neiro.app.data.api.models.AlbumList2ApiResponse
import dev.neiro.app.data.api.models.ArtistDetailApiResponse
import dev.neiro.app.data.api.models.ArtistInfo2ApiResponse
import dev.neiro.app.data.api.models.ArtistsApiResponse
import dev.neiro.app.data.api.models.GenresApiResponse
import dev.neiro.app.data.api.models.PingApiResponse
import dev.neiro.app.data.api.models.PlaylistApiResponse
import dev.neiro.app.data.api.models.PlaylistsApiResponse
import dev.neiro.app.data.api.models.Search3ApiResponse
import dev.neiro.app.data.api.models.SimilarSongsApiResponse
import dev.neiro.app.data.api.models.SongApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {

    @GET("rest/ping")
    suspend fun ping(): PingApiResponse

    @GET("rest/getAlbumList2")
    suspend fun getAlbumList2(
        @Query("type") type: String = "recent",
        @Query("size") size: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("fromYear") fromYear: Int? = null,
        @Query("toYear") toYear: Int? = null,
        @Query("genre") genre: String? = null
    ): AlbumList2ApiResponse

    @GET("rest/getAlbum")
    suspend fun getAlbum(
        @Query("id") id: String
    ): AlbumApiResponse

    @GET("rest/getSong")
    suspend fun getSong(
        @Query("id") id: String
    ): SongApiResponse

    @GET("rest/search3")
    suspend fun search3(
        @Query("query") query: String,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 40,
        @Query("artistCount") artistCount: Int = 10,
        @Query("offset") offset: Int = 0
    ): Search3ApiResponse

    @GET("rest/getArtists")
    suspend fun getArtists(): ArtistsApiResponse

    @GET("rest/getArtist")
    suspend fun getArtist(
        @Query("id") id: String
    ): ArtistDetailApiResponse

    @GET("rest/getArtistInfo2")
    suspend fun getArtistInfo2(
        @Query("id") id: String,
        @Query("count") count: Int = 5
    ): ArtistInfo2ApiResponse

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(): PlaylistsApiResponse

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(
        @Query("id") id: String
    ): PlaylistApiResponse

    @GET("rest/star")
    suspend fun star(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null
    ): PingApiResponse

    @GET("rest/unstar")
    suspend fun unstar(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null
    ): PingApiResponse

    @GET("rest/getGenres")
    suspend fun getGenres(): GenresApiResponse

    @GET("rest/getSimilarSongs2")
    suspend fun getSimilarSongs2(
        @Query("id") songId: String,
        @Query("count") count: Int = 20
    ): SimilarSongsApiResponse
}
