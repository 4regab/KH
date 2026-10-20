package com.kisskh.tv.api.media

import com.squareup.moshi.JsonReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * CineJoy's optional Febbox path (4K, more audio tracks). Without a token CineJoy still plays from
 * its own servers, see [CineJoyWeb]. With one, the watch page calls `https://feb.wing.st` with the viewer's Febbox token
 * (`cinejoy_fem_token` in the site, sent as `?ui=`). No token is shipped in the bundle: a request
 * without `ui` is rejected. The useful reply is `sources` whose `url` contains `.m3u8`, best
 * quality first, plus `subtitles`.
 *
 * This is not CineWave's embed list. A missing token is a missing token, not a reason to open an embed.
 */
object Febbox {
    const val BASE = "https://feb.wing.st"

    /** Site order. A lower index is a better stream. Unknown labels sort after these. */
    val QUALITY = listOf("4K", "2160P", "1440P", "1080P", "720P", "480P", "360P")

    fun rank(quality: String): Int {
        val index = QUALITY.indexOf(quality.trim().uppercase())
        return if (index >= 0) index else QUALITY.size
    }

    /** Null when [token] is blank, so a playable URL is never invented. */
    fun requestUrl(movie: Boolean, id: Int, season: Int, episode: Int, token: String): String? {
        val ui = token.trim()
        if (ui.isEmpty()) return null
        val encoded = URLEncoder.encode(ui, "UTF-8").replace("+", "%20")
        val path = if (movie) {
            "/movie/$id"
        } else {
            "/tv/$id/${season.coerceAtLeast(1)}/${episode.coerceAtLeast(1)}"
        }
        return "$BASE$path?ui=$encoded"
    }

    fun select(sources: List<FebSource>): FebSource? =
        sources
            .filter { ".m3u8" in it.url.lowercase() }
            .sortedBy { rank(it.quality) }
            .firstOrNull()

    fun parse(json: String): FebParse =
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                throw IllegalStateException("Febbox sent a reply the app can't read.")
            }
            var error: String? = null
            var sources = emptyList<FebSource>()
            var subs = emptyList<FebSub>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "error" -> error = reader.stringOrNull()
                    "sources" -> sources = reader.sources()
                    "subtitles" -> subs = reader.subs()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            val best = select(sources)
            when {
                best != null -> FebParse.Ready(
                    url = best.url,
                    quality = best.quality,
                    qualities = sources.filter { ".m3u8" in it.url.lowercase() }.sortedBy { rank(it.quality) },
                    subtitles = subs,
                )
                !error.isNullOrBlank() -> FebParse.Rejected(error)
                else -> FebParse.Empty
            }
        }

    private fun JsonReader.sources(): List<FebSource> {
        if (peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            return emptyList()
        }
        val out = ArrayList<FebSource>()
        beginArray()
        while (hasNext()) {
            if (peek() != JsonReader.Token.BEGIN_OBJECT) {
                skipValue()
                continue
            }
            var url = ""
            var quality = ""
            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    "url" -> url = stringOrNull().orEmpty()
                    "quality" -> quality = stringOrNull().orEmpty()
                    else -> skipValue()
                }
            }
            endObject()
            if (url.isNotBlank()) out += FebSource(url, quality)
        }
        endArray()
        return out
    }

    private fun JsonReader.subs(): List<FebSub> {
        if (peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            return emptyList()
        }
        val out = ArrayList<FebSub>()
        val seen = HashSet<String>()
        beginArray()
        while (hasNext()) {
            if (peek() != JsonReader.Token.BEGIN_OBJECT) {
                skipValue()
                continue
            }
            var url = ""
            var label = ""
            var lang: String? = null
            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    "url" -> url = stringOrNull().orEmpty()
                    "label", "name" -> if (label.isBlank()) label = stringOrNull().orEmpty() else skipValue()
                    "lang", "language" -> lang = stringOrNull()
                    else -> skipValue()
                }
            }
            endObject()
            if (url.isBlank() || !seen.add(url)) continue
            val language = lang?.takeIf { it.isNotBlank() } ?: langFromUrl(url)
            out += FebSub(url, label.ifBlank { language.uppercase() }, language)
        }
        endArray()
        return out
    }

    /** `subtitle.en.vtt` -> `en`. The file type itself is not a language. */
    fun langFromUrl(url: String): String {
        val file = url.substringBefore('?').substringAfterLast('/')
        val base = file.substringBeforeLast('.', file)
        val token = base.substringAfterLast('.').substringAfterLast('_').substringAfterLast('-')
        return if (token.length in 2..3 && token.all { it.isLetter() }) token.lowercase() else "und"
    }

    private fun JsonReader.stringOrNull(): String? = when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any?>()
            null
        }
        JsonReader.Token.STRING -> nextString()
        JsonReader.Token.NUMBER -> nextDouble().let { n ->
                if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()
            }
        else -> {
            skipValue()
            null
        }
    }
}

data class FebSource(val url: String, val quality: String)

data class FebSub(val url: String, val label: String, val lang: String)

sealed interface FebParse {
    data class Ready(
        val url: String,
        val quality: String,
        val qualities: List<FebSource>,
        val subtitles: List<FebSub>,
    ) : FebParse

    data class Rejected(val message: String) : FebParse
    data object Empty : FebParse
}

class FebboxClient(private val http: OkHttpClient = defaultHttp()) {
    fun fetch(url: String): FebParse {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Referer", "https://cinejoy.pk/")
            .header("Origin", "https://cinejoy.pk")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return if (response.isSuccessful) FebParse.Empty else FebParse.Rejected("Febbox returned ${response.code}.")
            }
            val parsed = try {
                Febbox.parse(body)
            } catch (e: IllegalStateException) {
                if (response.isSuccessful) throw e
                return FebParse.Rejected("Febbox returned ${response.code}.")
            }
            return when {
                response.isSuccessful -> parsed
                parsed is FebParse.Rejected -> parsed
                parsed is FebParse.Ready -> parsed
                else -> FebParse.Rejected("Febbox returned ${response.code}.")
            }
        }
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}

/** Raised when CineJoy cannot ask Febbox for a stream until the viewer supplies a token. */
class TokenRequired(message: String) : Exception(message)
