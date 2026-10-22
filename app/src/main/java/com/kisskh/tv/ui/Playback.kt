package com.kisskh.tv.ui

import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Episode
import com.kisskh.tv.ui.components.formatEpisodeNumber
import kotlin.math.floor

/*
 * Small rules about a title's episodes and how to label them.
 * Type comes from the detail's `type` field, never from the episode count.
 */

fun Drama.isMovie(): Boolean = type.equals("Movie", ignoreCase = true)

/** The API lists episodes newest first. This is the order people watch them. */
fun Drama.orderedEpisodes(): List<Episode> = episodes.sortedBy { it.number }

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

/** "Episode 12" -> "EP 12". Movies and parts keep their own labels. */
fun playerOverlayLabel(label: String): String {
    val prefix = "Episode "
    return if (label.startsWith(prefix)) "EP ${label.removePrefix(prefix)}" else label
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
