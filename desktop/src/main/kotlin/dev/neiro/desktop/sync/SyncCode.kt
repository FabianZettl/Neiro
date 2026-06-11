package dev.neiro.desktop.sync

import java.util.Base64

data class SyncData(
    val serverUrl: String,
    val username: String,
    val password: String,
    val sectionsJson: String?
)

object SyncCode {
    fun encode(serverUrl: String, username: String, password: String, sectionsJson: String? = null): String {
        val raw = buildString {
            append("$serverUrl\n$username\n$password")
            if (!sectionsJson.isNullOrBlank()) append("\n$sectionsJson")
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    fun decode(code: String): SyncData? = runCatching {
        val parts = String(Base64.getUrlDecoder().decode(code)).split("\n", limit = 4)
        if (parts.size < 3) null
        else SyncData(
            serverUrl = parts[0],
            username = parts[1],
            password = parts[2],
            sectionsJson = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
        )
    }.getOrNull()
}
