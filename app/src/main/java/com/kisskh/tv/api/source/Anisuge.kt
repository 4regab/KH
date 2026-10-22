package com.kisskh.tv.api.source

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbPaths
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.util.Locale

/**
 * Anisuge (anisuge.tv). The catalog is HTML. Episode and server lists are HTML inside JSON, and
 * the episode list is gated by [vrf]. Megaplay servers are decrypted in [MegaPlay]. Other
 * servers are still embed pages. Sub and dub are two groups of those servers.
 */
object Anisuge {
    const val SITE = "https://anisuge.tv"
    const val REFERER = "https://anisuge.tv/"
    private const val RC4_KEY = "ysJhV6U27FVIjjuk"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun showUrl(slug: String) = "$SITE/anime/$slug"
    fun episodeListUrl(mangaId: String, vrf: String) = "$SITE/ajax/episode/list/$mangaId?vrf=${urlEncode(vrf)}"
    fun serverListUrl(token: String) = "$SITE/ajax/server/list?servers=${urlEncode(token)}"
    fun serverUrl(linkId: String) = "$SITE/ajax/server?get=${urlEncode(linkId)}"
    fun searchUrl(keyword: String) = "$SITE/ajax/anime/search?keyword=${urlEncode(keyword)}"

    /**
     * One page (28 titles) of the site's own `/filter` list. Every part is optional except the order:
     * [genre] is a genre id, [year] a premiere year, [status] `currently-airing` or `finished-airing`.
     * The order is always sent, so a grid has one fixed order (`default` on the site is the same as
     * `latest-updated`). Country is not sent: the site ignores it.
     */
    fun filterUrl(page: Int, sort: BrowseSort, genre: String? = null, year: Int? = null, status: String? = null): String {
        val parts = ArrayList<String>()
        parts += "sort=" + when (sort) {
            BrowseSort.POPULAR -> "most-viewed"
            BrowseSort.TOP_RATED -> "score"
            BrowseSort.NEWEST -> "release-date"
            BrowseSort.UPDATED -> "latest-updated"
        }
        genre?.takeIf { it.isNotBlank() }?.let { parts += "genre%5B%5D=${urlEncode(it)}" }
        year?.let { parts += "year%5B%5D=$it" }
        status?.takeIf { it.isNotBlank() }?.let { parts += "status%5B%5D=${urlEncode(it)}" }
        parts += "page=$page"
        return "$SITE/filter?" + parts.joinToString("&")
    }

    /** True when the page links to a page after [page]. The last page only links back. */
    fun hasNextPage(html: String, page: Int): Boolean =
        Regex("""[?&;]page=(\d+)""").findAll(html).any { (it.groupValues[1].toIntOrNull() ?: 0) > page }

    /** What the show page says about the show. Every field is null or empty when the page lacks it. */
    data class ShowInfo(
        val overview: String? = null,
        val genres: List<String> = emptyList(),
        val year: String? = null,
        val runtimeMinutes: Int? = null,
        /** MAL score out of 10. */
        val score: Double? = null,
        val certification: String? = null,
        val status: String? = null,
        /** The page's wide background, resized to 1280 px when it is a TMDB picture. */
        val backdrop: String? = null,
    )

