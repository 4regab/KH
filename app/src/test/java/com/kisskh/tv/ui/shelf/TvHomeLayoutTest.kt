package com.kisskh.tv.ui.shelf

import androidx.compose.ui.unit.dp
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.ShelfRow
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.NavState
import com.kisskh.tv.ui.tabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeLayoutTest {
    private fun card(id: String) = TitleCard(Library.CINEWAVE, id, MediaKind.MOVIE, id, null, null, null, null)

    @Test fun aFocusedRowAlwaysFitsUnderTheBillboard() {
        for (shape in CardShape.values()) {
            for (h in listOf(480, 540, 720, 1080)) {
                val g = tvHomeGeometry(h.dp, shape)
                assertTrue("$shape $h", g.rowsTop.value + tvRowFocusHeight(shape).value <= h + 0.01f)
                assertTrue("billboard reaches behind the first row", g.billboardHeight > g.rowsTop)
                assertTrue(g.billboardHeight.value <= h)
            }
        }
    }

    @Test fun aChromecastScreenGetsAFullBillboardText() {
        val g = tvHomeGeometry(540.dp, CardShape.POSTER)
        assertEquals(3, billboardOverviewLines(g.textHeight))
        assertEquals(2, billboardTitleLines(g.textHeight))
        // About 58% of the screen is picture.
        assertTrue(g.billboardHeight.value in 312f..330f)
    }

    @Test fun aShortScreenDropsTheOverviewBeforeItClipsTheTitle() {
        assertEquals(0, billboardOverviewLines(100.dp))
        assertEquals(1, billboardTitleLines(100.dp))
    }

    @Test fun focusStartsOnTheSavedCardOrTheFirstOne() {
        val rows = listOf(ShelfRow("A", listOf(card("1"), card("2"))), ShelfRow("B", listOf(card("2"))))
        val saved = homeFocusKey("B", card("2"))
        assertEquals(saved, startFocusKey(rows, saved))
        assertEquals(homeFocusKey("A", card("1")), startFocusKey(rows, null))
        // A card that left the Home (a finished Continue row) falls back to the first card.
        assertEquals(homeFocusKey("A", card("1")), startFocusKey(rows, homeFocusKey("Gone", card("9"))))
        assertNull(startFocusKey(emptyList(), saved))
        assertEquals("2", cardForFocusKey(rows, saved)?.id)
    }

    @Test fun theSameTitleInTwoRowsHasTwoFocusKeys() {
        assertTrue(homeFocusKey("Continue Watching", card("1")) != homeFocusKey("Trending", card("1")))
    }

    @Test fun categoriesRemainReachableWithoutHomeChips() {
        for (library in Library.values()) {
            val nav = NavState(library = library)
            for (tab in library.tabs().filter {
                it == RootTab.SERIES || it == RootTab.MOVIES || it == RootTab.COUNTRIES || it == RootTab.ANIME
            }) {
                assertEquals("$library $tab", tab, nav.selectTab(tab).current.tab)
            }
        }
    }

    @Test fun aTabletHeroNeverTakesMoreThanSixtyPercentOfTheScreen() {
        assertEquals(480f, phoneHeroHeight(768.dp, 800.dp, CardShape.POSTER).value, 0.01f)
        // A narrow phone is limited by the card's own shape, not the screen.
        assertEquals(328f * 1.25f, phoneHeroHeight(328.dp, 800.dp, CardShape.POSTER).value, 0.01f)
    }
}
