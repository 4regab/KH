package com.kisskh.tv.api

import com.kisskh.tv.api.media.TmdbJson
import com.kisskh.tv.api.source.Anisuge
import com.kisskh.tv.api.source.BrowseSort
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.Nexus
import com.kisskh.tv.api.source.Reelix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure parsing and URL building for the filtered grids and the richer title pages. */
class SourceFilterParsingTest {
    // ---- Anisuge /filter ----

    @Test fun anisugeFilterUrlSendsOnlyWhatIsSet() {
        assertEquals("https://anisuge.tv/filter?sort=most-viewed&page=1", Anisuge.filterUrl(1, BrowseSort.POPULAR))
        assertEquals(
            "https://anisuge.tv/filter?sort=score&genre%5B%5D=1&year%5B%5D=2019&status%5B%5D=finished-airing&page=2",
            Anisuge.filterUrl(2, BrowseSort.TOP_RATED, "1", 2019, "finished-airing"),
        )
        assertTrue("sort=release-date" in Anisuge.filterUrl(1, BrowseSort.NEWEST))
        assertTrue("sort=latest-updated" in Anisuge.filterUrl(1, BrowseSort.UPDATED))
    }

    // Two cards and the pager, as /filter?sort=score&genre[]=1&year[]=2019&status[]=finished-airing printed them.
    private val filterPage = """
        <div class="item"> <div class="inner"> <div class="item-top"> <a class="poster tooltipstered" href="https://anisuge.tv/anime/mob-psycho-100-ii-qigow/ep-13" data-tip="52"> <div><img class=" lazyloaded" data-src="https://cdn.anipixcdn.co/thumbnail/1f0e3dad99908345f7439f8ffabdffc4.jpg" alt="Mob Psycho 100 II" src="https://cdn.anipixcdn.co/thumbnail/1f0e3dad99908345f7439f8ffabdffc4.jpg"> </div> </a> <div class="item-status"> <span class="type"> TV </span> </div> </div></div>
        <div class="item"> <div class="inner"> <div class="item-top"> <a class="poster tooltipstered" href="https://anisuge.tv/anime/vinland-saga-83zsx/ep-24" data-tip="1558"> <div><img class=" lazyloaded" data-src="https://cdn.anipixcdn.co/thumbnail/d645920e395fedad7bbbed0eca3fe2e0.jpg" alt="Vinland Saga" src="https://cdn.anipixcdn.co/thumbnail/d645920e395fedad7bbbed0eca3fe2e0.jpg"> </div> </a> <div class="item-status"> <span class="type"> TV </span> </div> </div></div>
        <ul class="pagination"><li class="page-item active"><a class="page-link">1</a></li><li class="page-item"><a title="Page 2" class="page-link" href="/filter?genre%5B0%5D=1&amp;year%5B0%5D=2019&amp;status%5B0%5D=finished-airing&amp;page=2&amp;sort=score&amp;direction=asc">2</a></li><li class="page-item"><a class="page-link" rel="next" href="/filter?genre%5B0%5D=1&amp;page=2&amp;sort=score">></a></li></ul>
    """.trimIndent()

    @Test fun anisugeFilterPageGivesCardsAndKnowsWhenThereIsAnotherPage() {
        val cards = Anisuge.parsePosters(filterPage)
        assertEquals(listOf("mob-psycho-100-ii-qigow", "vinland-saga-83zsx"), cards.map { it.id })
        assertEquals("Mob Psycho 100 II", cards[0].name)
        assertEquals("https://cdn.anipixcdn.co/thumbnail/1f0e3dad99908345f7439f8ffabdffc4.jpg", cards[0].poster)
        assertTrue(Anisuge.hasNextPage(filterPage, 1))
        assertFalse(Anisuge.hasNextPage(filterPage, 2))
        // The last page only links back.
        assertFalse(Anisuge.hasNextPage("""<a class="page-link" rel="prev" href="/filter?page=321&amp;sort=score"><</a>""", 322))
        // An empty result has no pager at all.
        assertFalse(Anisuge.hasNextPage("<div>No results</div>", 1))
        assertTrue(Anisuge.parsePosters("<div>No results</div>").isEmpty())
    }