    fun parseShowInfo(html: String): ShowInfo {
        // The text of one meta row: from its label to the end of that row, so a missing value never reads the next row.
        fun after(label: String): String? {
            val i = html.indexOf("<div>$label</div>")
            return if (i < 0) null else html.substring(i + label.length + 11, minOf(html.length, i + 2000)).substringBefore("</div>")
        }
        fun plain(raw: String?): String? =
            raw?.let { HtmlText.unescape(it.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")).trim() }?.ifBlank { null }
        val full = Regex("""data-name="full"[^>]*>\s*<div>([\s\S]*?)</div>""").find(html)?.groupValues?.get(1)
        val short = Regex("""data-name="short"[^>]*>\s*<div>([\s\S]*?)</div>""").find(html)?.groupValues?.get(1)
        val genres = after("Genre:")
            ?.let { span -> Regex("""<a\b[^>]*>([\s\S]*?)</a>""").findAll(span).mapNotNull { plain(it.groupValues[1]) }.toList() }
            .orEmpty()
        val backdrop = Regex("""class="media-bg[^"]*"\s+style="background-image:\s*url\('([^']+)'\)""").find(html)?.groupValues?.get(1)
        return ShowInfo(
            overview = plain(full) ?: plain(short),
            genres = genres,
            year = after("Premiered:")?.let { Regex("""\d{4}""").find(it)?.value },
            runtimeMinutes = after("Duration:")?.let { Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it > 0 },
            score = after("MAL:")?.let { Regex("""(\d+(?:\.\d+)?)""").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }?.takeIf { it > 0 },
            certification = plain(Regex("""class="rating mini-status"[^>]*>([^<]*)<""").find(html)?.groupValues?.get(1)),
            status = plain(after("Status:")),
            backdrop = backdrop?.let { art ->
                if ("image.tmdb.org/t/p/original/" in art) art.replace("/t/p/original/", "/t/p/${TmdbPaths.BACKDROP}/") else art
            },
        )
    }

    /** Same value the site sends as `vrf` for `ajax/episode/list/{id}`. */
    fun vrf(value: Any): String {
        val encoded = encodeUriComponent(value.toString())
        val inner = base64Url(rc4(RC4_KEY, encoded))
        return rot13(base64Url(shift(inner)))
    }

    fun slugOf(href: String): String {
        val path = href.substringAfter("anisuge.tv").substringBefore('?').trim('/')
        val parts = path.split('/').filter { it.isNotBlank() && it != "anime" && !it.startsWith("ep-") }
        return parts.lastOrNull().orEmpty()
    }

    fun parseHome(html: String): List<ShelfRow> {
        val rows = ArrayList<ShelfRow>()
        val headings = Regex("""<h2>([^<]+)</h2>""").findAll(html).toList()
        headings.forEachIndexed { index, heading ->
            val start = heading.range.last
            val end = headings.getOrNull(index + 1)?.range?.first ?: html.length
            val cards = parsePosters(html.substring(start, end))
            if (cards.isNotEmpty()) rows += ShelfRow(HtmlText.unescape(heading.groupValues[1].trim()), cards)
        }
        if (rows.isEmpty()) {
            val cards = parsePosters(html)
            if (cards.isNotEmpty()) rows += ShelfRow("Anime", cards)
        }
        // The slides only carry a wide banner. Borrow the poster of the same title from the rows below.
        val posters = rows.flatMap { it.items }.filter { !it.poster.isNullOrBlank() }.associateBy { it.id }
        val featured = parseSlides(html).map { slide -> slide.copy(poster = posters[slide.id]?.poster) }
        return if (featured.isEmpty()) rows else listOf(ShelfRow("Featured", featured)) + rows
    }

    /**
     * Every title card in [html]. Two layouts exist: the main grid (`<a class="poster" href>` with a
     * lazy-loaded `data-src`) and the ranked list (`<a class="item" href>` holding a poster div with
     * a plain `src`). Each `<a>` is read on its own, so a poster is never paired with a neighbour's link.
     */
    fun parsePosters(html: String): List<TitleCard> = parseLinks(html)

    private fun parseLinks(html: String): List<TitleCard> {
        val out = ArrayList<TitleCard>()
        for (piece in html.split(Regex("""(?i)<a\s""")).drop(1)) {
            val href = HtmlText.attr("<a ${piece.substringBefore('>')}>", "href") ?: continue
            if ("/anime/" !in href) continue
            val slug = slugOf(href)
            if (slug.isBlank()) continue
            val img = HtmlText.openingTags(piece, "img").firstOrNull() ?: continue
            val art = (HtmlText.attr(img, "data-src") ?: HtmlText.attr(img, "src"))?.let(::absolute) ?: continue
            if (art.startsWith("data:")) continue
            val name = HtmlText.attr(img, "alt")?.trim()?.takeIf { it.isNotEmpty() }
                ?: Regex("""class="name"[^>]*>([^<]+)""").find(piece)?.groupValues?.get(1)?.trim()
                ?: slug
            out += TitleCard(
                source = Library.ANISUGE,
                id = slug,
                kind = MediaKind.ANIME,
                name = HtmlText.unescape(name),
                poster = art,
                backdrop = null,
                year = null,
                overview = null,
                slug = slug,
            )
        }
        return out.distinctBy { it.id }
    }

    private fun absolute(url: String): String = if (url.startsWith("//")) "https:$url" else url

    /** The show's own poster on its page (the `og:image` tag), or null. */
    fun showPoster(html: String): String? =
        HtmlText.openingTags(html, "meta")
            .firstOrNull { HtmlText.attr(it, "property") == "og:image" }
            ?.let { HtmlText.attr(it, "content") }
            ?.takeIf { it.isNotBlank() }
            ?.let(::absolute)

    fun parseSlides(html: String): List<TitleCard> {
        val pattern = Regex("""<a href="([^"]+)" title="([^"]+)"\s+class="swiper-slide"\s+style="background-image:\s*url\('([^']+)'\)""")
        return pattern.findAll(html).map { match ->
            val slug = slugOf(match.groupValues[1])
            TitleCard(
                source = Library.ANISUGE,
                id = slug,
                kind = MediaKind.ANIME,
                name = HtmlText.unescape(match.groupValues[2]),
                poster = null,
                backdrop = match.groupValues[3],
                year = null,
                overview = null,
                slug = slug,
            )
        }.filter { it.id.isNotBlank() }.distinctBy { it.id }.toList()
    }

    fun mangaId(html: String): String? = Regex("""data-manga-id="(\d+)"""").find(html)?.groupValues?.get(1)

    fun showName(html: String): String? {
        val raw = Regex("""<h1[^>]*>([\s\S]*?)</h1>""").find(html)?.groupValues?.get(1) ?: return null
        return HtmlText.unescape(raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")).trim().ifBlank { null }
    }

    fun parseEpisodes(html: String): List<EpisodeRef> {
        val out = ArrayList<EpisodeRef>()
        for (tag in HtmlText.openingTags(html, "a")) {
            val token = HtmlText.attr(tag, "data-ids") ?: continue
            val id = HtmlText.attr(tag, "data-id") ?: continue
            val slug = HtmlText.attr(tag, "data-slug").orEmpty()
            val number = slug.toIntOrNull() ?: (out.size + 1)
            val name = HtmlText.attr(tag, "title").orEmpty().ifBlank { "Episode $number" }
            out += EpisodeRef(
                id = id,
                season = 1,
                number = number,
                name = name,
                still = null,
                slug = slug.ifBlank { number.toString() },
                token = token,
                sub = HtmlText.attr(tag, "data-sub") != "0",
                dub = HtmlText.attr(tag, "data-dub") == "1",
            )
        }
        return out.sortedBy { it.number }
    }

    data class Server(val name: String, val audio: String, val linkId: String)

    fun parseServers(html: String): List<Server> {
        val out = ArrayList<Server>()
        val chunks = html.split(Regex("""(?=<div class="server-type")"""))
        for (chunk in chunks) {
            val audio = HtmlText.attr(chunk.substringBefore('>'), "data-type")
                ?: Regex("""data-type="(sub|dub)"""").find(chunk)?.groupValues?.get(1)
                ?: "sub"
            for (tag in HtmlText.openingTags(chunk, "div")) {
                val link = HtmlText.attr(tag, "data-link-id") ?: continue
                val classes = HtmlText.attr(tag, "class").orEmpty().split(Regex("\\s+"))
                if ("server" !in classes) continue
                val name = Regex("""<span>([^<]+)</span>""").find(chunk.substringAfter(tag).take(200))?.groupValues?.get(1)?.trim()
                    ?: "Server"
                out += Server(name = name, audio = audio, linkId = link)
            }
        }
        return out
    }

    fun parseServerUrl(json: String): String? {
        var url: String? = null
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "result" && reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "url") url = reader.nextStringOrEmpty().ifBlank { null } else reader.skipValue()
                    }
                    reader.endObject()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        return url?.replace("\\/", "/")
    }

    /** Search replies are `{"result":{"html":"..."}}`. */
    fun parseSearch(json: String): List<TitleCard> {
        var html = ""
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return emptyList()
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "result" && reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "html") html = reader.nextStringOrEmpty() else reader.skipValue()
                    }
                    reader.endObject()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        if (html.isBlank()) return emptyList()
        return parseSearchHtml(html)
    }

    fun parseSearchHtml(html: String): List<TitleCard> = parseLinks(html)

    private fun encodeUriComponent(value: String): String {
        val safe = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
        val sb = StringBuilder()
        for (ch in value) {
            if (ch in safe) sb.append(ch)
            else {
                for (b in ch.toString().toByteArray(Charsets.UTF_8)) {
                    sb.append('%')
                    sb.append(String.format(Locale.US, "%02X", b.toInt() and 0xFF))
                }
            }
        }
        return sb.toString()
    }

    private fun rc4(key: String, data: String): String {
        val box = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + box[i] + key[i % key.length].code) % 256
            val swap = box[i]
            box[i] = box[j]
            box[j] = swap
        }
        var i = 0
        j = 0
        val out = StringBuilder()
        for (ch in data) {
            i = (i + 1) % 256
            j = (j + box[i]) % 256
            val swap = box[i]
            box[i] = box[j]
            box[j] = swap
            val k = box[(box[i] + box[j]) % 256]
            out.append((ch.code xor k).toChar())
        }
        return out.toString()
    }

    private fun base64Url(data: String): String {
        val bytes = IntArray(data.length) { data[it].code and 0xFF }
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i]
            val b1 = if (i + 1 < bytes.size) bytes[i + 1] else null
            val b2 = if (i + 2 < bytes.size) bytes[i + 2] else null
            val n0 = b0 shr 2
            var n1 = (b0 and 3) shl 4
            var n2: Int? = null
            var n3: Int? = null
            if (b1 != null) {
                n1 = n1 or (b1 shr 4)
                n2 = (b1 and 15) shl 2
            }
            if (b2 != null && n2 != null) {
                n2 = n2 or (b2 shr 6)
                n3 = b2 and 63
            }
            out.append(ALPHABET[n0])
            out.append(ALPHABET[n1])
            out.append(if (n2 == null) '=' else ALPHABET[n2])
            out.append(if (n3 == null) '=' else ALPHABET[n3])
            i += 3
        }
        return out.toString().replace('/', '_').replace('+', '-')
    }

    private fun shift(value: String): String {
        val out = StringBuilder()
        value.forEachIndexed { index, ch ->
            var code = ch.code
            when (index % 8) {
                0 -> code -= 3
                1 -> code += 3
                2 -> code -= 4
                3 -> code += 2
                4 -> code -= 2
                5 -> code += 5
                6 -> code += 4
                else -> code += 5
            }
            out.append(code.toChar())
        }
        return out.toString()
    }

    private fun rot13(value: String): String {
        val out = StringBuilder()
        for (ch in value) {
            val base = when (ch) {
                in 'A'..'Z' -> 'A'
                in 'a'..'z' -> 'a'
                else -> {
                    out.append(ch)
                    continue
                }
            }
            out.append(base + (ch - base + 13) % 26)
        }
        return out.toString()
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
