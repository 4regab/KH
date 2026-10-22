package com.kisskh.tv.api.source

import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKeys
import com.kisskh.tv.api.media.MediaKind

/**
 * One title, whatever site it came from. [id] is the site's own id (TMDB number, Anisuge slug,
 * Anime Nexus UUID). [slug] is only set when the site's page URL is not the id.
 */
data class TitleCard(
    val source: Library,
    val id: String,
    val kind: MediaKind,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val year: String?,
    val overview: String?,
    val slug: String = "",
    val meta: String? = null,
    /** Set on Continue cards: KissKH episode id, or the episode number for every other source. */
    val resumeEpisode: String? = null,
    val resumeSeason: Int = 0,
    val resumeNumber: Int = 0,
    /** How far the viewer got (0..1), on Continue cards whose position is known. */
    val progress: Float? = null,
) {
    val artwork: String? get() = backdrop ?: poster
}

data class ShelfRow(val title: String, val items: List<TitleCard>)

/** Title of the row of titles the viewer started. Home puts it first. */
const val CONTINUE_ROW = "Continue Watching"

data class ShelfPage(val items: List<TitleCard>, val page: Int, val hasMore: Boolean)

data class EpisodeRef(
    val id: String,
    val season: Int,
    val number: Int,
    val name: String,
    val still: String?,
    val slug: String = "",
    /** Anisuge's per-episode server bundle. Empty everywhere else. */
    val token: String = "",
    val sub: Boolean = true,
    val dub: Boolean = false,
)

data class SeasonBlock(val number: Int, val name: String, val episodes: List<EpisodeRef>)

data class TitleDetail(
    val card: TitleCard,
    val description: String,
    val seasons: List<SeasonBlock>,
    val certification: String? = null,
) {
    val playable: EpisodeRef?
        get() = seasons.firstOrNull()?.episodes?.firstOrNull()

    fun episode(season: Int, number: Int): EpisodeRef? =
        seasons.firstOrNull { it.number == season }?.episodes?.firstOrNull { it.number == number }
            ?: seasons.flatMap { it.episodes }.firstOrNull { it.number == number && (season <= 0 || it.season == season) }

    fun neighbors(episode: EpisodeRef): Pair<EpisodeRef?, EpisodeRef?> {
        val all = seasons.flatMap { it.episodes }
        val index = all.indexOfFirst { it.id == episode.id && it.season == episode.season }
        if (index < 0) return null to null
        return all.getOrNull(index - 1) to all.getOrNull(index + 1)
    }
}

/**
 * One way to play the current episode. [direct] is an HLS or MP4 URL for the native player.
 * [page] is loaded when there is no direct URL: the page is sniffed for a stream, and if that
 * fails the page itself stays on screen.
 */
data class PlayTarget(
    val name: String,
    val audio: String = "",
    val page: String? = null,
    val referer: String,
    val direct: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subs: List<SubtitleFile> = emptyList(),
    val subsUrl: String? = null,
    val sniff: Boolean = true,
)

data class PlayPlan(
    val title: String,
    val label: String,
    val poster: String?,
    val progressKey: String,
    val targets: List<PlayTarget>,
    val next: EpisodeRef? = null,
    val previous: EpisodeRef? = null,
)

fun progressKey(source: String, kind: MediaKind, id: String, season: Int, episode: Int): String =
    if (kind == MediaKind.MOVIE) MediaKeys.movie(source, id)
    else MediaKeys.episode(source, id, season.coerceAtLeast(1), episode.coerceAtLeast(1))

object HtmlText {
    fun attr(tag: String, name: String): String? {
        val match = Regex("""(?:^|\s)${Regex.escape(name)}\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(tag)
        return match?.groupValues?.get(1)?.let(::unescape)
    }

    fun openingTags(html: String, name: String): List<String> =
        Regex("""<$name\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).map { it.value }.toList()

    fun unescape(raw: String): String {
        val named = raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        return Regex("""&#(\d+);""").replace(named) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value }
    }

    fun paragraph(html: String, className: String): String? {
        val body = Regex("""<p[^>]*class="$className"[^>]*>([\s\S]*?)</p>""").find(html)?.groupValues?.get(1) ?: return null
        val text = unescape(body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")).trim()
        return text.ifBlank { null }
    }
}
