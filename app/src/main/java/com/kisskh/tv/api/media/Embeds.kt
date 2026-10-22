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
    /** Plain JSON subtitle list (`[{label,file}]`) the page itself fetches, or null. */
    val subsMovie: String? = null,
    val subsTv: String? = null,
    /** False when the page streams without a plain .m3u8 request, so it can only play in the page. */
    val sniffs: Boolean = true,
) {
    fun url(kind: MediaKind, id: Int, season: Int = 1, episode: Int = 1): String {
        val template = if (kind.isMovie) movie else tv
        return template
            .replace("{id}", id.toString())
            .replace("{s}", season.toString())
            .replace("{e}", episode.toString())
    }

    fun subsUrl(kind: MediaKind, id: Int, season: Int = 1, episode: Int = 1): String? =
        (if (kind.isMovie) subsMovie else subsTv)
            ?.replace("{id}", id.toString())
            ?.replace("{s}", season.toString())
            ?.replace("{e}", episode.toString())
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
        // The site's own VidZee TV link (`/embed/tv/{id}?season=&episode=`) is a 404 page.
        EmbedServer(
            "VidZee", "ZEE", "https://player.vidzee.wtf/embed/movie/{id}", "https://player.vidzee.wtf/embed/tv/{id}/{s}/{e}",
            subsMovie = "https://core.vidzee.wtf/subs/movie/{id}", subsTv = "https://core.vidzee.wtf/subs/tv/{id}/{s}/{e}",
        ),
        EmbedServer("Airflix", "AIR", "https://airflix1.com/movie/{id}", "https://airflix1.com/tv/{id}/{s}/{e}"),
        EmbedServer("VidSync", "SYNC", "https://vidsync.xyz/embed/movie/{id}", "https://vidsync.xyz/embed/tv/{id}/{s}/{e}"),
        EmbedServer(
            "VidRock", "ROCK", "https://vidrock.net/embed/movie/{id}", "https://vidrock.net/embed/tv/{id}/{s}/{e}",
            subsMovie = "https://sub.vdrk.site/v1/movie/{id}", subsTv = "https://sub.vdrk.site/v1/tv/{id}/{s}/{e}",
        ),
        EmbedServer("Hexa", "HEXA", "https://hexa.su/embed/movie/{id}", "https://hexa.su/embed/tv/{id}/{s}/{e}"),
        EmbedServer("VidOra", "ORA", "https://vidora.su/embed/movie/{id}", "https://vidora.su/embed/tv/{id}/{s}/{e}"),
        EmbedServer("Peachify", "PEACH", "https://peachify.top/embed/movie/{id}", "https://peachify.top/embed/tv/{id}?season={s}&episode={e}", supportsDub = true),
        EmbedServer("Mapple TV", "MAP", "https://mappletv.uk/embed/movie/{id}", "https://mappletv.uk/embed/tv/{id}/{s}/{e}", sniffs = false),
        EmbedServer("VidKing", "KING", "https://vidking.net/embed/movie/{id}", "https://vidking.net/embed/tv/{id}/{s}/{e}"),
        EmbedServer("TouStream", "TOU", "https://toustream-play.chickenkiller.com/movie/{id}", "https://toustream-play.chickenkiller.com/tv/{id}/{s}/{e}"),
        EmbedServer("VidSrc Embed", "VSE", "https://vidsrc-embed.ru/embed/movie/{id}", "https://vidsrc-embed.ru/embed/tv/{id}/{s}/{e}"),
        EmbedServer("111Movies", "111M", "https://111movies.net/movie/{id}", "https://111movies.net/tv/{id}?s={s}&e={e}", sniffs = false),
        EmbedServer("FMovies", "FM", "https://fmovies.gd/movie/{id}", "https://fmovies.gd/tv/{id}?s={s}&e={e}"),
        EmbedServer("CinemaOS", "COS", "https://cinemaos.tech/embed/movie/{id}", "https://cinemaos.tech/embed/tv/{id}?s={s}&e={e}"),
        EmbedServer("VidLux", "LUX", "https://vidlux.xyz/embed/movie/{id}", "https://vidlux.xyz/embed/tv/{id}/{s}/{e}"),
    )

    /**
     * Tried first, because they returned a real stream in testing: VidZee and VidRock hand over an
     * HLS playlist the native player can take, Mapple plays only inside its page. The site's first
     * servers (VidSrc CC, Videasy) answer 403 to many networks, and VidCore's link is a 404 page.
     */
    val preferred = listOf("ZEE", "ROCK", "MAP", "111M")

    /** Servers that can actually be loaded in a WebView: [preferred] first, then the site's order. */
    val playable: List<EmbedServer> = all.filter { it.web }.let { web ->
        preferred.mapNotNull { code -> web.firstOrNull { it.code == code } } + web.filter { it.code !in preferred }
    }

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
 * Pages that load but have nothing to play. HTTP and network errors are caught by the WebView; these
 * are the ones it reports as a success: an error page drawn by the site, or a redirect to its home page.
 */
