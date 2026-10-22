package com.kisskh.tv.api.source

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.net.URLEncoder

/**
 * Anime Nexus (anime.nexus). The site is a client of api.anime.nexus. Requests need the same
 * fingerprint headers the page sends. Images are imgproxy paths on anime.delivery.
 * The stream API names an m3u8, but every playlist and segment also needs a token from their
 * socket. The page player asks for those tokens, so playback stays in that page.
 */
object Nexus {
    const val SITE = "https://anime.nexus"
    const val API = "https://api.anime.nexus"
    const val IMAGES = "https://anime.delivery"
    const val REFERER = "https://anime.nexus/"
    const val FINGERPRINT = "11111111-1111-4111-8111-111111111111:22222222-2222-4222-8222-222222222222"

    fun headers(): Map<String, String> = linkedMapOf(
        "Accept" to "application/json, text/plain, */*",
        "Origin" to SITE,
        "Referer" to REFERER,
        "X-Client-Fingerprint" to FINGERPRINT,
        "x-fingerprint" to FINGERPRINT,
    )

    fun featuredUrl() = "$API/api/anime/featured"
    fun popularUrl(period: String = "day", limit: Int = 20) = "$API/api/anime/popular?period=$period&limit=$limit"
    fun recentUrl() = "$API/api/anime/recent"
    fun seasonalUrl() = "$API/api/anime/seasonal"
    fun latestUrl(page: Int, perPage: Int = 24) = "$API/api/anime/latest?perPage=$perPage&page=$page&audio=all"
    fun searchUrl(query: String) = "$API/api/anime/search?search=${encode(query)}"
    fun episodesUrl(id: String, page: Int, perPage: Int = 100) =
        "$API/api/anime/details/episodes?id=$id&page=$page&perPage=$perPage&order=asc"

    fun statisticsUrl(id: String) = "$API/api/anime/details/statistics?id=$id"

    fun watchUrl(episodeId: String, slug: String): String {
        val tail = slug.trim('/').ifBlank { "watch" }
        return "$SITE/watch/$episodeId/$tail"
    }

    /** The site's own watch page. Sniffing the playlist drops the socket token and the CDN refuses it. */
    fun player(episodeId: String, slug: String): PlayTarget = PlayTarget(
        name = "Nexus",
        page = watchUrl(episodeId, slug),
        referer = REFERER,
        sniff = false,
    )

    fun image(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val file = if (path.startsWith("/")) path else "/$path"
        return "$IMAGES$file"
    }

    fun parseList(json: String): List<TitleCard> {
        val out = ArrayList<TitleCard>()
        walkData(json) { reader -> readCard(reader)?.let { out += it } }
        return out
    }

    fun parseEpisodes(json: String): List<EpisodeRef> {
        val out = ArrayList<EpisodeRef>()
        walkData(json) { reader -> readEpisode(reader)?.let { out += it } }
        return out.sortedBy { it.number }
    }

    fun lastPage(json: String): Int {
        var last = 1
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return 1
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "meta" && reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        if (name == "last_page" || name == "lastPage") last = reader.readLoose().toIntOrNull() ?: last
                        else reader.skipValue()
                    }
                    reader.endObject()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        return last.coerceAtLeast(1)
    }

    private fun walkData(json: String, item: (JsonReader) -> Unit) {
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "data" && reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                    reader.beginArray()
                    while (reader.hasNext()) item(reader)
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
    }

    private fun readCard(reader: JsonReader): TitleCard? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var id = ""
        var slug = ""
        var name = ""
        var poster: String? = null
        var backdrop: String? = null
        var overview = ""
        var year = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.readLoose()
                "slug" -> slug = reader.nextStringOrEmpty()
                "name" -> name = reader.nextStringOrEmpty()
                "description" -> overview = reader.nextStringOrEmpty()
                "poster" -> poster = image(firstPath(reader))
                "backdrop" -> backdrop = image(firstPath(reader))
                "logo" -> reader.skipValue()
                "release_date", "relese_date" -> year = reader.nextStringOrEmpty().take(4)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (id.isBlank() || name.isBlank()) return null
        return TitleCard(
            source = Library.NEXUS,
            id = id,
            kind = MediaKind.ANIME,
            name = name,
            poster = poster,
            backdrop = backdrop,
            year = year.ifBlank { null },
            overview = overview.ifBlank { null },
            slug = slug,
        )
    }

    private fun readEpisode(reader: JsonReader): EpisodeRef? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var id = ""
        var title = ""
        var slug = ""
        var number = 0
        var still: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.readLoose()
                "title" -> title = reader.nextStringOrEmpty()
                "slug" -> slug = reader.nextStringOrEmpty()
                "number" -> number = reader.readLoose().substringBefore('.').toIntOrNull() ?: 0
                "image" -> still = image(firstPath(reader))
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (id.isBlank() || number <= 0) return null
        return EpisodeRef(
            id = id,
            season = 1,
            number = number,
            name = title.ifBlank { "Episode $number" },
            still = still,
            slug = slug,
        )
    }

    /**
     * One picture path from a plain string or a nested object like
     * `{"resized":{"640x960":"/a.avif",...},"resized_blur":{...}}`. The 640-wide rendition wins,
     * otherwise the first path. Anything under a "blur" key is a tiny preview and is never used.
     * Returns null for `null` and for objects with no path.
     */
    private fun firstPath(reader: JsonReader): String? {
        val found = ArrayList<Pair<String, String>>()
        collectPaths(reader, found, "")
        return (found.firstOrNull { it.first.startsWith("640x") } ?: found.firstOrNull())?.second
    }

    private fun collectPaths(reader: JsonReader, found: ArrayList<Pair<String, String>>, key: String) {
        when (reader.peek()) {
            JsonReader.Token.STRING -> {
                val value = reader.nextString()
                // A bare string is the path itself. Inside an object only path-like strings count.
                if (value.isNotBlank() && (key.isEmpty() || '/' in value)) found += key to value
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name.contains("blur", ignoreCase = true)) reader.skipValue() else collectPaths(reader, found, name)
                }
                reader.endObject()
            }
            JsonReader.Token.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) collectPaths(reader, found, key)
                reader.endArray()
            }
            else -> reader.skipValue()
        }
    }

    private fun encode(query: String): String = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
}
