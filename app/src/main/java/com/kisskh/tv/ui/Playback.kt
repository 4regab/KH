package com.kisskh.tv.ui

import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Episode
import com.kisskh.tv.ui.components.dateOnly
import com.kisskh.tv.ui.components.formatEpisodeNumber
import com.kisskh.tv.ui.components.yearOf

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
