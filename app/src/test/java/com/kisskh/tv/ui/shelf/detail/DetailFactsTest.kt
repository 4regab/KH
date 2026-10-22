package com.kisskh.tv.ui.shelf.detail

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.api.source.TitleDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailFactsTest {
    private fun detail(
        kind: MediaKind = MediaKind.TV,
        year: String? = null,
        meta: String? = null,
        genres: List<String> = emptyList(),
        country: String? = null,
        status: String? = null,
        runtime: Int? = null,
        rating: Double? = null,
        certification: String? = null,
    ) = TitleDetail(
        card = TitleCard(Library.KISSKH, "1", kind, "T", null, null, null, null, meta = meta),
        description = "",
        seasons = emptyList(),
        certification = certification,
        genres = genres,
        runtimeMinutes = runtime,
        year = year,
        rating = rating,
        country = country,
        status = status,
    )

    private fun episode(number: Int = 3, name: String = "", airDate: String? = null, runtime: Int? = null, dub: Boolean = false) =
        EpisodeRef("id", 1, number, name, null, dub = dub, airDate = airDate, runtimeMinutes = runtime)

    @Test
    fun runtimeReadsLikeDisneyPlus() {
        assertEquals("2h 14m", runtimeText(134))
        assertEquals("2h", runtimeText(120))
        assertEquals("45m", runtimeText(45))
        assertNull(runtimeText(0))
        assertNull(runtimeText(null))
    }

    @Test
    fun scoreHasOneDecimalAndHidesZero() {
        assertEquals("7.8", scoreText(7.84))
        assertEquals("9.0", scoreText(9.0))
        assertNull(scoreText(0.0))
        assertNull(scoreText(null))
    }

    @Test
    fun extentNamesSeasonsOrEpisodes() {
        assertEquals("3 Seasons", extentText(false, 3, 24))
        assertEquals("24 Episodes", extentText(false, 1, 24))
        assertEquals("1 Episode", extentText(false, 1, 1))
        assertEquals("2 Parts", extentText(true, 1, 2))
        assertNull(extentText(true, 1, 1))
        assertNull(extentText(false, 1, 0))
    }

    @Test
    fun emptyTitleHasNoFacts() {
        assertTrue(metaFacts(detail(), movie = false, seasonCount = 1, episodeCount = 0).isEmpty)
    }

    @Test
    fun kissKhCountryInMetaIsNotAScore() {
        val facts = metaFacts(detail(meta = "South Korea", year = "2023"), movie = false, seasonCount = 1, episodeCount = 16)
        assertNull(facts.score)
        assertEquals("2023", facts.year)
        assertEquals("16 Episodes", facts.extent)
    }

    @Test
    fun scoreFallsBackToNumericCardMeta() {
        assertEquals("9.0", metaFacts(detail(meta = "8.957"), false, 1, 0).score)
        assertEquals("7.0", metaFacts(detail(meta = "8.957", rating = 7.0), false, 1, 0).score)
    }

    @Test
    fun blankCertificationIsDropped() {
        assertNull(metaFacts(detail(certification = "  "), false, 1, 0).certification)
        assertEquals("PG-13", metaFacts(detail(certification = "PG-13"), true, 1, 1).certification)
    }

    @Test
    fun subtitleUsesThreeGenresOrFallsBack() {
        assertEquals(listOf("Drama", "Thriller", "Crime"), subtitleParts(detail(genres = listOf("Drama", "Thriller", "Crime", "Mystery"))))
        assertEquals(
            listOf("Series", "South Korea", "Ongoing"),
            subtitleParts(detail(country = "South Korea", status = "Ongoing")),
        )
        assertEquals(listOf("Movie"), subtitleParts(detail(kind = MediaKind.MOVIE)))
        assertEquals("Drama \u00B7 Crime", joinDots(listOf("Drama", "Crime")))
    }

    @Test
    fun seasonPickerSwitchesAtSeven() {
        assertEquals(SeasonPicker.NONE, seasonPickerFor(0))
        assertEquals(SeasonPicker.NONE, seasonPickerFor(1))
        assertEquals(SeasonPicker.CHIPS, seasonPickerFor(2))
        assertEquals(SeasonPicker.CHIPS, seasonPickerFor(6))
        assertEquals(SeasonPicker.DROPDOWN, seasonPickerFor(7))
    }

    @Test
    fun castInitialSkipsSymbols() {
        assertEquals("T", castInitial("tom hanks"))
        assertEquals("J", castInitial("  \"J\" Smith"))
        assertEquals("?", castInitial(""))
    }

    @Test
    fun episodeTitleKeepsNumberAndName() {
        assertEquals("3. Pilot", episodeTitle(episode(name = "Pilot")))
        assertEquals("Episode 3", episodeTitle(episode(name = "Episode 3")))
        assertEquals("Episode 3", episodeTitle(episode(name = "3")))
        assertEquals("Episode 3", episodeTitle(episode(name = " ")))
        assertEquals("Episode 3: Start", episodeTitle(episode(name = "Episode 3: Start")))
    }

    @Test
    fun unairedEpisodeSaysWhenItComes() {
        val ep = episode(airDate = "2099-03-05", runtime = 45)
        assertFalse(ep.aired("2026-01-01"))
        assertEquals(listOf("Coming Mar 5"), episodeMetaParts(ep, aired = false, watched = false, current = false))
        assertEquals("Coming soon", comingText("TBA"))
    }

    @Test
    fun airedEpisodeShowsLengthAndPlace() {
        val ep = episode(runtime = 45, dub = true)
        assertEquals(listOf("45m", "Continue", "Sub \u00B7 Dub"), episodeMetaParts(ep, true, watched = false, current = true))
        assertEquals(listOf("Watched"), episodeMetaParts(episode(), true, watched = true, current = true))
        assertEquals(listOf("42m"), episodeMetaParts(episode(), true, false, false, fallbackMinutes = 42))
        assertEquals(emptyList<String>(), episodeMetaParts(episode(), true, false, false))
    }

    @Test
    fun missingAirDateCountsAsAired() {
        assertTrue(episode(airDate = null).aired("2026-01-01"))
        assertTrue(episode(airDate = "2026-01-01").aired("2026-01-01"))
    }

    @Test
    fun notOutYetMessageNeedsAValidDate() {
        assertEquals("Not out yet. Comes out March 5, 2099.", notOutYetMessage("2099-03-05"))
        assertEquals("Not out yet.", notOutYetMessage(null))
        assertEquals("Not out yet.", notOutYetMessage("TBA"))
    }

    @Test
    fun overviewIsTrimmedOrNull() {
        assertNull(episodeOverview(episode()))
        assertEquals("Hi", episodeOverview(episode().copy(overview = "  Hi ")))
        assertNull(episodeOverview(episode().copy(overview = "   ")))
    }
}
