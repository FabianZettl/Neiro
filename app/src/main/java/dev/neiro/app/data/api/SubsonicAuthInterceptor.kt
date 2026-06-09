package dev.neiro.app.data.api

import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.data.prefs.NieroPrefs
import dev.neiro.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites every Retrofit request to:
 *  1. Replace the placeholder base URL with the configured server URL
 *  2. Append Subsonic MD5 token-auth query parameters
 *
 * Uses a cached copy of preferences (updated via Flow) to avoid
 * blocking the OkHttp dispatch thread with runBlocking.
 */
@Singleton
class SubsonicAuthInterceptor @Inject constructor(
    preferences: NieroPreferences,
    @ApplicationScope scope: CoroutineScope
) : Interceptor {

    @Volatile
    private var cachedPrefs: NieroPrefs = NieroPrefs()

    init {
        scope.launch {
            preferences.prefsFlow.collect { prefs ->
                cachedPrefs = prefs
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val prefs = cachedPrefs

        if (prefs.serverUrl.isBlank()) {
            return chain.proceed(chain.request())
        }

        val salt = generateSalt()
        val token = md5(prefs.password + salt)
        val originalRequest = chain.request()

        val serverHttpUrl = prefs.serverUrl.trimEnd('/').toHttpUrlOrNull()
            ?: return chain.proceed(originalRequest)

        // Replace scheme/host/port while keeping ALL original path + query params intact,
        // then append Subsonic auth params. This is critical — the old approach only kept
        // the path and dropped all @Query parameters (type=, id=, query=, etc.).
        val newUrlBuilder = originalRequest.url.newBuilder()
            .scheme(serverHttpUrl.scheme)
            .host(serverHttpUrl.host)
            .port(serverHttpUrl.port)

        // If the server has a non-root base path (e.g. https://example.com/music),
        // prepend it to the endpoint path.
        val serverBasePath = serverHttpUrl.encodedPath.trimEnd('/')
        if (serverBasePath.isNotEmpty()) {
            newUrlBuilder.encodedPath(serverBasePath + originalRequest.url.encodedPath)
        }

        val newUrl = newUrlBuilder
            .addQueryParameter("u", prefs.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "neiro")
            .addQueryParameter("f", "json")
            .build()

        return chain.proceed(originalRequest.newBuilder().url(newUrl).build())
    }

    companion object {
        private val SALT_CHARS = ('a'..'z') + ('0'..'9')

        fun generateSalt(length: Int = 8): String =
            (1..length).map { SALT_CHARS.random() }.joinToString("")

        fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