    // The parts of https://anisuge.tv/anime/one-piece-odmau that the detail page reads.
    private val showPage = """
        <div class="media-bg " style="background-image: url('https://image.tmdb.org/t/p/original/a6ptrTUH1c5OdWanjyYtAkOuYD0.jpg')"> </div>
        <h1 itemprop="name" class="title" data-jp="One Piece"> One Piece </h1>
        <div class="status"> <span class="type mini-status"> TV </span> <span class="rating mini-status"> PG-13 </span> <span class="quality mini-status">HD</span> </div>
        <div class="description cts-wrapper"> <div class="short cts-block" data-name="short"> <div> Gold Roger was known as the &quot;Pirate King,&quot;... </div> <button>more+</button> </div>
        <div class="full cts-block" data-name="full" style="display: none;"> <div> Gold Roger was known as the "Pirate King," the strongest pirate. Enter Monkey Luffy. </div> </div> </div>
        <div class="meta"> <div> <div>Type:</div> <span> TV </span> </div>
        <div> <div>Premiered:</div> <span> <a href="/filter?season[]=&amp;nbsp&amp;year[]=1999"> &NBSP 1999 </a> </span> </div>
        <div> <div>Aired: </div> <span> <span itemprop="dateCreated"> Oct 20, 1999 to ? </span> </span> </div>
        <div> <div>Status:</div> <span> <a href="https://anisuge.tv/status/currently-airing"> Currently Airing </a> </span> </div>
        <div> <div>MAL:</div> <span> 8.73 </span> </div>
        <div> <div>Duration:</div> <span> 24 </span> </div>
        <div> <div>Genre:</div> <span> <a href="https://anisuge.tv/genre/action"> Action </a> , <a href="https://anisuge.tv/genre/adventure"> Adventure </a> , <a href="https://anisuge.tv/genre/fantasy"> Fantasy </a> </span> </div> </div>
    """.trimIndent()

    @Test fun anisugeShowPageGivesTheRichFields() {
        val info = Anisuge.parseShowInfo(showPage)
        assertEquals("Gold Roger was known as the \"Pirate King,\" the strongest pirate. Enter Monkey Luffy.", info.overview)
        assertEquals(listOf("Action", "Adventure", "Fantasy"), info.genres)
        assertEquals("1999", info.year)
        assertEquals(24, info.runtimeMinutes)
        assertEquals(8.73, info.score!!, 0.001)
        assertEquals("PG-13", info.certification)
        assertEquals("Currently Airing", info.status)
        // The TMDB original is swapped for the 1280 px file.
        assertEquals("https://image.tmdb.org/t/p/w1280/a6ptrTUH1c5OdWanjyYtAkOuYD0.jpg", info.backdrop)
    }

    @Test fun anisugeShowPageWithNothingKnownIsEmptyNotWrong() {
        val info = Anisuge.parseShowInfo("<h1>Nothing</h1>")
        assertNull(info.overview)
        assertTrue(info.genres.isEmpty())
        assertNull(info.year)
        assertNull(info.runtimeMinutes)
        assertNull(info.score)
        assertNull(info.status)
        assertNull(info.backdrop)
        // A missing premiere must not borrow the year of the next row.
        val noYear = Anisuge.parseShowInfo("""<div> <div>Premiered:</div> <span> ? </span> </div> <div> <div>Aired: </div> <span> Oct 20, 1999 </span> </div>""")
        assertNull(noYear.year)
    }

    // ---- Anime Nexus /shows ----

    @Test fun nexusShowsUrlCarriesPagingFiltersAndOrder() {
        assertEquals(
            "https://api.anime.nexus/api/anime/shows?page=1&perPage=24&hasVideos=1&sortBy=release_date%20desc" +
                "&includes%5B%5D=poster&includes%5B%5D=genres&includes%5B%5D=background",
            Nexus.showsUrl(1, BrowseSort.NEWEST),
        )
        val url = Nexus.showsUrl(3, BrowseSort.UPDATED, "998fb863-e6c5-4527-a87a-94c8c91d9ee1", "Currently Airing")
        assertTrue("page=3&" in url)
        assertTrue("sortBy=updated_at%20desc" in url)
        assertTrue("genres%5B%5D=998fb863-e6c5-4527-a87a-94c8c91d9ee1" in url)
        assertTrue("status%5B%5D=Currently%20Airing" in url)
        assertFalse("year" in url)
    }

    private val showsJson = """
        {"data":[
          {"id":"9e782042-138e-4fdb-8dfa-5f1b7eeb317b","name":"Oedo Fire Slayer","slug":"oedo-fire-slayer-5f46","description":"A samurai rebuilds a fire brigade.",
           "release_date":"2026-01-11","status":"Finished Airing","type":"TV","parental_rating":"PG-13 - Teens 13 or older",
           "poster":{"resized":{"640x960":"/p.avif"},"resized_blur":{"20x30":"/blur.avif"}},
           "background":{"resized":{"1280x720":"/bg.avif"}},
           "genres":[{"id":"998fb863-f39d-4d94-83cc-35e37a0494ea","name":"Adventure"},{"id":"998fb864-2391-44b7-8d22-8b0ab840dede","name":"Fantasy"}]},
          {"id":"other","name":"Other","slug":"other","release_date":"2020-02-02","poster":"/o.jpg","genres":[]}
        ],"meta":{"current_page":1,"last_page":88,"per_page":24,"total":2112}}
    """.trimIndent()

    @Test fun nexusShowsListGivesCardsAndPaging() {
        val cards = Nexus.parseList(showsJson)
        assertEquals(listOf("Oedo Fire Slayer", "Other"), cards.map { it.name })
        assertEquals("https://anime.delivery/p.avif", cards[0].poster)
        assertEquals("https://anime.delivery/bg.avif", cards[0].backdrop)
        assertEquals("2026", cards[0].year)
        assertEquals(88, Nexus.lastPage(showsJson))
    }

