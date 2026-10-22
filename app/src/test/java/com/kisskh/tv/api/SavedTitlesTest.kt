package com.kisskh.tv.api

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.TitleCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedTitlesTest {
    private val prefs = FakePrefs()
    private var now = 1_000L
    private fun store(p: FakePrefs = prefs) = SavedTitles(p) { now++ }

    private fun card(id: String, name: String = "Title $id", kind: MediaKind = MediaKind.TV, source: Library = Library.CINEWAVE) =
        TitleCard(source, id, kind, name, "https://img/$id-p.jpg", "https://img/$id-b.jpg", "2020", "story", slug = "slug-$id")

    @Test fun keyIsSourceKindIdAndSeriesAndAnimeShareOne() {
        assertEquals("cw:movie:550", savedKey(card("550", kind = MediaKind.MOVIE)))
        assertEquals("cw:tv:550", savedKey(card("550", kind = MediaKind.TV)))
        assertEquals(savedKey(card("1", kind = MediaKind.TV)), savedKey(card("1", kind = MediaKind.ANIME)))
        assertNotEquals(savedKey(card("550", kind = MediaKind.MOVIE)), savedKey(card("550", kind = MediaKind.TV)))
        // KissKH ids are unique across kinds, and Continue cards cannot tell a film from a series.
        assertEquals(savedKey(card("9", kind = MediaKind.MOVIE, source = Library.KISSKH)), savedKey(card("9", kind = MediaKind.TV, source = Library.KISSKH)))
    }

    @Test fun myListIsNewestFirstWithoutDuplicates() {
        val s = store()
        s.addToList(card("1"))
        s.addToList(card("2"))
        s.addToList(card("3"))
        s.addToList(card("1")) // already saved: stays where it was
        assertEquals(listOf("3", "2", "1"), s.myList().map { it.id })
        assertTrue(s.inList(card("2")))
        s.removeFromList(card("2"))
        assertFalse(s.inList(card("2")))
        assertEquals(listOf("3", "1"), s.myList().map { it.id })
        s.removeFromList(card("99")) // not saved: nothing happens
        assertEquals(2, s.myList().size)
    }

    @Test fun toggleListReportsTheNewState() {
        val s = store()
        assertTrue(s.toggleList(card("1")))
        assertFalse(s.toggleList(card("1")))
        assertTrue(s.myList().isEmpty())
    }

    @Test fun savedTitlesComeBackAsCardsAfterARestart() {
        store().addToList(card("7", name = "Café \"Seven\"", kind = MediaKind.ANIME, source = Library.NEXUS))
        val back = store().myList().single()
        assertEquals("Café \"Seven\"", back.name)
        assertEquals(1_000L, back.at)
        val c = back.toCard()!!
        assertEquals(Library.NEXUS, c.source)
        assertEquals(MediaKind.ANIME, c.kind)
        assertEquals("https://img/7-p.jpg", c.poster)
        assertEquals("https://img/7-b.jpg", c.backdrop)
        assertEquals("slug-7", c.slug)
        assertEquals("2020", c.year)
        assertNull(c.meta)
        assertEquals("Anime Nexus", back.toCard(sourceLabelAsMeta = true)!!.meta)
    }

    @Test fun lists_watched_and_episodes_use_the_saved_file_only() {
        val s = store()
        s.addToList(card("1"))
        s.markWatched(card("2"))
        s.setEpisodeWatched(card("3"), 1, 2, true)
        assertEquals(setOf("list", "watched", "episodes"), prefs.map.keys)
    }

    @Test fun watchedTitleIsSeparateFromMyListAndNewestFirst() {
        val s = store()
        s.markWatched(card("1"))
        s.markWatched(card("2"))
        s.markWatched(card("2"))
        assertEquals(listOf("2", "1"), s.watchedList().map { it.id })
        assertEquals(setOf("cw:tv:1", "cw:tv:2"), s.watchedKeys())
        assertTrue(s.myList().isEmpty())
        assertTrue(s.isWatched(card("1")))
        s.unmarkWatched(card("1"))
        assertFalse(s.isWatched(card("1")))
        assertTrue(s.toggleWatched(card("1")))
        assertFalse(s.toggleWatched(card("1")))
    }

    @Test fun episodeMarksSurviveARestartAndStayPerEpisode() {
        val s = store()
        val show = card("5")
        s.setEpisodeWatched(show, 1, 2, true)
        val back = store()
        assertTrue(back.episodeWatched(show, 1, 2))
        assertFalse(back.episodeWatched(show, 1, 3))
        assertFalse(back.episodeWatched(show, 2, 2))
        assertFalse(back.episodeWatched(card("6"), 1, 2))
        assertFalse(back.isWatched(show)) // one episode does not mark the title
        back.setEpisodeWatched(show, 1, 2, false)
        assertFalse(back.episodeWatched(show, 1, 2))
    }

    @Test fun aWatchedTitleShowsEveryEpisodeWatched() {
        val s = store()
        val show = card("5")
        s.markWatched(show)
        assertTrue(s.episodeWatched(show, 3, 9))
    }

    @Test fun unwatchingOneEpisodeOfAWatchedTitleKeepsTheOthersWatched() {
        val s = store()
        val show = card("5")
        s.markWatched(show)
        s.setEpisodeWatched(show, 1, 2, false, siblings = listOf(1 to 1, 1 to 2, 1 to 3))
        assertFalse(s.isWatched(show))
        assertFalse(s.episodeWatched(show, 1, 2))
        assertTrue(s.episodeWatched(show, 1, 1))
        assertTrue(s.episodeWatched(show, 1, 3))
    }

    @Test fun unmarkingATitleClearsItsEpisodeMarksToo() {
        val s = store()
        val show = card("5")
        s.markWatched(show)
        s.setEpisodeWatched(show, 1, 1, true)
        s.setEpisodeWatched(card("6"), 1, 1, true)
        s.unmarkWatched(show)
        assertFalse(s.episodeWatched(show, 1, 1))
        assertTrue(s.episodeWatched(card("6"), 1, 1))
    }

    @Test fun versionGoesUpOnlyWhenSomethingChanged() {
        val s = store()
        val start = s.version.value
        s.addToList(card("1"))
        assertEquals(start + 1, s.version.value)
        s.addToList(card("1"))
        s.removeFromList(card("2"))
        s.unmarkWatched(card("1"))
        assertEquals(start + 1, s.version.value)
        s.markWatched(card("1"))
        assertEquals(start + 2, s.version.value)
    }

    @Test fun damagedTextReadsAsEmptyAndBadEntriesAreSkipped() {
        assertTrue(SavedCodec.decodeTitles("not json").isEmpty())
        assertTrue(SavedCodec.decodeTitles(null).isEmpty())
        assertTrue(SavedCodec.decodeEpisodes("{").isEmpty())
        val mixed = """[{"s":"cw","k":"TV","i":"1","n":"Ok","t":5},{"s":"cw","k":"NOPE","i":"2","n":"Bad kind"},{"s":"cw","k":"TV","i":"3"}]"""
        assertEquals(listOf("1"), SavedCodec.decodeTitles(mixed).map { it.id })
    }

    @Test fun continueRowDropsWatchedTitles() {
        val a = card("1")
        val b = card("2", kind = MediaKind.MOVIE)
        val c = card("3", kind = MediaKind.ANIME)
        assertEquals(listOf(a, b, c), listOf(a, b, c).withoutWatched(emptySet()))
        // A watched series is dropped whether its Continue card says TV or anime; the movie stays.
        val watched = setOf("cw:tv:1", "cw:tv:3")
        assertEquals(listOf(b), listOf(a, b, c).withoutWatched(watched))
        // Same id, other source or other kind: not the same title.
        assertEquals(listOf(card("1", source = Library.CINEJOY), card("1", kind = MediaKind.MOVIE)), listOf(card("1", source = Library.CINEJOY), card("1", kind = MediaKind.MOVIE)).withoutWatched(watched))
    }
}
