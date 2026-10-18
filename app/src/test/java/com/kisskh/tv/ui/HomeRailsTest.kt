package com.kisskh.tv.ui

import com.kisskh.tv.ui.home.HomeRails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRailsTest {
    @Test fun feedsAreTheVerifiedRowsInOrder() {
        assertEquals(
            listOf(
                "Latest",
                "Popular",
                "Top Rating",
                "Anime",
                "Upcoming",
                "Korean Series",
                "Chinese Series",
                "Movies",
            ),
            HomeRails.feeds,
        )
    }

    @Test fun continueWatchingIsFirstAndTheTotalStaysWithinNine() {
        assertEquals(HomeRails.feeds, HomeRails.titles(continueWatching = false))
        val withHistory = HomeRails.titles(continueWatching = true)
        assertEquals("Continue Watching", withHistory.first())
        assertEquals(HomeRails.feeds, withHistory.drop(1))
        assertTrue(withHistory.size <= 9)
        assertFalse(withHistory.groupingBy { it }.eachCount().any { it.value > 1 })
    }
}
