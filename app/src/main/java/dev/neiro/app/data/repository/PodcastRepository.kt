package dev.neiro.app.data.repository

import android.util.Xml
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.neiro.app.data.api.models.PodcastEpisode
import dev.neiro.app.data.api.models.PodcastEpisodeWithPodcast
import dev.neiro.app.data.api.models.PodcastSubscription
import dev.neiro.app.data.prefs.NieroPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val preferences: NieroPreferences
) {
    private val http = OkHttpClient()
    private val gson = Gson()
    private val listType = object : TypeToken<List<PodcastSubscription>>() {}.type

    // Cache: feedUrl → parsed episodes, TTL 20 min
    private val episodeCache = MemoryCache<String, Pair<PodcastSubscription, List<PodcastEpisode>>>(ttlMs = 20 * 60_000L)

    // ── Subscriptions ─────────────────────────────────────────────────────────

    val subscriptionsFlow: Flow<List<PodcastSubscription>> =
        preferences.podcastSubscriptionsJson.map { json ->
            if (json.isNullOrBlank()) emptyList()
            else runCatching { gson.fromJson<List<PodcastSubscription>>(json, listType) }.getOrElse { emptyList() }
        }

    suspend fun addSubscription(feedUrl: String): Result<PodcastSubscription> = runCatching {
        val subs = loadSubscriptions().toMutableList()
        if (subs.any { it.feedUrl == feedUrl }) error("Already subscribed")
        val (sub, _) = fetchAndParse(feedUrl)
        subs.add(sub)
        persist(subs)
        sub
    }

    suspend fun removeSubscription(id: String) {
        persist(loadSubscriptions().filter { it.id != id })
    }

    // ── Episodes ──────────────────────────────────────────────────────────────

    suspend fun fetchEpisodes(subscription: PodcastSubscription): List<PodcastEpisode> =
        runCatching { fetchAndParseOrCached(subscription.feedUrl).second }.getOrElse { emptyList() }

    /** Fetches up to 3 latest episodes from each subscription, sorted by date. */
    suspend fun getLatestEpisodes(limit: Int = 20): List<PodcastEpisodeWithPodcast> {
        val subs = loadSubscriptions()
        return subs.flatMap { sub ->
            runCatching {
                fetchAndParseOrCached(sub.feedUrl).second.take(3).map { PodcastEpisodeWithPodcast(it, sub) }
            }.getOrElse { emptyList() }
        }.sortedByDescending { it.episode.pubDateMs ?: 0L }.take(limit)
    }

    // ── OPML import ───────────────────────────────────────────────────────────

    /** Parses an OPML InputStream and subscribes to all found feeds. Returns count added. */
    suspend fun importOpml(inputStream: InputStream): Int {
        val feedUrls = withContext(Dispatchers.IO) { parseOpml(inputStream) }
        val subs = loadSubscriptions().toMutableList()
        val existing = subs.map { it.feedUrl }.toSet()
        var added = 0
        for (url in feedUrls) {
            if (url in existing) continue
            runCatching {
                val (sub, _) = fetchAndParse(url)
                subs.add(sub)
                added++
            }
        }
        if (added > 0) persist(subs)
        return added
    }

    // ── Internal: HTTP + parsing ──────────────────────────────────────────────

    private suspend fun fetchAndParseOrCached(feedUrl: String): Pair<PodcastSubscription, List<PodcastEpisode>> {
        episodeCache.get(feedUrl)?.let { return it }
        return fetchAndParse(feedUrl).also { episodeCache.put(feedUrl, it) }
    }

    private suspend fun fetchAndParse(feedUrl: String): Pair<PodcastSubscription, List<PodcastEpisode>> =
        withContext(Dispatchers.IO) {
            val response = http.newCall(Request.Builder().url(feedUrl).build()).execute()
            val body = response.body?.byteStream() ?: error("Empty feed response")
            parseRss(feedUrl, body)
        }

    private fun parseRss(feedUrl: String, stream: InputStream): Pair<PodcastSubscription, List<PodcastEpisode>> {
        val p = Xml.newPullParser()
        p.setInput(stream, null)

        var inItem = false
        var inChannelImage = false

        // Channel
        var chTitle = ""; var chDesc: String? = null
        var chImage: String? = null; var chAuthor: String? = null

        // Current episode
        var epTitle = ""; var epDesc: String? = null; var epAudio = ""
        var epGuid = ""; var epImage: String? = null; var epDur: Long? = null
        var epDate: Long? = null; var epSize: Long? = null; var epMime: String? = null

        val episodes = mutableListOf<PodcastEpisode>()

        fun resetEpisode() {
            epTitle = ""; epDesc = null; epAudio = ""; epGuid = ""
            epImage = null; epDur = null; epDate = null; epSize = null; epMime = null
        }

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            val tag = p.name ?: ""
            when (ev) {
                XmlPullParser.START_TAG -> when {
                    tag == "item"                            -> inItem = true
                    tag == "image" && !inItem                -> inChannelImage = true
                    // Channel-level
                    tag == "title" && !inItem && !inChannelImage   -> chTitle = p.nextText()
                    tag == "description" && !inItem          -> if (chDesc == null) chDesc = p.nextText()
                    tag == "url" && inChannelImage           -> if (chImage == null) chImage = p.nextText()
                    tag == "itunes:image" && !inItem         -> if (chImage == null) chImage = p.getAttributeValue(null, "href")
                    tag == "itunes:author" && !inItem        -> if (chAuthor == null) chAuthor = p.nextText()
                    tag == "managingEditor" && !inItem       -> if (chAuthor == null) chAuthor = p.nextText()
                    // Episode-level
                    tag == "title" && inItem                 -> epTitle = p.nextText()
                    tag == "description" && inItem           -> if (epDesc == null) epDesc = p.nextText()
                    tag == "itunes:summary" && inItem        -> if (epDesc == null) epDesc = p.nextText()
                    tag == "content:encoded" && inItem       -> if (epDesc == null) epDesc = p.nextText()
                    tag == "guid" && inItem                  -> epGuid = p.nextText()
                    tag == "pubDate" && inItem               -> epDate = parseDate(p.nextText())
                    tag == "enclosure" && inItem             -> {
                        val url  = p.getAttributeValue(null, "url") ?: ""
                        val mime = p.getAttributeValue(null, "type") ?: ""
                        // prefer audio enclosures; fallback to first non-empty
                        if (url.isNotBlank() && (epAudio.isBlank() || mime.startsWith("audio"))) {
                            epAudio = url
                            epMime  = mime.ifBlank { null }
                            epSize  = p.getAttributeValue(null, "length")?.toLongOrNull()
                        }
                    }
                    tag == "itunes:image" && inItem          -> epImage = p.getAttributeValue(null, "href")
                    tag == "itunes:duration" && inItem       -> epDur = parseDuration(p.nextText())
                    tag == "media:content" && inItem && epAudio.isBlank() -> {
                        val url  = p.getAttributeValue(null, "url") ?: ""
                        val mime = p.getAttributeValue(null, "type") ?: ""
                        if (url.isNotBlank() && mime.startsWith("audio")) epAudio = url
                    }
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "image" -> inChannelImage = false
                    "item"  -> {
                        if (epAudio.isNotBlank()) {
                            episodes.add(PodcastEpisode(
                                guid          = epGuid.ifBlank { epAudio },
                                title         = epTitle.ifBlank { "Untitled" },
                                description   = epDesc,
                                audioUrl      = epAudio,
                                imageUrl      = epImage,
                                durationSeconds = epDur,
                                pubDateMs     = epDate,
                                fileSize      = epSize,
                                mimeType      = epMime
                            ))
                        }
                        resetEpisode(); inItem = false
                    }
                }
            }
            ev = p.next()
        }

        val sub = PodcastSubscription(
            id          = UUID.nameUUIDFromBytes(feedUrl.toByteArray()).toString(),
            feedUrl     = feedUrl,
            title       = chTitle.ifBlank { feedUrl },
            description = chDesc,
            imageUrl    = chImage,
            author      = chAuthor
        )
        return sub to episodes
    }

    private fun parseOpml(stream: InputStream): List<String> {
        val p = Xml.newPullParser()
        p.setInput(stream, null)
        val urls = mutableListOf<String>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "outline") {
                val url = p.getAttributeValue(null, "xmlUrl")
                if (!url.isNullOrBlank()) urls.add(url)
            }
            ev = p.next()
        }
        return urls.distinct()
    }

    // ── Date / duration helpers ───────────────────────────────────────────────

    private val DATE_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    )

    private fun parseDate(str: String): Long? {
        val trimmed = str.trim()
        for (fmt in DATE_FORMATS) {
            try { return SimpleDateFormat(fmt, Locale.ENGLISH).parse(trimmed)?.time } catch (_: Exception) {}
        }
        return null
    }

    /** "HH:MM:SS", "MM:SS", or plain seconds. */
    private fun parseDuration(str: String): Long? {
        if (str.isBlank()) return null
        return runCatching {
            val parts = str.trim().split(":")
            when (parts.size) {
                1 -> parts[0].toLong()
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                else -> null
            }
        }.getOrNull()
    }

    // ── DataStore helpers ─────────────────────────────────────────────────────

    private suspend fun loadSubscriptions(): List<PodcastSubscription> {
        val json = preferences.getPodcastSubscriptions()
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<PodcastSubscription>>(json, listType) }.getOrElse { emptyList() }
    }

    private suspend fun persist(subs: List<PodcastSubscription>) {
        preferences.savePodcastSubscriptions(gson.toJson(subs))
    }
}
