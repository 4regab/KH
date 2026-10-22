package com.kisskh.tv.api

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbJson
import com.kisskh.tv.api.source.TitleCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailabilityTest {
    private val day = 24L * 60 * 60 * 1000
    private val prefs = FakePrefs()
    private var now = 1_000_000L
    private fun store(p: FakePrefs = prefs) = Availability(p) { now }

    private fun card(date: String? = null, id: String = "550", kind: MediaKind = MediaKind.MOVIE) =
        TitleCard(Library.CINEWAVE, id, kind, "Title", null, null, "2026", null, releaseDate = date)

    // ---------------------------------------------------------------- release dates

    @Test fun dateAfterTodayIsUnreleased() {
        assertTrue(ReleaseDates.isUnreleased("2026-12-16", today = "2026-10-22"))
        assertTrue(ReleaseDates.isUnreleased("2026-10-23", today = "2026-10-22"))
    }

    @Test fun todayAndEarlierAreReleased() {
        assertFalse(ReleaseDates.isUnreleased("2026-10-22", today = "2026-10-22"))
        assertFalse(ReleaseDates.isUnreleased("2019-04-26", today = "2026-10-22"))
    }

    @Test fun missingOrOddDateIsNeverMarked() {
        assertFalse(ReleaseDates.isUnreleased(null, today = "2026-10-22"))
        assertFalse(ReleaseDates.isUnreleased("", today = "2026-10-22"))
        assertFalse(ReleaseDates.isUnreleased("2027", today = "2026-10-22"))
        assertFalse(ReleaseDates.isUnreleased("2027-13-40", today = "2026-10-22"))
    }

    @Test fun datesReadWell() {
        assertEquals("Dec 16", ReleaseDates.short("2026-12-16"))
        assertEquals("December 16, 2026", ReleaseDates.long("2026-12-16"))
        assertEquals("Jan 5", ReleaseDates.short("2027-01-05"))
        assertNull(ReleaseDates.short("soon"))
        assertNull(ReleaseDates.long(null))
    }

    @Test fun tmdbKeepsTheFullDateForMoviesAndShows() {
        val json = """{"page":1,"total_pages":1,"results":[
            {"id":1003596,"title":"Avengers: Doomsday","release_date":"2026-12-16","media_type":"movie"},
            {"id":7,"name":"Some Show","first_air_date":"2027-02-01","media_type":"tv"},
            {"id":8,"title":"No Date","release_date":"","media_type":"movie"}]}"""
        val items = TmdbJson.page(json, null).items
        assertEquals("2026-12-16", items[0].releaseDate)
        assertEquals("2027-02-01", items[1].releaseDate)
        assertNull(items[2].releaseDate)
    }

    @Test fun tmdbDetailsKeepTheFullDate() {
        val json = """{"id":1003596,"title":"Avengers: Doomsday","release_date":"2026-12-16"}"""
        assertEquals("2026-12-16", TmdbJson.details(json, "movie").title.releaseDate)
    }

    // ---------------------------------------------------------------- marks on a card

    @Test fun unreleasedWinsOverUnavailable() {
        val mark = AvailabilityRules.markFor(card("2026-12-16"), unavailable = true, today = "2026-10-22")
        assertEquals(TitleMark.ComingSoon("2026-12-16"), mark)
    }

    @Test fun unavailableOnlyWhenLearned() {
        assertEquals(TitleMark.Unavailable, AvailabilityRules.markFor(card("2020-01-01"), unavailable = true, today = "2026-10-22"))
        assertNull(AvailabilityRules.markFor(card("2020-01-01"), unavailable = false, today = "2026-10-22"))
        assertNull(AvailabilityRules.markFor(card(null), unavailable = false, today = "2026-10-22"))
    }

    // ---------------------------------------------------------------- expiry

    @Test fun markRunsOutAfterThreeDays() {
        assertTrue(AvailabilityRules.fresh(1_000L, 1_000L))
        assertTrue(AvailabilityRules.fresh(1_000L, 1_000L + 3 * day - 1))
        assertFalse(AvailabilityRules.fresh(1_000L, 1_000L + 3 * day))
        assertFalse(AvailabilityRules.fresh(null, 5L))
    }

    @Test fun markFromTheFutureDoesNotCount() {
        assertFalse(AvailabilityRules.fresh(2_000L, 1_000L))
    }

    // ---------------------------------------------------------------- the store

    @Test fun markThenClear() {
        val s = store()
        val key = availabilityKey("cw", MediaKind.MOVIE, "1748404")
        assertFalse(s.isUnavailable(key))
        s.markUnavailable(key)
        assertTrue(s.isUnavailable(key))
        s.clear(key)
        assertFalse(s.isUnavailable(key))
        assertFalse(prefs.contains(key))
    }

    @Test fun markExpiresWhileRunning() {
        val s = store()
        val key = availabilityKey("cw", MediaKind.MOVIE, "1")
        s.markUnavailable(key)
        now += 3 * day - 1
        assertTrue(s.isUnavailable(key))
        now += 1
        assertFalse(s.isUnavailable(key))
    }

    @Test fun marksSurviveARestartButExpiredOnesAreDropped() {
        val keep = availabilityKey("cw", MediaKind.MOVIE, "1")
        val old = availabilityKey("cw", MediaKind.MOVIE, "2")
        store().apply {
            markUnavailable(old)
            now += 2 * day
            markUnavailable(keep)
        }
        now += day + 1 // "old" is now three days and a bit old, "keep" is a day old
        val again = store()
        assertTrue(again.isUnavailable(keep))
        assertFalse(again.isUnavailable(old))
        assertFalse(prefs.contains(old))
    }

    @Test fun seriesAndAnimeShareOneMarkAndMovieIsSeparate() {
        val s = store()
        s.markUnavailable(availabilityKey("cw", MediaKind.ANIME, "9"))
        assertEquals(TitleMark.Unavailable, s.markFor(card(id = "9", kind = MediaKind.TV)))
        assertNull(s.markFor(card(id = "9", kind = MediaKind.MOVIE)))
    }

    @Test fun versionGoesUpOnlyWhenSomethingChanges() {
        val s = store()
        val key = availabilityKey("cw", MediaKind.MOVIE, "1")
        val start = s.version.value
        s.clear(key) // nothing to clear
        assertEquals(start, s.version.value)
        s.markUnavailable(key)
        assertEquals(start + 1, s.version.value)
        s.clear(key)
        assertEquals(start + 2, s.version.value)
    }
}
