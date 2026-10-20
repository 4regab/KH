package com.kisskh.tv.api.media

import java.net.URI

/**
 * CineWave playback. The site does not hand out a file URL. It builds an iframe from the server
 * list in its bundle (`Ay`) and, when `/api/check` reports 404, moves to the next server.
 * `hdhub://` is not a page, so it is left out of [playable].
 *
 * The play token (`Aa` / `g0`) is how the site's own `/play/` route names a title. Embed URLs use
 * the raw TMDB id. The token is still built the same way, so a link can be checked against the site.
 */
object PlayToken {
    private const val KEY = "cinewave"

    fun movie(id: Int) = encode("movie-$id")
    fun tv(id: Int, season: Int, episode: Int) = encode("tv-$id-$season-$episode")

    fun encode(plain: String): String {
        val mixed = ByteArray(plain.length) { i ->
            (plain[i].code xor KEY[i % KEY.length].code).toByte()
        }
        return Base64Url.encode(mixed)
    }

    fun decode(token: String): PlayRef? {
        val bytes = try {
            Base64Url.decode(token)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val plain = buildString {
            bytes.forEachIndexed { i, b ->
                append((b.toInt() xor KEY[i % KEY.length].code).toChar())
            }
        }
        val parts = plain.split("-")
        return when {
            parts.size == 2 && parts[0] == "movie" -> parts[1].toIntOrNull()?.let { PlayRef.Movie(it) }
            parts.size == 4 && parts[0] == "tv" -> {
                val id = parts[1].toIntOrNull() ?: return null
                val season = parts[2].toIntOrNull() ?: return null
                val episode = parts[3].toIntOrNull() ?: return null
                PlayRef.Tv(id, season, episode)
            }
            else -> null
        }
    }
}

sealed interface PlayRef {
    data class Movie(val id: Int) : PlayRef
    data class Tv(val id: Int, val season: Int, val episode: Int) : PlayRef
}

/** URL-safe Base64 without padding, matching the site's `btoa` plus `+`/`/`/`=` replacement. */
internal object Base64Url {
    private const val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(data: ByteArray): String {
        val out = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            out.append(ALPHA[b0 shr 2])
            out.append(ALPHA[((b0 and 3) shl 4) or (b1 shr 4)])
            if (i + 1 < data.size) out.append(ALPHA[((b1 and 15) shl 2) or (b2 shr 6)])
            if (i + 2 < data.size) out.append(ALPHA[b2 and 63])
            i += 3
        }
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        val clean = text.trim().replace('+', '-').replace('/', '_').trimEnd('=')
        if (clean.any { ALPHA.indexOf(it) < 0 }) throw IllegalArgumentException("bad token")
        val out = ArrayList<Byte>(clean.length * 3 / 4)
        var i = 0
        while (i < clean.length) {
            val c0 = ALPHA.indexOf(clean[i])
            val c1 = if (i + 1 < clean.length) ALPHA.indexOf(clean[i + 1]) else 0
            val c2 = if (i + 2 < clean.length) ALPHA.indexOf(clean[i + 2]) else 0
            val c3 = if (i + 3 < clean.length) ALPHA.indexOf(clean[i + 3]) else 0
            out += ((c0 shl 2) or (c1 shr 4)).toByte()
            if (i + 2 < clean.length) out += (((c1 and 15) shl 4) or (c2 shr 2)).toByte()
            if (i + 3 < clean.length) out += (((c2 and 3) shl 6) or c3).toByte()
            i += 4
        }
        return out.toByteArray()
    }
}

data class EmbedServer(
    val name: String,
    val code: String,
    val movie: String,
    val tv: String,
    val supportsDub: Boolean = false,
    val web: Boolean = true,
) {
    fun url(kind: MediaKind, id: Int, season: Int = 1, episode: Int = 1): String {
        val template = if (kind.isMovie) movie else tv
        return template
            .replace("{id}", id.toString())
            .replace("{s}", season.toString())
            .replace("{e}", episode.toString())
    }
}