    @Test fun nexusShowInfoIsPickedByIdFromASearchList() {
        val info = Nexus.parseShowInfo(showsJson, "9e782042-138e-4fdb-8dfa-5f1b7eeb317b")!!
        assertEquals("A samurai rebuilds a fire brigade.", info.overview)
        assertEquals(listOf("Adventure", "Fantasy"), info.genres)
        assertEquals("2026", info.year)
        assertEquals("PG-13", info.certification)
        assertEquals("Finished Airing", info.status)
        assertEquals("https://anime.delivery/bg.avif", info.backdrop)
        assertNull(Nexus.parseShowInfo(showsJson, "not-in-the-list"))
    }

    // ---- Reelix episodes ----

    @Test fun reelixEpisodesCarryDateOverviewAndRuntime() {
        val episodes = Reelix.parseSeason(
            """{"season":{"number":1},"episodes":[
                {"number":1,"title":"Pilot","overview":"A teacher.","runtime":59,"airDate":"2008-01-20","still":"https://image.tmdb.org/t/p/w300/s.jpg"},
                {"number":2,"title":"Cat","overview":"","runtime":null,"airDate":null,"still":null}]}""",
            1,
        )
        assertEquals("2008-01-20", episodes[0].airDate)
        assertEquals("A teacher.", episodes[0].overview)
        assertEquals(59, episodes[0].runtimeMinutes)
        assertNull(episodes[1].airDate)
        assertNull(episodes[1].overview)
        assertNull(episodes[1].runtimeMinutes)
    }

    @Test fun anEpisodeThatAirsLaterIsNotAiredYet() {
        val today = "2026-10-18"
        fun ep(date: String?) = EpisodeRef("1", 1, 1, "x", null, airDate = date)
        assertTrue(ep("2026-10-18").aired(today))
        assertTrue(ep("2020-01-01").aired(today))
        assertFalse(ep("2026-10-19").aired(today))
        assertTrue(ep(null).aired(today)) // no date: never block a real episode
    }

    // ---- TMDB detail ----

    @Test fun tmdbDetailCarriesCastLogoRuntimeAndImageSizes() {
        val details = TmdbJson.details(
            """
            {"id":1396,"name":"Breaking Bad","overview":"A teacher.","poster_path":"/p.jpg","backdrop_path":"/b.jpg","vote_average":8.9,
             "first_air_date":"2008-01-20","tagline":"Remember my name","episode_run_time":[],
             "last_episode_to_air":{"runtime":56,"name":"Felina"},
             "genres":[{"id":18,"name":"Drama"},{"id":80,"name":"Crime"}],
             "credits":{"cast":[
               {"name":"Bryan Cranston","character":"Walter White","profile_path":"/bc.jpg"},
               {"name":"Aaron Paul","character":"","profile_path":null}]},
             "images":{"logos":[{"iso_639_1":"fr","file_path":"/fr.png"},{"iso_639_1":"en","file_path":"/en.png"},{"iso_639_1":null,"file_path":"/none.png"}]},
             "seasons":[{"season_number":1,"name":"Season 1","episode_count":7}]}
            """.trimIndent(),
            "tv",
        )
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", details.title.poster)
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", details.title.backdrop)
        assertEquals("https://image.tmdb.org/t/p/w500/en.png", details.logo)
        assertEquals("Remember my name", details.tagline)
        assertEquals(listOf("Drama", "Crime"), details.genres)
        assertEquals(56, details.episodeRuntime) // episode_run_time is empty, so the last aired episode is used
        assertEquals(listOf("Bryan Cranston", "Aaron Paul"), details.castMembers.map { it.name })
        assertEquals("Walter White", details.castMembers[0].character)
        assertEquals("https://image.tmdb.org/t/p/w185/bc.jpg", details.castMembers[0].photo)
        assertNull(details.castMembers[1].character)
        assertNull(details.castMembers[1].photo)
        assertEquals(listOf("Bryan Cranston", "Aaron Paul"), details.cast) // the old four-name list still works
    }

    @Test fun tmdbCastIsCappedAtTwelve() {
        val people = (1..20).joinToString(",") { """{"name":"Actor $it","character":"Role $it","profile_path":null}""" }
        val details = TmdbJson.details("""{"id":1,"title":"X","credits":{"cast":[$people]}}""", "movie")
        assertEquals(12, details.castMembers.size)
        assertEquals("Actor 12", details.castMembers.last().name)
        assertEquals(4, details.cast.size)
    }

    @Test fun tmdbEpisodeRunTimeWinsWhenPresent() {
        val details = TmdbJson.details("""{"id":1,"name":"X","episode_run_time":[24,30],"last_episode_to_air":{"runtime":29}}""", "tv")
        assertEquals(24, details.episodeRuntime)
    }
}
