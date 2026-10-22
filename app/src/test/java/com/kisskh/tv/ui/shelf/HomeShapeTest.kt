package com.kisskh.tv.ui.shelf

import com.kisskh.tv.api.MY_LIST_ROW
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.CONTINUE_ROW
import com.kisskh.tv.api.source.ShelfRow
import com.kisskh.tv.api.source.TitleCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShapeTest {
    private fun card(
        library: Library,
        id: String,
        poster: String?,
        backdrop: String?,
        kind: MediaKind = MediaKind.TV,
        resume: Boolean = false,
        season: Int = 0,
        number: Int = 0,
    ) = TitleCard(
        library, id, kind, id, poster, backdrop, null, null,
        resumeEpisode = if (resume) number.toString() else null,
        resumeSeason = season,
        resumeNumber = number,
    )

    @Test fun animePostersAreRealPostersEvenWithoutABackdrop() {
        assertTrue(card(Library.ANISUGE, "a", "https://cdn/p.png", null, MediaKind.ANIME).hasPoster())
        assertTrue(card(Library.NEXUS, "n", "https://anime.delivery/p.avif", null, MediaKind.ANIME).hasPoster())
        // A banner-only slide and a KissKH still (same picture twice) are not posters.
        assertFalse(card(Library.ANISUGE, "s", null, "https://banner.jpg", MediaKind.ANIME).hasPoster())
        assertFalse(card(Library.KISSKH, "k", "https://k/t.jpg", "https://k/t.jpg").hasPoster())
    }

    @Test fun oneShapeForTheWholeHomeIgnoresContinueAndMyList() {
        // Continue cards store the same picture twice, so on their own they look "landscape".
        val continueRow = ShelfRow(CONTINUE_ROW, listOf(card(Library.NEXUS, "c", "https://p.avif", "https://p.avif", resume = true, number = 3)))
        val myList = ShelfRow(MY_LIST_ROW, listOf(card(Library.NEXUS, "m", "https://p2.avif", "https://p2.avif")))
        val posters = ShelfRow("Popular", listOf(card(Library.NEXUS, "x", "https://x.avif", null), card(Library.NEXUS, "y", "https://y.avif", null)))
        assertEquals(CardShape.POSTER, homeShape(listOf(continueRow, myList, posters)))

        val stills = ShelfRow("Latest", listOf(card(Library.KISSKH, "k1", "https://k/1.jpg", "https://k/1.jpg")))
        assertEquals(CardShape.LANDSCAPE, homeShape(listOf(continueRow, stills)))
    }

    @Test fun skeletonHintMatchesTheSourcesThatHavePosters() {
        assertEquals(CardShape.LANDSCAPE, Library.KISSKH.shapeHint())
        assertEquals(CardShape.POSTER, Library.NEXUS.shapeHint())
        assertEquals(CardShape.POSTER, Library.ANISUGE.shapeHint())
    }

    @Test fun continueBadgeNamesTheEpisodeOrMovie() {
        assertEquals("Movie", continueBadge(card(Library.CINEWAVE, "1", "p", "b", MediaKind.MOVIE, resume = true, number = 1)))
        assertEquals("S1 E2", continueBadge(card(Library.CINEWAVE, "2", "p", "b", MediaKind.TV, resume = true, season = 1, number = 2)))
        assertEquals("E5", continueBadge(card(Library.NEXUS, "3", "p", null, MediaKind.ANIME, resume = true, season = 1, number = 5)))
        assertEquals("E7", continueBadge(card(Library.KISSKH, "4", "p", "p", MediaKind.TV, resume = true, season = 1, number = 7)))
        assertNull(continueBadge(card(Library.CINEWAVE, "5", "p", "b")))
    }

    @Test fun heroFollowsTheHomeShape() {
        val stillOnly = card(Library.ANISUGE, "s", null, "https://banner.jpg", MediaKind.ANIME)
        val withPoster = card(Library.ANISUGE, "p", "https://poster.png", null, MediaKind.ANIME)
        val rows = listOf(
            ShelfRow(CONTINUE_ROW, listOf(card(Library.ANISUGE, "c", "https://c.png", "https://c.png"))),
            ShelfRow("Featured", listOf(stillOnly, withPoster)),
        )
        assertEquals("p", pickHero(rows, CardShape.POSTER)?.id)
        assertEquals("s", pickHero(rows, CardShape.LANDSCAPE)?.id)
        assertNull(pickHero(listOf(ShelfRow(CONTINUE_ROW, rows[0].items)), CardShape.POSTER))
    }
}
