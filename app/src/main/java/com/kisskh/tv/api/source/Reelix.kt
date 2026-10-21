package com.kisskh.tv.api.source

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.net.URLEncoder

/**
 * Reelix (reelix.ac). Catalog pages are server-rendered cards. Search and a TV season are JSON.
 * Playback starts at vidcore.io. A blocked vidcore page is skipped. The other servers are the
 * embed list from the watch page, and they stay in the page so the embed's own player can run.
 */
object Reelix {
    const val SITE = "https://reelix.ac"
    const val REFERER = "https://reelix.ac/"

    fun searchUrl(query: String) = "$SITE/api/search?q=${encode(query)}"
    fun listUrl(kind: MediaKind, page: Int): String = when (kind) {
        MediaKind.MOVIE -> "$SITE/movies?page=$page"
        MediaKind.TV -> "$SITE/tv?page=$page"
        MediaKind.ANIME -> "$SITE/anime?page=$page"
    }

    fun titleUrl(kind: MediaKind, slug: String): String {
        val section = if (kind == MediaKind.MOVIE) "movie" else "tv"
        return "$SITE/$section/$slug"
    }

    fun seasonUrl(id: String, season: Int) = "$SITE/api/title/tv/$id/seasons/$season"

    fun vidcore(movie: Boolean, id: String, season: Int, episode: Int): String {
        val query = "theme=d7dee2&autoPlay=true&autoNext=true&poster=true&title=true&chromecast=true&sub=en"
        return if (movie) "https://vidcore.io/movie/$id?$query"
        else "https://vidcore.io/tv/$id/${season.coerceAtLeast(1)}/${episode.coerceAtLeast(1)}?$query&nextButton=true"
    }

    /** Embed servers in the order the watch page prints them. */
    fun embeds(movie: Boolean, id: String, season: Int, episode: Int): List<PlayTarget> {
        val s = season.coerceAtLeast(1)
        val e = episode.coerceAtLeast(1)
        val pages = if (movie) {
            listOf(
                "vsembed" to "https://vsembed.su/embed/movie/$id",
                "vidy" to "https://www.vidy.st/movie/$id",
                "vidbolt" to "https://vidbolt.xyz/embed/movie/$id",
                "vidrock" to "https://vidrock.net/movie/$id",
                "vidlink" to "https://vidlink.pro/movie/$id",
                "superembed" to "https://multiembed.mov/?video_id=$id&tmdb=1",
                "zxc" to "https://player.zxcprime.xyz/embed/movie/$id",
            )
        } else {
            listOf(
                "vsembed" to "https://vsembed.su/embed/tv/$id/$s/$e",
                "vidy" to "https://www.vidy.st/tv/$id/$s/$e",
                "vidbolt" to "https://vidbolt.xyz/embed/tv/$id/$s/$e",
                "vidrock" to "https://vidrock.net/tv/$id/$s/$e",
                "vidlink" to "https://vidlink.pro/tv/$id/$s/$e",
                "superembed" to "https://multiembed.mov/?video_id=$id&tmdb=1&s=$s&e=$e",
                "zxc" to "https://player.zxcprime.xyz/embed/tv/$id/$s/$e",
            )
        }
        return pages.map { (name, url) -> PlayTarget(name = name, page = url, referer = REFERER, sniff = false) }
    }

    fun targets(movie: Boolean, id: String, season: Int, episode: Int): List<PlayTarget> {
        val direct = PlayTarget(name = "VidCore", page = vidcore(movie, id, season, episode), referer = REFERER)
        return listOf(direct) + embeds(movie, id, season, episode)
    }

