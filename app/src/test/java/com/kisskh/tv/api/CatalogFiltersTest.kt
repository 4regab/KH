package com.kisskh.tv.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Ids and labels must match the brief and API.md "Verified catalog mapping". */
class CatalogFiltersTest {
    @Test
    fun contentTypes() {
        assertEquals(
            listOf(0 to "All", 1 to "TV Series", 2 to "Movies", 3 to "Anime", 4 to "Hollywood"),
            ContentType.entries.map { it.id to it.label },
        )
        assertEquals(
            listOf("ALL", "SERIES", "MOVIE", "ANIME", "HOLLYWOOD"),
            ContentType.entries.map { it.name },
        )
        assertEquals(ContentType.MOVIE, ContentType.fromId(2))
        assertNull(ContentType.fromId(9))
    }

    @Test
    fun countriesAreInSiteOrderWithoutId9() {
        assertEquals(listOf(2, 1, 6, 5, 8, 3, 4, 7), Country.entries.map { it.id })
        assertEquals(
            listOf("South Korea", "China", "United States", "Thailand", "Philippines", "Japan", "Hong Kong", "Taiwan"),
            Country.entries.map { it.label },
        )
        assertFalse(Country.entries.any { it.id == 9 })
        assertNull(Country.fromId(9))
        assertNull(Country.fromId(0))
        assertEquals(Country.CHINA, Country.fromId(1))
    }

    @Test
    fun countryApiNamesMatchDramaCountryStrings() {
        // API.md: detail.country strings.
        val apiNames = setOf("China", "South Korea", "Japan", "Hong Kong", "Thailand", "United States", "Taiwan", "Philippines")
        assertEquals(apiNames, Country.entries.map { it.apiName }.toSet())
        assertEquals(Country.PHILIPPINES, Country.fromApiName("Philippines"))
        assertEquals(Country.SOUTH_KOREA, Country.fromApiName(" south korea "))
        assertNull(Country.fromApiName("Indonesia"))
        assertNull(Country.fromApiName(null))
    }

    @Test
    fun statusAndSortOrder() {
        assertEquals(
            listOf(0 to "All", 1 to "Ongoing", 2 to "Completed", 3 to "Upcoming"),
            Status.entries.map { it.id to it.label },
        )
        assertEquals(
            listOf(1 to "Popular", 2 to "Last Update", 3 to "Release Date"),
            SortOrder.entries.map { it.id to it.label },
        )
        assertEquals(3, SortOrder.entries.size) // there is no rating sort
    }

    @Test
    fun defaultQueryMeansEverythingPopularFirst() {
        val q = CatalogQuery()
        assertEquals(ContentType.ALL, q.type)
        assertEquals(0, q.countryId)
        assertEquals(Status.ALL, q.status)
        assertEquals(SortOrder.POPULAR, q.order)
        assertEquals(6, CatalogQuery(country = Country.UNITED_STATES).countryId)
    }

    // ---- availableTabs (hide empty country tabs)

    private fun countSource(counts: Map<ContentType, Int?>) = FakeCatalogSource { q, _, _ ->
        val n = counts[q.type] ?: throw IOException("probe failed for ${q.type}")
        ListPage(totalCount = n)
    }

    @Test
    fun tabsLeaveOutEmptyOnes() = kotlinx.coroutines.test.runTest {
        val src = countSource(mapOf(ContentType.ALL to 12, ContentType.SERIES to 0, ContentType.MOVIE to 2))
        assertEquals(listOf(ContentType.ALL, ContentType.MOVIE), src.availableTabs(Country.UNITED_STATES))
        // each probe asks for one row of the right country
        assertTrue(src.calls.all { it.page == 1 && it.size == 1 && it.query.country == Country.UNITED_STATES })
    }

    @Test
    fun tabsAreEmptyWhenCountryHasNothing() = kotlinx.coroutines.test.runTest {
        val src = countSource(mapOf(ContentType.ALL to 0))
        assertEquals(emptyList<ContentType>(), src.availableTabs(Country.TAIWAN))
    }

    @Test
    fun failedSeriesOrMovieProbeKeepsTheTab() = kotlinx.coroutines.test.runTest {
        val src = countSource(mapOf(ContentType.ALL to 5, ContentType.MOVIE to 0)) // SERIES probe throws
        assertEquals(listOf(ContentType.ALL, ContentType.SERIES), src.availableTabs(Country.JAPAN))
    }

    @Test
    fun failedAllProbeThrows() = kotlinx.coroutines.test.runTest {
        val src = countSource(mapOf(ContentType.SERIES to 5))
        try {
            src.availableTabs(Country.JAPAN)
            fail("expected IOException")
        } catch (_: IOException) {
        }
    }
}