object EmbedServers {
    val all: List<EmbedServer> = listOf(
        EmbedServer("VidSrc CC", "VCC", "https://vsembed.ru/embed/movie/{id}", "https://vsembed.ru/embed/tv/{id}/{s}/{e}", supportsDub = true),
        EmbedServer("Videasy", "VE", "https://player.videasy.net/movie/{id}", "https://player.videasy.net/tv/{id}/{s}/{e}", supportsDub = true),
        EmbedServer("HdHub", "HDH", "hdhub://movie/{id}", "hdhub://tv/{id}/{s}/{e}", supportsDub = true, web = false),
        EmbedServer("2Embed", "2EM", "https://2embed.cc/embed/movie/{id}", "https://2embed.cc/embed/tv/{id}&s={s}&e={e}"),
        EmbedServer("VidFast", "VF", "https://vidfast.pro/movie/{id}", "https://vidfast.pro/tv/{id}/{s}/{e}"),
        EmbedServer("VidCore", "VC", "https://vidcore.net/embed/movie/{id}", "https://vidcore.net/embed/tv/{id}/{s}/{e}"),
        EmbedServer("Rive", "RIVE", "https://rivestream.org/embed?type=movie&id={id}", "https://rivestream.org/embed?type=tv&id={id}&season={s}&episode={e}"),
        EmbedServer("VidZee", "ZEE", "https://player.vidzee.wtf/embed/movie/{id}", "https://player.vidzee.wtf/embed/tv/{id}?season={s}&episode={e}"),
        EmbedServer("Airflix", "AIR", "https://airflix1.com/movie/{id}", "https://airflix1.com/tv/{id}/{s}/{e}"),
        EmbedServer("VidSync", "SYNC", "https://vidsync.xyz/embed/movie/{id}", "https://vidsync.xyz/embed/tv/{id}/{s}/{e}"),
        EmbedServer("VidRock", "ROCK", "https://vidrock.net/embed/movie/{id}", "https://vidrock.net/embed/tv/{id}/{s}/{e}"),
        EmbedServer("Hexa", "HEXA", "https://hexa.su/embed/movie/{id}", "https://hexa.su/embed/tv/{id}/{s}/{e}"),
        EmbedServer("VidOra", "ORA", "https://vidora.su/embed/movie/{id}", "https://vidora.su/embed/tv/{id}/{s}/{e}"),
        EmbedServer("Peachify", "PEACH", "https://peachify.top/embed/movie/{id}", "https://peachify.top/embed/tv/{id}?season={s}&episode={e}", supportsDub = true),
        EmbedServer("Mapple TV", "MAP", "https://mappletv.uk/embed/movie/{id}", "https://mappletv.uk/embed/tv/{id}/{s}/{e}"),
        EmbedServer("VidKing", "KING", "https://vidking.net/embed/movie/{id}", "https://vidking.net/embed/tv/{id}/{s}/{e}"),
        EmbedServer("TouStream", "TOU", "https://toustream-play.chickenkiller.com/movie/{id}", "https://toustream-play.chickenkiller.com/tv/{id}/{s}/{e}"),
        EmbedServer("VidSrc Embed", "VSE", "https://vidsrc-embed.ru/embed/movie/{id}", "https://vidsrc-embed.ru/embed/tv/{id}/{s}/{e}"),
        EmbedServer("111Movies", "111M", "https://111movies.net/movie/{id}", "https://111movies.net/tv/{id}?s={s}&e={e}"),
        EmbedServer("FMovies", "FM", "https://fmovies.gd/movie/{id}", "https://fmovies.gd/tv/{id}?s={s}&e={e}"),
        EmbedServer("CinemaOS", "COS", "https://cinemaos.tech/embed/movie/{id}", "https://cinemaos.tech/embed/tv/{id}?s={s}&e={e}"),
        EmbedServer("VidLux", "LUX", "https://vidlux.xyz/embed/movie/{id}", "https://vidlux.xyz/embed/tv/{id}/{s}/{e}"),
    )

    /** Servers that can actually be loaded in a WebView, in the site's order. */
    val playable: List<EmbedServer> = all.filter { it.web }

    fun server(index: Int): EmbedServer = playable[index.coerceIn(0, playable.lastIndex)]
}

/** Each server is tried once, starting at [start], then wrapping to the ones before it. */
object EmbedFailover {
    fun order(start: Int, count: Int): List<Int> {
        if (count <= 0 || start !in 0 until count) return emptyList()
        return (start until count).toList() + (0 until start).toList()
    }

    fun next(start: Int, count: Int, tried: Set<Int>): Int? =
        order(start, count).firstOrNull { it !in tried }
}

/**
 * What to do with a navigation inside the embed player.
 * Popups and non-http schemes are blocked. A known ad host on the main frame fails over.
 */
enum class EmbedNav { ALLOW, BLOCK, FAIL }

object EmbedPolicy {
    private val blocked = listOf(
        "detergesateens.com",
        "popads.net",
        "popcash.net",
        "adsterra.com",
        "doubleclick.net",
        "googlesyndication.com",
        "exoclick.com",
        "clickadu.com",
        "hilltopads.net",
        "propellerads.com",
        "adcash.com",
        "adskeeper.co.uk",
        "mgid.com",
        "outbrain.com",
        "taboola.com",
        "adnxs.com",
        "serving-sys.com",
        "popunder.net",
        "onclickads.net",
        "juicyads.com",
        "trafficjunky.net",
        "exdynsrv.com",
        "tsyndicate.com",
        "clickaine.com",
    )

    fun blocked(host: String): Boolean {
        val h = host.lowercase().trim('.')
        return blocked.any { h == it || h.endsWith(".$it") }
    }

    fun decide(url: String, mainFrame: Boolean): EmbedNav {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return EmbedNav.BLOCK
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme == "about") return EmbedNav.ALLOW
        if (scheme != "http" && scheme != "https") return EmbedNav.BLOCK
        val host = uri.host?.lowercase() ?: return EmbedNav.BLOCK
        if (blocked(host)) return if (mainFrame) EmbedNav.FAIL else EmbedNav.BLOCK
        return EmbedNav.ALLOW
    }
}
