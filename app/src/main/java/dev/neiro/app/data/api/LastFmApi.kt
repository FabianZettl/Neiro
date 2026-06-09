package dev.neiro.app.data.api

import dev.neiro.app.data.api.models.LastFmAlbumInfoResponse
import dev.neiro.app.data.api.models.LastFmArtistInfoResponse
import dev.neiro.app.data.api.models.LastFmLovedTracksResponse
import dev.neiro.app.data.api.models.LastFmSessionResponse
import dev.neiro.app.data.api.models.LastFmStatusResponse
import dev.neiro.app.data.api.models.LastFmTopAlbumsResponse
import dev.neiro.app.data.api.models.LastFmTopArtistsResponse
import dev.neiro.app.data.api.models.LastFmTopTracksResponse
import dev.neiro.app.data.api.models.LastFmTrackInfoResponse
import dev.neiro.app.data.api.models.LastFmUserInfoResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LastFmApi {

    @GET(".")
    suspend fun getTopArtists(
        @Query("method") method: String = "user.getTopArtists",
        @Query("user") user: String,
        @Query("period") period: String,   // overall, 7day, 1month, 3month, 6month, 12month
        @Query("limit") limit: Int = 20,
        @Query("api_key") apiKey: String,
        @Query("format") format: String = "json"
    ): LastFmTopArtistsResponse

    @GET(".")
    suspend fun getTopTracks(
        @Query("method") method: String = "user.getTopTracks",
        @Query("user") user: String,
        @Query("api_key") apiKey: String,
        @Query("period") period: String = "1month",
        @Query("limit") limit: Int = 20,
        @Query("format") format: String = "json"
    ): LastFmTopTracksResponse

    @GET(".")
    suspend fun getTopAlbums(
        @Query("method") method: String = "user.getTopAlbums",
        @Query("user") user: String,
        @Query("period") period: String,
        @Query("limit") limit: Int = 20,
        @Query("api_key") apiKey: String,
        @Query("format") format: String = "json"
    ): LastFmTopAlbumsResponse

    @GET(".")
    suspend fun getArtistInfo(
        @Query("method") method: String = "artist.getInfo",
        @Query("artist") artist: String,
        @Query("username") username: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String = "json"
    ): LastFmArtistInfoResponse

    @GET(".")
    suspend fun getAlbumInfo(
        @Query("method") method: String = "album.getInfo",
        @Query("artist") artist: String,
        @Query("album") album: String,
        @Query("username") username: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String = "json"
    ): LastFmAlbumInfoResponse

    @GET(".")
    suspend fun getUserInfo(
        @Query("method") method: String = "user.getInfo",
        @Query("user") user: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String = "json"
    ): LastFmUserInfoResponse

    @GET(".")
    suspend fun getTrackInfo(
        @Query("method") method: String = "track.getInfo",
        @Query("track") track: String,
        @Query("artist") artist: String,
        @Query("username") username: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String = "json"
    ): LastFmTrackInfoResponse

    @GET(".")
    suspend fun getLovedTracks(
        @Query("method") method: String = "user.getLovedTracks",
        @Query("user") user: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 200,
        @Query("format") format: String = "json"
    ): LastFmLovedTracksResponse

    @FormUrlEncoded
    @POST(".")
    suspend fun getMobileSession(
        @Field("method") method: String = "auth.getMobileSession",
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("api_key") apiKey: String,
        @Field("api_sig") apiSig: String,
        @Field("format") format: String = "json"
    ): LastFmSessionResponse

    @FormUrlEncoded
    @POST(".")
    suspend fun loveTrack(
        @Field("method") method: String,
        @Field("track") track: String,
        @Field("artist") artist: String,
        @Field("sk") sessionKey: String,
        @Field("api_key") apiKey: String,
        @Field("api_sig") apiSig: String,
        @Field("format") format: String = "json"
    ): LastFmStatusResponse
}
