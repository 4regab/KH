package com.kisskh.tv.api.source

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.util.Locale

/**
 * Anisuge (anisuge.tv). The catalog is HTML. Episode and server lists are HTML inside JSON, and
 * the episode list is gated by [vrf]. Each server returns an embed URL (megaplay and others),
 * which the app sniffs. Sub and dub are two groups of those servers.
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
        val featured = parseSlides(html)
        if (featured.isNotEmpty()) rows += ShelfRow("Featured", featured)
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
        return rows
    }

    fun parsePosters(html: String): List<TitleCard> {
        val out = ArrayList<TitleCard>()
        var from = 0
        while (true) {
            val at = html.indexOf("class=\"poster\"", from)
            if (at < 0) break
            val window = html.substring(at, minOf(html.length, at + 900))
            val href = HtmlText.attr(window, "href")
            val art = Regex("""data-src="([^"]+)"""").find(window)?.groupValues?.get(1)
            val alt = Regex("""alt="([^"]*)"""").find(window)?.groupValues?.get(1)
            val slug = href?.let(::slugOf).orEmpty()
            if (slug.isNotBlank()) {
                out += TitleCard(
                    source = Library.ANISUGE,
                    id = slug,
                    kind = MediaKind.ANIME,
                    name = HtmlText.unescape(alt.orEmpty()).ifBlank { slug },
                    poster = art,
                    backdrop = null,
                    year = null,
                    overview = null,
                    slug = slug,
                )
            }
            from = at + 12
        }
        return out.distinctBy { it.id }
    }

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

    fun parseSearchHtml(html: String): List<TitleCard> {
        val out = ArrayList<TitleCard>()
        val pieces = html.split(Regex("""(?i)<a\b""")).drop(1)
        for (piece in pieces) {
            val tag = piece.substringBefore('>')
            val href = HtmlText.attr("<a $tag>", "href") ?: continue
            val slug = slugOf(href)
            if (slug.isBlank()) continue
            val name = Regex("""class="name"[^>]*>([^<]+)""").find(piece)?.groupValues?.get(1)
                ?: Regex("""alt="([^"]+)"""").find(piece)?.groupValues?.get(1)
                ?: slug
            val poster = Regex("""(?:data-src|src)="([^"]+)"""").find(piece)?.groupValues?.get(1)
            out += TitleCard(
                source = Library.ANISUGE,
                id = slug,
                kind = MediaKind.ANIME,
                name = HtmlText.unescape(name.trim()),
                poster = poster,
                backdrop = null,
                year = null,
                overview = null,
                slug = slug,
            )
        }
        return out.distinctBy { it.id }
    }

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
