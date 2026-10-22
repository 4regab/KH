package com.kisskh.tv.api

import com.kisskh.tv.api.media.TmdbPaths
import com.kisskh.tv.api.source.OriginCountries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryFilterTest {
    private val today = "2026-10-18"

    @Test fun discoverHasNoCountryUnlessOneIsSet() {
        val plain = TmdbPaths.discover("movie", false, 2, today)
        assertEquals("/discover/movie?sort_by=popularity.desc&page=2", plain)
        assertFalse("with_origin_country" in plain)
        assertEquals(plain, TmdbPaths.discover("movie", false, 2, today, null))
        assertEquals(plain, TmdbPaths.discover("movie", false, 2, today, ""))
        assertEquals(plain, TmdbPaths.discover("movie", false, 2, today, "Korea")) // not an ISO code: ignored
    }

    @Test fun discoverAddsTheCountryBeforeThePage() {
        assertEquals(
            "/discover/tv?sort_by=popularity.desc&with_origin_country=KR&page=3",
            TmdbPaths.discover("tv", false, 3, today, "kr"),
        )
        val latest = TmdbPaths.discover("movie", true, 1, today, "JP")
        assertTrue(latest.endsWith("&with_origin_country=JP&page=1"))
        assertTrue("primary_release_date.lte=$today" in latest)
    }

    @Test fun theFilterListIsKissKhsCountriesPlusIndia() {
        assertEquals(
            listOf("South Korea", "China", "Japan", "Thailand", "Philippines", "Hong Kong", "Taiwan", "United States", "India"),
            OriginCountries.all.map { it.label },
        )
        assertEquals(listOf("KR", "CN", "JP", "TH", "PH", "HK", "TW", "US", "IN"), OriginCountries.all.map { it.code })
        // Every KissKH country is offered.
        assertTrue(Country.entries.all { c -> OriginCountries.all.any { it.label == c.label } })
        assertEquals("Japan", OriginCountries.byCode("jp")?.label)
        assertEquals(null, OriginCountries.byCode("XX"))
    }
}
