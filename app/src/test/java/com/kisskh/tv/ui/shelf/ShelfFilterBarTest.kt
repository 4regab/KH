package com.kisskh.tv.ui.shelf

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.BrowseFilters
import com.kisskh.tv.api.source.BrowseSort
import com.kisskh.tv.api.source.FilterCapabilities
import com.kisskh.tv.api.source.SourceFilters
import com.kisskh.tv.api.source.TitleCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfFilterBarTest {
    private val year = 2026
    private fun caps(library: Library, kind: MediaKind) = SourceFilters.capabilities(library, kind, year)

    @Test fun theEmptyMessageNamesEveryFilterThatIsSet() {
        val caps = caps(Library.REELIX, MediaKind.MOVIE)
        val horror = caps.genres.first { it.label == "Horror" }.id
        assertEquals(
            "No Horror movies from South Korea, 2019, on Reelix.",
            emptyFilterMessage(Library.REELIX, MediaKind.MOVIE, caps, BrowseFilters(country = "KR", genre = horror, year = 2019)),
        )
    }

    @Test fun theEmptyMessageHandlesOneFilterAtATime() {
        val caps = caps(Library.CINEWAVE, MediaKind.TV)
        val drama = caps.genres.first { it.label == "Drama" }.id
        assertEquals("No Drama TV shows on CineWave.", emptyFilterMessage(Library.CINEWAVE, MediaKind.TV, caps, BrowseFilters(genre = drama)))
        assertEquals("No TV shows from Japan, on CineWave.", emptyFilterMessage(Library.CINEWAVE, MediaKind.TV, caps, BrowseFilters(country = "JP")))
        assertEquals("No TV shows from 2001, on CineWave.", emptyFilterMessage(Library.CINEWAVE, MediaKind.TV, caps, BrowseFilters(year = 2001)))
    }

    @Test fun theEmptyMessageNamesStatusAndUsesTheSourcesOwnWords() {
        val caps = caps(Library.KISSKH, MediaKind.TV)
        assertEquals(
            "No ongoing TV series from China, on KissKH.",
            emptyFilterMessage(Library.KISSKH, MediaKind.TV, caps, BrowseFilters(country = "CN", status = "ONGOING")),
        )
        assertEquals("KissKH has no TV series right now.", emptyFilterMessage(Library.KISSKH, MediaKind.TV, caps, BrowseFilters()))
    }

    @Test fun theOrderAloneNeverMakesAListEmptyInTheMessage() {
        val caps = caps(Library.CINEWAVE, MediaKind.MOVIE)
        assertEquals(
            "CineWave has no movies right now.",
            emptyFilterMessage(Library.CINEWAVE, MediaKind.MOVIE, caps, BrowseFilters(sort = BrowseSort.TOP_RATED)),
        )
    }

    @Test fun clearedKeepsTheOrderAndDropsTheRest() {
        val f = BrowseFilters(country = "KR", genre = "18", year = 2019, sort = BrowseSort.NEWEST, status = "x").cleared()
        assertFalse(f.narrowed)
        assertEquals(BrowseSort.NEWEST, f.sort)
    }

    @Test fun everyGridHasABar() {
        for (library in Library.entries) for (kind in MediaKind.entries) {
            assertTrue("$library $kind", caps(library, kind).hasBar())
        }
        assertFalse(FilterCapabilities().hasBar())
    }

    @Test fun aSearchResultSaysItsYearAndKind() {
        fun card(year: String?, kind: MediaKind) = TitleCard(Library.CINEWAVE, "1", kind, "T", null, null, year, null)
        assertEquals("2019 · Movie", searchDetail(card("2019", MediaKind.MOVIE)))
        assertEquals("TV", searchDetail(card(null, MediaKind.TV)))
        assertEquals("1998 · Anime", searchDetail(card(" 1998 ", MediaKind.ANIME)))
    }
}