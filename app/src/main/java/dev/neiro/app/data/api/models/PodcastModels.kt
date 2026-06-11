package dev.neiro.app.data.api.models

data class PodcastSubscription(
    val id: String = "",
    val feedUrl: String = "",
    val title: String = "",
    val description: String? = null,
    val imageUrl: String? = null,
    val author: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

data class PodcastEpisode(
    val guid: String = "",
    val title: String = "",
    val description: String? = null,
    val audioUrl: String = "",
    val imageUrl: String? = null,
    val durationSeconds: Long? = null,
    val pubDateMs: Long? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null
)

/** Episode paired with its parent subscription, used for home section + playback. */
data class PodcastEpisodeWithPodcast(
    val episode: PodcastEpisode,
    val subscription: PodcastSubscription
)
