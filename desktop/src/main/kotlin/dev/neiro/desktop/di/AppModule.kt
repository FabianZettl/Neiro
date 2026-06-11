package dev.neiro.desktop.di

import dev.neiro.desktop.data.api.LastFmApi
import dev.neiro.desktop.data.api.SubsonicApi
import dev.neiro.desktop.data.api.SubsonicAuthInterceptor
import dev.neiro.desktop.data.prefs.DesktopPreferences
import dev.neiro.desktop.data.repository.LastFmRepository
import dev.neiro.desktop.data.repository.MusicRepository
import dev.neiro.desktop.player.DesktopPlayerController
import dev.neiro.desktop.ui.album.AlbumViewModel
import dev.neiro.desktop.ui.artist.ArtistViewModel
import dev.neiro.desktop.ui.home.HomeViewModel
import dev.neiro.desktop.ui.player.PlayerViewModel
import dev.neiro.desktop.ui.search.SearchViewModel
import dev.neiro.desktop.ui.settings.SettingsViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {

    single { DesktopPreferences() }

    single {
        val prefs = get<DesktopPreferences>()
        SubsonicAuthInterceptor(prefs)
    }

    single {
        val authInterceptor = get<SubsonicAuthInterceptor>()
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single<SubsonicApi> {
        get<Retrofit>().create(SubsonicApi::class.java)
    }

    single<LastFmApi> {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
        Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/2.0/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LastFmApi::class.java)
    }

    single { MusicRepository(get(), get()) }

    single { LastFmRepository(get(), get()) }

    single { DesktopPlayerController(get(), get()) }

    viewModelOf(::HomeViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::AlbumViewModel)
    factory { (artistId: String) -> ArtistViewModel(artistId, get(), get()) }
    viewModelOf(::SearchViewModel)
    viewModelOf(::SettingsViewModel)
}