    fun parseCards(html: String): List<TitleCard> {
        val out = ArrayList<TitleCard>()
        for (tag in HtmlText.openingTags(html, "article")) {
            if ("data-title-card" !in tag && "data-modal-title" !in tag && "data-id" !in tag) continue
            val id = HtmlText.attr(tag, "data-id") ?: continue
            val name = HtmlText.attr(tag, "data-title") ?: continue
            val type = HtmlText.attr(tag, "data-type") ?: "movie"
            val href = HtmlText.attr(tag, "data-href").orEmpty()
            val slug = href.substringAfterLast('/').substringBefore('?')
            out += TitleCard(
                source = Library.REELIX,
                id = id,
                kind = if (type == "tv") MediaKind.TV else MediaKind.MOVIE,
                name = name,
                poster = HtmlText.attr(tag, "data-poster"),
                backdrop = null,
                year = HtmlText.attr(tag, "data-year"),
                overview = null,
                slug = slug,
                meta = HtmlText.attr(tag, "data-rating"),
            )
        }
        return out.distinctBy { it.id + it.kind.name }
    }

    fun seasons(html: String): List<Int> =
        Regex("""data-season="(\d+)"""").findAll(html).map { it.groupValues[1].toInt() }.distinct().sorted().toList()

    fun hasNextPage(html: String, page: Int): Boolean = "page=${page + 1}" in html

    fun parseSearch(json: String): List<TitleCard> = readArray(json, "titles") { readSearchItem(it) }

    fun parseSeason(json: String, season: Int): List<EpisodeRef> {
        val out = ArrayList<EpisodeRef>()
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return emptyList()
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "episodes" && reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                    reader.beginArray()
                    while (reader.hasNext()) out += readEpisode(reader, season)
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        return out.filter { it.number > 0 }.sortedBy { it.number }
    }

    private fun readSearchItem(reader: JsonReader): TitleCard? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var id = ""
        var slug = ""
        var name = ""
        var overview = ""
        var poster: String? = null
        var backdrop: String? = null
        var type = "movie"
        var year = ""
        var rating = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.readLoose()
                "slug" -> slug = reader.nextStringOrEmpty()
                "title", "name" -> if (name.isBlank()) name = reader.nextStringOrEmpty() else reader.skipValue()
                "overview" -> overview = reader.nextStringOrEmpty()
                "poster" -> poster = reader.nextStringOrEmpty().ifBlank { null }
                "backdrop" -> backdrop = reader.nextStringOrEmpty().ifBlank { null }
                "type" -> type = reader.nextStringOrEmpty()
                "year" -> year = reader.readLoose()
                "rating" -> rating = reader.readLoose()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (id.isBlank() || name.isBlank()) return null
        return TitleCard(
            source = Library.REELIX,
            id = id,
            kind = if (type == "tv") MediaKind.TV else MediaKind.MOVIE,
            name = name,
            poster = poster,
            backdrop = backdrop,
            year = year.take(4).ifBlank { null },
            overview = overview.ifBlank { null },
            slug = slug,
            meta = rating.ifBlank { null },
        )
    }

    private fun readEpisode(reader: JsonReader, season: Int): EpisodeRef {
        var number = 0
        var name = ""
        var still: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "number" -> number = reader.readLoose().toIntOrNull() ?: 0
                "title", "name" -> if (name.isBlank()) name = reader.nextStringOrEmpty() else reader.skipValue()
                "still" -> still = reader.nextStringOrEmpty().ifBlank { null }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return EpisodeRef(
            id = number.toString(),
            season = season,
            number = number,
            name = name.ifBlank { "Episode $number" },
            still = still,
        )
    }

    private fun encode(query: String): String = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
}

internal fun JsonReader.nextStringOrEmpty(): String = when (peek()) {
    JsonReader.Token.STRING -> nextString()
    JsonReader.Token.NULL -> { skipValue(); "" }
    else -> { skipValue(); "" }
}

internal fun JsonReader.readLoose(): String = when (peek()) {
    JsonReader.Token.STRING -> nextString()
    JsonReader.Token.NUMBER -> {
        val number = nextDouble()
        if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
    }
    JsonReader.Token.BOOLEAN -> nextBoolean().toString()
    JsonReader.Token.NULL -> { skipValue(); "" }
    else -> { skipValue(); "" }
}

internal fun <T> readArray(json: String, field: String, item: (JsonReader) -> T?): List<T> {
    val out = ArrayList<T>()
    JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return emptyList()
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == field && reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                reader.beginArray()
                while (reader.hasNext()) item(reader)?.let { out += it }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
    }
    return out
}
