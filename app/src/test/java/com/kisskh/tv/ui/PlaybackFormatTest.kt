package com.kisskh.tv.ui

import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Episode
import com.kisskh.tv.ui.components.dateOnly
import com.kisskh.tv.ui.components.formatEpisodeNumber
import com.kisskh.tv.ui.components.formatPosition
import com.kisskh.tv.ui.components.yearOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The wording rules for episodes and parts. */
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

    @Test fun playerOverlayShortensEpisodeLabelsOnly() {
        assertEquals("EP 12", playerOverlayLabel("Episode 12"))
        assertEquals("EP 29.6", playerOverlayLabel("Episode 29.6"))
        assertEquals("Movie", playerOverlayLabel("Movie"))
        assertEquals("Part 2", playerOverlayLabel("Part 2"))
    }
}
