package dev.neiro.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.neiro.desktop.di.appModule
import dev.neiro.desktop.ui.NieroDesktopApp
import okhttp3.OkHttpClient
import org.koin.compose.KoinApplication

fun main() = application {
    // Configure Coil3 singleton with OkHttp for desktop
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() })) }
            .build()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Neiro",
        state = rememberWindowState(width = 1280.dp, height = 820.dp)
    ) {
        KoinApplication(application = { modules(appModule) }) {
            NieroDesktopApp()
        }
    }
}
