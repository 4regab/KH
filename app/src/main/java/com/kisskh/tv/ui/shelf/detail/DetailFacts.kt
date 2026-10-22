package com.kisskh.tv.ui.shelf.detail

import com.kisskh.tv.api.ReleaseDates
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.TitleDetail
import java.util.Locale

/*
 * The plain-text rules of the title page: what the facts line says, what an episode row says,
 * which season picker to draw. No Compose in here, so it can be tested on the JVM.
 * Every field of a title can be empty. Each function leaves a missing fact out instead of
 * printing a blank.
 */

private const val DOT = "\u00B7"

/** "2h 14m", "2h" or "45m". Null for a missing or zero length. */
internal fun runtimeText(minutes: Int?): String? {
    val m = minutes?.takeIf { it > 0 } ?: return null
    return when {
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }
}

/** A score out of 10 with one decimal ("7.8"). Null for a missing or zero score. */
internal fun scoreText(rating: Double?): String? =
    rating?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) }

/** "3 Seasons", "24 Episodes" or "2 Parts". A show with several seasons names the seasons, since its episode count is only for one. */
internal fun extentText(movie: Boolean, seasonCount: Int, episodeCount: Int): String? = when {
    movie -> if (episodeCount > 1) "$episodeCount Parts" else null
    seasonCount > 1 -> "$seasonCount Seasons"
    episodeCount == 1 -> "1 Episode"
    episodeCount > 1 -> "$episodeCount Episodes"
    else -> null
}

/** The facts under the title, already worded. Each one is null when the source does not know it. */
internal data class MetaFacts(
    val year: String?,
    val certification: String?,
    val runtime: String?,
    val extent: String?,
    val score: String?,
) {
    val isEmpty: Boolean get() = year == null && certification == null && runtime == null && extent == null && score == null
}

internal fun metaFacts(detail: TitleDetail, movie: Boolean, seasonCount: Int, episodeCount: Int): MetaFacts {
    val card = detail.card
    return MetaFacts(
        year = (detail.year ?: card.year)?.trim()?.takeIf { it.isNotEmpty() },
        certification = detail.certification?.trim()?.takeIf { it.isNotEmpty() },
        runtime = runtimeText(detail.runtimeMinutes),
        extent = extentText(movie, seasonCount, episodeCount),
        // A few sources keep the score as text in card.meta; KissKH keeps the country there, which is not a number.
        score = scoreText(detail.rating ?: card.meta?.trim()?.toDoubleOrNull()),
    )
}

internal fun kindText(kind: MediaKind): String = when (kind) {
    MediaKind.MOVIE -> "Movie"
    MediaKind.TV -> "Series"
    MediaKind.ANIME -> "Anime"
}

/**
 * The line under the facts: up to three genres. A source with no genres (KissKH) falls back to the
 * kind, country and status, so the page still says something about the title.
 */
internal fun subtitleParts(detail: TitleDetail): List<String> {
    val genres = detail.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
    if (genres.isNotEmpty()) return genres
    return listOfNotNull(
        kindText(detail.card.kind),
        detail.country?.trim()?.takeIf { it.isNotEmpty() },
        detail.status?.trim()?.takeIf { it.isNotEmpty() },
    )
}

internal fun joinDots(parts: List<String>): String = parts.joinToString(" $DOT ")

/** How seasons are chosen: nothing for one season, chips up to six, one dropdown above that. */
internal enum class SeasonPicker { NONE, CHIPS, DROPDOWN }

internal const val MAX_SEASON_CHIPS = 6

internal fun seasonPickerFor(seasonCount: Int): SeasonPicker = when {
    seasonCount <= 1 -> SeasonPicker.NONE
    seasonCount <= MAX_SEASON_CHIPS -> SeasonPicker.CHIPS
    else -> SeasonPicker.DROPDOWN
}

/** The letter shown in a cast circle with no photo. */
internal fun castInitial(name: String): String =
    name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

/** The Toast text for something that is not out yet. [date] is yyyy-MM-dd. */
internal fun notOutYetMessage(date: String?): String {
    val long = ReleaseDates.long(date)
    return if (long != null) "Not out yet. Comes out $long." else "Not out yet."
}

/** "3. Pilot", or "Episode 3" when the name is only the number. */
internal fun episodeTitle(episode: EpisodeRef): String {
    val name = episode.name.trim()
    if (name.isBlank() || name.equals("Episode ${episode.number}", true) || name == episode.number.toString()) return "Episode ${episode.number}"
    if (name.startsWith("Episode", true)) return name
    return "${episode.number}. $name"
}

/** An episode's identity in the list, without its position. */
internal fun episodeKey(ep: EpisodeRef): String = "ep:${ep.season}:${ep.id}:${ep.number}"

/** "Coming Mar 5", or "Coming soon" when the date is not a date. */
internal fun comingText(airDate: String?): String = ReleaseDates.short(airDate)?.let { "Coming $it" } ?: "Coming soon"

/**
 * The small facts line of an episode row. An unaired episode says when it comes; an aired one says
 * its length (or [fallbackMinutes], the show's usual length) and where the viewer is.
 */
internal fun episodeMetaParts(
    episode: EpisodeRef,
    aired: Boolean,
    watched: Boolean,
    current: Boolean,
    fallbackMinutes: Int? = null,
): List<String> {
    if (!aired) return listOf(comingText(episode.airDate))
    return listOfNotNull(
        runtimeText(episode.runtimeMinutes ?: fallbackMinutes),
        if (watched) "Watched" else if (current) "Continue" else null,
        if (episode.dub) "Sub $DOT Dub" else null,
    )
}

/** The episode's synopsis, or null when the source has none. */
internal fun episodeOverview(episode: EpisodeRef): String? = episode.overview?.trim()?.takeIf { it.isNotEmpty() }
