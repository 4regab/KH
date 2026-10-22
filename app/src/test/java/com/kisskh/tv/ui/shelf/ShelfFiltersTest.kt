package com.kisskh.tv.ui.shelf

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.BrowseFilters
import com.kisskh.tv.api.source.BrowseSort
import com.kisskh.tv.api.source.SourceFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShelfFiltersTest {
    private val year = 2026

    @Test fun everyDistinctFilterSetIsItsOwnList() {
        val plain = browseKey(Library.CINEWAVE, MediaKind.MOVIE, BrowseFilters())
        val drama = browseKey(Library.CINEWAVE, MediaKind.MOVIE, BrowseFilters(genre = "18"))
        val dramaTop = browseKey(Library.CINEWAVE, MediaKind.MOVIE, BrowseFilters(genre = "18", sort = BrowseSort.TOP_RATED))
        assertEquals(3, setOf(plain, drama, dramaTop).size)
        // Same filters, other grid: other list.
        assertNotEquals(drama, browseKey(Library.CINEJOY, MediaKind.MOVIE, BrowseFilters(genre = "18")))
        assertNotEquals(drama, browseKey(Library.CINEWAVE, MediaKind.TV, BrowseFilters(genre = "18")))
    }

    @Test fun aCountryCodeHasOneKeyWhateverItsCaseAndNoFilterIsAll() {
        assertEquals(
            browseKey(Library.CINEWAVE, MediaKind.MOVIE, BrowseFilters(country = "KR")),
            browseKey(Library.CINEWAVE, MediaKind.MOVIE, BrowseFilters(country = "kr")),
        )
        assertEquals("cw:MOVIE:all", browseKey(Library.CINEWAVE, MediaKind.MOVIE, BrowseFilters()))
    }

    @Test fun valuesTheGridCannotFilterShareTheKeyOfTheCleanGrid() {
        // The session normalizes before it builds a key, so a stray value never splits a grid's state.
        fun key(f: BrowseFilters) = browseKey(Library.NEXUS, MediaKind.ANIME, SourceFilters.normalize(Library.NEXUS, MediaKind.ANIME, f, year))
        assertEquals(key(BrowseFilters()), key(BrowseFilters(country = "KR", year = 2019, sort = BrowseSort.TOP_RATED)))
        assertNotEquals(key(BrowseFilters()), key(BrowseFilters(status = "Currently Airing")))
    }
}
