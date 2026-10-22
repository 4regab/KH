package com.kisskh.tv.api.source

import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.Base64Url
import com.kisskh.tv.api.media.SubLink
import com.kisskh.tv.api.media.SubLists
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Anisuge's megaplay.buzz player. `stream/getSourcesNew` returns an AES-CBC `enc` blob, not a file
 * URL. The player decrypts it, then HMACs the two path ids onto the playlist as `token`. The CDN
 * answers 200 only when Referer is the site origin; the full player URL is refused.
 */
object MegaPlay {
    const val ORIGIN = "https://megaplay.buzz"
    const val REFERER = "https://megaplay.buzz/"

    private const val AES_KEY = "i?LMTAx0Q6,:}50U"
    private const val AES_IV = "W0;27ToaUpl_P%'c"
    private const val HMAC_KEY = "MpCdnT0k3n!9f2K#xQ7vL5mR8wN1pY4s"
    private const val TOKEN_TTL_SECONDS = 90L
    private val PATH_IDS = Regex("""/([a-fA-F0-9]{32})/([a-fA-F0-9]{32})/""")
    private val STREAM_ID = Regex("""/stream/(?:[^/?#]+/)?(\d+)(?:[/?#]|$)""")

    fun handles(page: String): Boolean = page.contains("://megaplay.buzz/") || page.contains("://www.megaplay.buzz/")

    fun streamId(page: String): String? = STREAM_ID.find(page)?.groupValues?.get(1)

    /**
     * The id the player script sends to getSources: `data-id` on the `megaplay-player` element of the
     * page. The number in the page URL is a different id, and on some titles it gives another show's stream.
     */
    fun dataId(html: String): String? {
        val tags = HtmlText.openingTags(html, "div")
        val player = tags.firstOrNull { HtmlText.attr(it, "id") == "megaplay-player" }
            ?: tags.firstOrNull { HtmlText.attr(it, "data-id") != null }
        return player?.let { HtmlText.attr(it, "data-id") }?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    fun sourcesUrl(id: String): String =
        "$ORIGIN/stream/getSourcesNew?id=${URLEncoder.encode(id, "UTF-8")}&platform=OTHER"

    fun playbackHeaders(): Map<String, String> = linkedMapOf(
        "Referer" to REFERER,
        "Origin" to ORIGIN,
    )

    data class Resolved(val url: String, val subs: List<SubtitleFile>)

    /** Decrypt [json] from getSources and return the signed playlist plus caption tracks. */
    fun resolve(json: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Resolved? {
        val doc = readSources(json) ?: return null
        val decrypted = doc.enc?.let { enc ->
            try {
                decrypt(enc)
            } catch (_: Exception) {
                null
            }
        }
        val file = decrypted?.let { fileOf(it) } ?: doc.file
        if (file.isNullOrBlank() || !file.startsWith("http")) return null
        // Some titles list dozens of languages. English first and one per other language, so the player does not fetch them all.
        val subs = SubLists.pick(doc.subs.map { SubLink(it.uri, it.label, it.language) }, SubLists.LIMIT)
            .map { SubtitleFile(it.url, it.label, it.lang) }
        return Resolved(sign(file, nowEpochSeconds), subs)
    }

    fun decrypt(enc: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(padded(AES_KEY, 32), "AES"),
            IvParameterSpec(padded(AES_IV, 16)),
        )
        return String(cipher.doFinal(Base64Url.decode(enc)), Charsets.UTF_8)
    }

    /**
     * `expiry|hex1/hex2` signed with HMAC-SHA256. The token is base64url(payload) + "." + base64url(mac).
     * A URL that already has `token` or has no 32-hex path pair is returned unchanged.
     */
    fun sign(url: String, nowEpochSeconds: Long): String {
        if (url.contains("token=")) return url
        val ids = PATH_IDS.find(url) ?: return url
        val material = ids.groupValues[1].lowercase() + "/" + ids.groupValues[2].lowercase()
        val payload = "${nowEpochSeconds + TOKEN_TTL_SECONDS}|$material"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        val token = Base64Url.encode(payload.toByteArray(Charsets.UTF_8)) + "." + Base64Url.encode(signature)
        val separator = if ('?' in url) "&" else "?"
        return "$url${separator}token=$token"
    }

    fun fileOf(json: String): String? {
        var file: String? = null
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "file" && reader.peek() == JsonReader.Token.STRING) {
                    file = reader.nextString()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        return file?.takeIf { it.startsWith("http") }
    }

    private data class Sources(val enc: String?, val file: String?, val subs: List<SubtitleFile>)

    private fun readSources(json: String): Sources? {
        var enc: String? = null
        var file: String? = null
        val subs = ArrayList<SubtitleFile>()
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "enc" -> enc = reader.nextStringOrEmpty().ifBlank { null }
                    "file" -> if (reader.peek() == JsonReader.Token.STRING) file = reader.nextString() else reader.skipValue()
                    "tracks" -> subs += readTracks(reader)
                    "sources" -> file = file ?: readSourceFile(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        return Sources(enc, file, subs)
    }

    private fun readTracks(reader: JsonReader): List<SubtitleFile> {
        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) {
            reader.skipValue()
            return emptyList()
        }
        val out = ArrayList<SubtitleFile>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            var uri = ""
            var label = ""
            var kind = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "file" -> uri = reader.nextStringOrEmpty()
                    "label" -> label = reader.nextStringOrEmpty()
                    "kind" -> kind = reader.nextStringOrEmpty()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (!uri.startsWith("http") || kind.equals("thumbnails", true)) continue
            val name = label.ifBlank { "Subtitles" }
            out += SubtitleFile(uri, name, SubLists.code(name, name))
        }
        reader.endArray()
        return out
    }

    private fun readSourceFile(reader: JsonReader): String? {
        when (reader.peek()) {
            JsonReader.Token.STRING -> return reader.nextString().takeIf { it.startsWith("http") }
            JsonReader.Token.BEGIN_ARRAY -> {
                reader.beginArray()
                var found: String? = null
                while (reader.hasNext()) {
                    if (found == null && reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                        reader.beginObject()
                        var file: String? = null
                        while (reader.hasNext()) {
                            if (reader.nextName() == "file" && reader.peek() == JsonReader.Token.STRING) {
                                file = reader.nextString()
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                        found = file?.takeIf { it.startsWith("http") }
                    } else if (found == null && reader.peek() == JsonReader.Token.STRING) {
                        found = reader.nextString().takeIf { it.startsWith("http") }
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endArray()
                return found
            }
            else -> {
                reader.skipValue()
                return null
            }
        }
    }

    private fun padded(text: String, size: Int): ByteArray {
        val raw = text.toByteArray(Charsets.UTF_8)
        val out = ByteArray(size)
        raw.copyInto(out, endIndex = minOf(raw.size, size))
        return out
    }
}
