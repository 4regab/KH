package com.kisskh.tv.ui.shelf

import com.kisskh.tv.api.MY_LIST_ROW
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.BrowseFilters
import com.kisskh.tv.api.source.CONTINUE_ROW
import com.kisskh.tv.api.source.ShelfRow
import com.kisskh.tv.api.source.TitleCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShelfRowsTest {
    private fun row(title: String) = ShelfRow(title, listOf(TitleCard(Library.CINEWAVE, title, MediaKind.MOVIE, title, null, null, null, null)))

    @Test fun myListSitsRightAfterContinueWatching() {
        val rows = mergeHomeRows(row(CONTINUE_ROW), row(MY_LIST_ROW), listOf(row("Popular"), row("Trending")))
        assertEquals(listOf(CONTINUE_ROW, MY_LIST_ROW, "Popular", "Trending"), rows.map { it.title })
    }

    @Test fun missingRowsAreLeftOutAndOldCopiesNeverRepeat() {
        val old = listOf(row(CONTINUE_ROW), row(MY_LIST_ROW), row("Popular"))
        assertEquals(listOf("Popular"), mergeHomeRows(null, null, old).map { it.title })
        assertEquals(listOf(MY_LIST_ROW, "Popular"), mergeHomeRows(null, row(MY_LIST_ROW), old).map { it.title })
    }

    @Test fun eachCountryHasItsOwnGridKey() {
        fun key(library: Library, kind: MediaKind, country: String?) = browseKey(library, kind, BrowseFilters(country = country))
        val all = key(Library.CINEWAVE, MediaKind.MOVIE, null)
        val kr = key(Library.CINEWAVE, MediaKind.MOVIE, "KR")
        assertNotEquals(all, kr)
        assertNotEquals(kr, key(Library.CINEWAVE, MediaKind.MOVIE, "JP"))
        assertNotEquals(kr, key(Library.CINEJOY, MediaKind.MOVIE, "KR"))
        assertNotEquals(kr, key(Library.CINEWAVE, MediaKind.TV, "KR"))
        assertEquals(kr, key(Library.CINEWAVE, MediaKind.MOVIE, "kr"))
    }
}
