package com.kisskh.tv.api.media

import com.squareup.moshi.JsonReader
import okio.Buffer
import java.net.URI

/**
 * The embed pages and CineJoy decrypt their stream link in page script, so the app does not copy
 * their keys. It loads the page in a WebView, waits for the page's own player to ask for an HLS
 * playlist, and hands that URL to the native player with the headers the page used.
 */
data class SniffedStream(val url: String, val headers: Map<String, String>)

object StreamSniff {
    /** Players that load a fake clip first. These are never the title. */
    private val decoys = listOf("demo-video", "thumbnail", "/ads/", "preroll")

    fun isStream(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (EmbedPolicy.blocked(host)) return false
        val path = (uri.rawPath ?: "").lowercase()
        if (decoys.any { it in path }) return false
        return path.endsWith(".m3u8")
    }

    /**
     * Keep only what the stream host checks. A missing Referer falls back to the page that asked,
     * since VidRock's CDN answers 403 without one.
     */
    fun headers(request: Map<String, String>, page: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        request.forEach { (k, v) ->
            when (k.lowercase()) {
                "referer" -> out["Referer"] = v
                "origin" -> out["Origin"] = v
            }
        }
        if ("Referer" !in out) origin(page)?.let { out["Referer"] = "$it/" }
        return out
    }

    fun origin(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }
}

/** CineJoy's own watch page. Its default servers need no Febbox token. */
object CineJoyWeb {
    const val SITE = "https://cinejoy.pk"

    fun watchUrl(movie: Boolean, id: Int, season: Int, episode: Int): String =
        if (movie) "$SITE/watch/movie/$id"
        else "$SITE/watch/tv/$id/${season.coerceAtLeast(1)}/${episode.coerceAtLeast(1)}"

    fun subtitlesUrl(movie: Boolean, id: Int, season: Int, episode: Int): String =
        if (movie) "https://subs.wing.st/subtitles?type=movie&tmdb=$id"
        else "https://subs.wing.st/subtitles?type=tv&tmdb=$id&season=${season.coerceAtLeast(1)}&episode=${episode.coerceAtLeast(1)}"
}

data class SubLink(val url: String, val label: String, val lang: String)

/** Subtitle lists that the pages fetch in plain JSON. Only SubRip and WebVTT are kept. */
object SubLists {
    const val LIMIT = 8

    /** subs.wing.st: `{"subtitles":[{"url","language","type","display"}]}`. */
    fun parseWing(json: String, limit: Int = LIMIT): List<SubLink> {
        val out = ArrayList<SubLink>()
        JsonReader.of(Buffer().writeUtf8(json)).use { r ->
            if (r.peek() != JsonReader.Token.BEGIN_OBJECT) return emptyList()
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() != "subtitles" || r.peek() != JsonReader.Token.BEGIN_ARRAY) {
                    r.skipValue()
                    continue
                }
                r.beginArray()
                while (r.hasNext()) {
                    val m = r.readStrings()
                    val url = m["url"].orEmpty()
                    val type = m["type"].orEmpty().lowercase()
                    if (url.isBlank() || (type != "srt" && type != "vtt")) continue
                    val lang = m["language"].orEmpty().ifBlank { "und" }.lowercase()
                    val label = m["display"].orEmpty().ifBlank { lang.uppercase() }
                    out += SubLink(url, label, lang)
                }
                r.endArray()
            }
            r.endObject()
        }
        return pick(out, limit)
    }

    /** VidZee and VidRock: `[{"label":"English","file":"https://.../english.vtt"}]`. */
    fun parseLabelFile(json: String, limit: Int = LIMIT): List<SubLink> {
        val out = ArrayList<SubLink>()
        JsonReader.of(Buffer().writeUtf8(json)).use { r ->
            if (r.peek() != JsonReader.Token.BEGIN_ARRAY) return emptyList()
            r.beginArray()
            while (r.hasNext()) {
                val m = r.readStrings()
                val url = m["file"] ?: m["url"] ?: continue
                val label = m["label"].orEmpty().ifBlank { "Subtitles" }
                out += SubLink(url, label, langOf(label))
            }
            r.endArray()
        }
        return pick(out, limit)
    }

    /** English first, then one per other language, so Media3 does not fetch hundreds of files. */
    fun pick(all: List<SubLink>, limit: Int): List<SubLink> {
        val seen = HashSet<String>()
        val unique = all.filter { seen.add(it.url) }
        val english = unique.filter { it.lang == "en" }.take(3)
        val langs = HashSet<String>()
        val others = unique.filter { it.lang != "en" && langs.add(it.lang) }
        return (english + others).take(limit)
    }

    private val names = mapOf(
        "english" to "en", "spanish" to "es", "french" to "fr", "german" to "de", "italian" to "it",
        "portuguese" to "pt", "brazilian" to "pt", "arabic" to "ar", "indonesian" to "id", "malay" to "ms",
        "korean" to "ko", "japanese" to "ja", "chinese" to "zh", "thai" to "th", "vietnamese" to "vi",
        "turkish" to "tr", "russian" to "ru", "hindi" to "hi", "tagalog" to "tl", "filipino" to "tl",
        "dutch" to "nl", "polish" to "pl", "czech" to "cs", "greek" to "el", "hebrew" to "he",
        "romanian" to "ro", "hungarian" to "hu", "swedish" to "sv", "danish" to "da", "finnish" to "fi",
        "norwegian" to "no", "bulgarian" to "bg", "croatian" to "hr", "serbian" to "sr", "slovenian" to "sl",
    )

    fun langOf(label: String): String {
        val word = label.trim().lowercase().takeWhile { it.isLetter() }
        return names[word] ?: "und"
    }

    private fun JsonReader.readStrings(): Map<String, String> {
        if (peek() != JsonReader.Token.BEGIN_OBJECT) {
            skipValue()
            return emptyMap()
        }
        val m = HashMap<String, String>()
        beginObject()
        while (hasNext()) {
            val name = nextName()
            if (peek() == JsonReader.Token.STRING) m[name] = nextString() else skipValue()
        }
        endObject()
        return m
    }
}

/** Fetches a subtitle list. Any failure is no subtitles, never a failed stream. */
class SubFetcher(private val http: okhttp3.OkHttpClient = defaultHttp()) {
    fun load(url: String, referer: String?, parse: (String) -> List<SubLink>): List<com.kisskh.tv.api.SubtitleFile> =
        runCatching {
            val request = okhttp3.Request.Builder().url(url)
                .header("Accept", "application/json")
                .apply { if (referer != null) header("Referer", referer) }
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                parse(response.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList()).map { com.kisskh.tv.api.SubtitleFile(it.url, it.label, it.lang) }

    companion object {
        fun defaultHttp(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
            .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
