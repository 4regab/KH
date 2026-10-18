package com.kisskh.tv.ui

import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Episode
import com.kisskh.tv.api.Show
import com.kisskh.tv.api.WatchEntry
import com.kisskh.tv.ui.components.dateOnly
import com.kisskh.tv.ui.components.formatEpisodeNumber
import com.kisskh.tv.ui.components.yearOf
import kotlin.math.floor

/*
 * Small rules about a title's episodes and how to describe it. Used by Home and Detail.
 * Type comes from the detail's `type` field, never from the episode count.
 */

fun Drama.isMovie(): Boolean = type.equals("Movie", ignoreCase = true)

/** The API lists episodes newest first. This is the order people watch them. */
fun Drama.orderedEpisodes(): List<Episode> = episodes.sortedBy { it.number }

/** "TVSeries" -> "TV Series". Other types are shown as the site names them. */
fun Drama.typeLabel(): String? = when {
    type.isNullOrBlank() -> null
    type.equals("TVSeries", ignoreCase = true) -> "TV Series"
    else -> type.trim()
}

/** How many episodes (or parts, for a movie) the title has. */
fun Drama.episodeTotal(): Int = if (episodes.isNotEmpty()) episodes.size else episodesCount

private fun Drama.countLabel(): String? {
    val n = episodeTotal()
    return when {
        isMovie() -> if (n > 1) "$n parts" else null
        n == 1 -> "1 episode"
        n > 1 -> "$n episodes"
        else -> null
    }
}

/**
 * Facts for the line under a title: type, country, status, then the year ([fullDate] false) or the
 * release date ([fullDate] true), then the episode count. Only parts the API gave.
 */
fun Drama.metaParts(fullDate: Boolean): List<String> = listOfNotNull(
    typeLabel(),
    country?.trim()?.takeIf { it.isNotEmpty() },
    status?.trim()?.takeIf { it.isNotEmpty() },
    if (fullDate) dateOnly(releaseDate) else yearOf(releaseDate),
    countLabel(),
)

/** Player title bar text for an episode: "Episode 5", "Movie", or "Part 2" for a multi-part movie. */
fun Drama.episodeLabel(episode: Episode): String {
    if (!isMovie()) return "Episode ${formatEpisodeNumber(episode.number)}"
    val ordered = orderedEpisodes()
    return if (ordered.size <= 1) "Movie" else "Part ${ordered.indexOfFirst { it.id == episode.id } + 1}"
}

fun Drama.playerDest(episode: Episode): Dest.Player = Dest.Player(
    episodeId = episode.id,
    dramaId = id,
    title = title.orEmpty(),
    label = episodeLabel(episode),
    episodeNumber = episode.number,
    thumbnail = thumbnail,
)

/**
 * Facts on the billboard: year, type, country, then episode count and status.
 * Only parts the detail call actually returned.
 */
fun Drama.billboardLine(): String = listOfNotNull(
    yearOf(releaseDate),
    typeLabel(),
    country?.trim()?.takeIf { it.isNotEmpty() },
    countLabel(),
    status?.trim()?.takeIf { it.isNotEmpty() },
).joinToString("  •  ")

/** What a home row can say before the detail call comes back. Null when the row item has no count. */
fun Show.fallbackLine(): String? = when (episodesCount) {
    0 -> null
    1 -> "1 episode"
    else -> "$episodesCount episodes"
}

/** "Episode 12" -> "EP 12". Movies and parts keep their own labels. */
fun playerOverlayLabel(label: String): String {
    val prefix = "Episode "
    return if (label.startsWith(prefix)) "EP ${label.removePrefix(prefix)}" else label
}

/**
 * The big button on a title page, and what it plays.
 * Saved progress on an unfinished episode resumes it. A finished one offers the next episode.
 * Nothing saved: the first (lowest-numbered) episode, resolved on press because later ones may not be out.
 * A null episode means "the first one that is out".
 */
data class PrimaryAction(val label: String, val episode: Episode?, val name: String)

fun primaryAction(drama: Drama, ordered: List<Episode>, entry: WatchEntry?): PrimaryAction? {
    if (ordered.isEmpty()) return null
    val movie = drama.isMovie()
    val saved = entry?.let { e -> ordered.firstOrNull { it.id == e.episodeId } }
    if (entry != null && saved != null) {
        if (entry.resumable) {
            return when {
                !movie -> "EP ${formatEpisodeNumber(saved.number)}".let { PrimaryAction("Resume $it", saved, it) }
                ordered.size > 1 -> "Part ${ordered.indexOf(saved) + 1}".let { PrimaryAction("Resume $it", saved, it) }
                else -> PrimaryAction("Resume", saved, "the movie")
            }
        }
        if (!movie) {
            ordered.firstOrNull { it.number > saved.number }?.let {
                return "EP ${formatEpisodeNumber(it.number)}".let { n -> PrimaryAction("Play $n", it, n) }
            }
        }
    }
    if (movie) return PrimaryAction("Watch movie", null, "the movie")
    val first = "EP ${formatEpisodeNumber(ordered.first().number)}"
    return PrimaryAction("Play $first", null, first)
}

/** One block of a long episode list: "1-50", "51-100", and the episodes whose numbers fall in it. */
data class EpisodeRange(val start: Int, val end: Int, val episodes: List<Episode>)

/**
 * Groups [episodes] into blocks of [bucketSize] by episode number, not by list position.
 * 29.6 stays in 1-50. 51 starts 51-100. Empty blocks (gaps in the numbering) are left out.
 * Episodes inside a block are in watch order (lowest number first).
 */
fun episodeRanges(episodes: List<Episode>, bucketSize: Int = 50): List<EpisodeRange> {
    if (episodes.isEmpty() || bucketSize <= 0) return emptyList()
    val grouped = episodes.groupBy { rangeStart(it.number, bucketSize) }
    return grouped.keys.sorted().map { start ->
        EpisodeRange(start, start + bucketSize - 1, grouped.getValue(start).sortedBy { it.number })
    }
}

/** First number of the block that contains [number]. */
fun rangeStart(number: Float, bucketSize: Int = 50): Int {
    val whole = floor(number.toDouble()).toInt()
    val index = (whole - 1).coerceAtLeast(0) / bucketSize
    return index * bucketSize + 1
}

/** "EP 12" for the player chrome. */
fun compactEpisodeLabel(episode: Episode, parts: Boolean, partIndex: Int): String =
    if (parts) "Part ${partIndex + 1}" else "EP ${formatEpisodeNumber(episode.number)}"