object EmbedPage {
    private val dead = Regex("\\b404\\b|not found|unreachable|error code|access denied|forbidden|bad gateway|unavailable", RegexOption.IGNORE_CASE)

    /** An error page is short. A real site with a player on it is not. */
    private const val SHORT_TEXT = 600

    /** [text] is what the page shows, [players] how many video, iframe and canvas elements it has. */
    fun unavailable(text: String, players: Int): Boolean =
        players == 0 && text.length < SHORT_TEXT && dead.containsMatchIn(text)

    /** True when [asked] pointed at a title but [landed] is a bare http(s) site root. */
    fun sentHome(asked: String, landed: String): Boolean {
        if (!web(landed)) return false
        return path(asked).isNotEmpty() && path(landed).isEmpty()
    }

    /** True when the page replaced itself with about:blank (some players do this when they detect a debugger). */
    fun blanked(landed: String): Boolean = landed.startsWith("about:blank")

    private fun web(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

    private fun path(url: String): String =
        runCatching { URI(url).path.orEmpty().trim('/') }.getOrDefault("")

    /** A short reason for the bar, from the WebView's own error text (for example "net::ERR_NAME_NOT_RESOLVED"). */
    fun reason(error: String): String = when {
        "NAME_NOT_RESOLVED" in error -> "server not found"
        "CONNECTION" in error || "ADDRESS_UNREACHABLE" in error -> "can't connect"
        "TIMED_OUT" in error -> "timed out"
        "SSL" in error || "CERT" in error -> "security error"
        else -> "failed to load"
    }
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
        // Seen in multiembed.mov's ad tag (it picks one at random from a base64 list), and other pop / overlay networks.
        // ponytail: a fixed list. These networks rotate domains, so AdShield's page script does the real work.
        "cdn4ads.com",
        "daqsvmdrgchu.com",
        "pkvjquvxvbd.com",
        "d3g5ovfngjw9bw.cloudfront.net",
        "llvpn.com",
        "a-ads.com",
        "acscdn.com",
        "realsrv.com",
        "adsco.re",
        "pubfuturead.com",
        "highperformanceformat.com",
        "profitablecpmrate.com",
        "effectiveratecpm.com",
        "monetag.com",
        "rtmark.net",
        "admaven.com",
        "ad-maven.com",
        "bvtpk.com",
        "adexchangeclear.com",
    )

    /** The blocked hosts, for the page script that spots ad frames and links. */
    val adHosts: List<String> get() = blocked

    fun blocked(host: String): Boolean {
        val h = host.lowercase().trim('.')
        return blocked.any { h == it || h.endsWith(".$it") }
    }

    /**
     * True when the page tried to move itself to another site after it had settled ([settled] is true once
     * the page has finished loading). Embed players hide ads under their play button, and ad scripts open
     * pages with `window.open` or `location`. Before the page finishes, a move to a mirror (vidfast.pro to
     * vidfast.vc) is still the player. Server redirects, and moves within the same site (www.a.com to
     * cdn.a.com), are not counted.
     */
    fun hijack(currentUrl: String?, targetUrl: String, settled: Boolean, redirect: Boolean): Boolean {
        if (!settled || redirect) return false
        val from = site(currentUrl) ?: return false
        val to = site(targetUrl) ?: return false
        return from != to
    }

    /** The last two labels of the host: `player.vidsrc.cc` is `vidsrc.cc`. */
    private fun site(url: String?): String? {
        if (url == null) return null
        val host = try {
            URI(url).host
        } catch (_: Exception) {
            null
        } ?: return null
        return host.lowercase().trim('.').split('.').takeLast(2).joinToString(".")
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
