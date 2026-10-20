package com.kisskh.tv.ui

import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Episode
import com.kisskh.tv.api.WatchEntry
import com.kisskh.tv.ui.components.dateOnly
import com.kisskh.tv.ui.components.formatEpisodeNumber
import com.kisskh.tv.ui.components.formatPosition
import com.kisskh.tv.ui.components.yearOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The wording rules for episodes, parts and the facts line. */
class PlaybackFormatTest {
    private fun eps(vararg numbers: Float) = numbers.mapIndexed { i, n -> Episode(id = 100 + i, number = n) }

    @Test fun episodeNumbersKeepTheirFraction() {
        assertEquals("5", formatEpisodeNumber(5f))
        assertEquals("29.6", formatEpisodeNumber(29.6f))
        assertEquals("0", formatEpisodeNumber(0f))
    }

    @Test fun positionsReadAsClockTime() {
        assertEquals("12:34", formatPosition(754_000))
        assertEquals("1:06:40", formatPosition(4_000_000))
        assertEquals("0:00", formatPosition(-5))
    }

    @Test fun datesAreShownWithoutTheTime() {
        assertEquals("2026-10-08", dateOnly("2026-10-08T17:34:37"))
        assertEquals("2026", yearOf("2026-10-08T17:34:37"))
        assertNull(dateOnly(""))
        assertNull(yearOf("soon"))
        assertNull(yearOf(null))
    }

    @Test fun factsLineUsesOnlyWhatTheApiGave() {
        val series = Drama(
            id = 1, title = "A", type = "TVSeries", country = "South Korea", status = "Ongoing",
            releaseDate = "2026-10-08T17:34:37", episodes = eps(2f, 1f),
        )
        assertEquals(listOf("TV Series", "South Korea", "Ongoing", "2026", "2 episodes"), series.metaParts(fullDate = false))
        assertEquals(listOf("TV Series", "South Korea", "Ongoing", "2026-10-08", "2 episodes"), series.metaParts(fullDate = true))
        assertEquals(listOf("Movie"), Drama(id = 2, title = "B", type = "Movie", episodes = eps(1f)).metaParts(fullDate = true))
        assertEquals(listOf("Movie", "4 parts"), Drama(id = 3, title = "C", type = "Movie", episodes = eps(1f, 2f, 3f, 4f)).metaParts(true))
    }

    @Test fun playerLabelsFollowTheType() {
        val series = Drama(id = 1, title = "A", type = "TVSeries", episodes = eps(29.6f, 1f))
        assertEquals("Episode 29.6", series.episodeLabel(series.episodes[0]))
        val movie = Drama(id = 2, title = "B", type = "Movie", episodes = eps(1f))
        assertEquals("Movie", movie.episodeLabel(movie.episodes[0]))
        // Parts are numbered by order, whatever number the API gave them. The API lists newest first.
        val parts = Drama(id = 3, title = "C", type = "Movie", episodes = eps(2f, 1f))
        assertEquals("Part 2", parts.episodeLabel(parts.episodes[0]))
        assertEquals("Part 1", parts.episodeLabel(parts.episodes[1]))
    }

    @Test fun billboardLineUsesYearThenTypeCountryCountAndStatus() {
        val series = Drama(
            id = 1, title = "A", type = "TVSeries", country = "South Korea", status = "Ongoing",
            releaseDate = "2026-10-08T17:34:37", episodes = eps(2f, 1f),
        )
        assertEquals(
            "2026  •  TV Series  •  South Korea  •  2 episodes  •  Ongoing",
            series.billboardLine(),
        )
        assertEquals("Movie", Drama(id = 2, title = "B", type = "Movie", episodes = eps(1f)).billboardLine())
    }

    @Test fun playerOverlayShortensEpisodeLabelsOnly() {
        assertEquals("EP 12", playerOverlayLabel("Episode 12"))
        assertEquals("EP 29.6", playerOverlayLabel("Episode 29.6"))
        assertEquals("Movie", playerOverlayLabel("Movie"))
        assertEquals("Part 2", playerOverlayLabel("Part 2"))
    }

    @Test fun primaryActionResumesTheSavedEpisode() {
        val series = Drama(id = 1, title = "A", type = "TVSeries", episodes = eps(1f, 2f, 3f))
        val ordered = series.orderedEpisodes()
        val entry = WatchEntry(1, ordered[1].id, 2f, 40_000, 100_000, 5, "A", null, finished = false)
        val action = primaryAction(series, ordered, entry)!!
        assertEquals("Resume EP 2", action.label)
        assertEquals(ordered[1].id, action.episode!!.id)
    }

    @Test fun primaryActionOffersTheNextEpisodeAfterAFinishedOne() {
        val series = Drama(id = 1, title = "A", type = "TVSeries", episodes = eps(1f, 2f))
        val ordered = series.orderedEpisodes()
        val entry = WatchEntry(1, ordered[0].id, 1f, 0, 100_000, 5, "A", null, finished = true)
        val action = primaryAction(series, ordered, entry)!!
        assertEquals("Play EP 2", action.label)
        assertEquals(ordered[1].id, action.episode!!.id)
    }

    @Test fun primaryActionForASingleMovieDoesNotInventParts() {
        val movie = Drama(id = 2, title = "B", type = "Movie", episodes = eps(1f))
        val action = primaryAction(movie, movie.orderedEpisodes(), null)!!
        assertEquals("Watch movie", action.label)
        assertNull(action.episode)
    }

    @Test fun primaryActionIsNullWhenThereAreNoEpisodes() {
        val drama = Drama(id = 3, title = "C", type = "TVSeries")
        assertNull(primaryAction(drama, emptyList(), null))
    }
}
