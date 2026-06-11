package dev.neiro.desktop.data.api

import dev.neiro.desktop.data.prefs.DesktopPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest

class SubsonicAuthInterceptor(
    preferences: DesktopPreferences
) : Interceptor {

    @Volatile
    private var cachedServerUrl: String = ""
    @Volatile
    private var cachedUsername: String = ""
    @Volatile
    private var cachedPassword: String = ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            preferences.prefsFlow.collect { prefs ->
                cachedServerUrl = prefs.serverUrl
                cachedUsername = prefs.username
                cachedPassword = prefs.password
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val serverUrl = cachedServerUrl
        val username = cachedUsername
        val password = cachedPassword

        if (serverUrl.isBlank()) {
            return chain.proceed(chain.request())
        }

        val salt = generateSalt()
        val token = md5(password + salt)
        val originalRequest = chain.request()

        val serverHttpUrl = serverUrl.trimEnd('/').toHttpUrlOrNull()
            ?: return chain.proceed(originalRequest)

        val newUrlBuilder = originalRequest.url.newBuilder()
            .scheme(serverHttpUrl.scheme)
            .host(serverHttpUrl.host)
            .port(serverHttpUrl.port)

        val serverBasePath = serverHttpUrl.encodedPath.trimEnd('/')
        if (serverBasePath.isNotEmpty()) {
            newUrlBuilder.encodedPath(serverBasePath + originalRequest.url.encodedPath)
        }

        val newUrl = newUrlBuilder
            .addQueryParameter("u", username)
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
