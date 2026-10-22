package com.kisskh.tv.api

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbPaths
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.api.source.BrowseFilters
import com.kisskh.tv.api.source.BrowseSort
import com.kisskh.tv.api.source.SourceFilters
import com.kisskh.tv.api.source.card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFiltersTest {
    private val today = "2026-10-18"
    private val year = 2026

    // ---- TMDB discover URLs ----

    @Test fun discoverWithNothingSetIsTheOldPopularList() {
        assertEquals(
            "/discover/movie?sort_by=popularity.desc&page=2",
            TmdbPaths.discover("movie", false, 2, today),
        )
    }

    @Test fun discoverAddsGenreYearAndCountryInOrder() {
        assertEquals(
            "/discover/movie?sort_by=popularity.desc&with_origin_country=KR&with_genres=18&primary_release_year=2019&page=1",
            TmdbPaths.discover("movie", false, 1, today, "kr", "18", 2019),
        )
        // TV uses its own year field.
        assertEquals(
            "/discover/tv?sort_by=popularity.desc&with_genres=10759&first_air_date_year=2020&page=3",
            TmdbPaths.discover("tv", false, 3, today, null, "10759", 2020),
        )
    }

    @Test fun topRatedNeedsEnoughVotes() {
        assertEquals(
            "/discover/tv?sort_by=vote_average.desc&vote_count.gte=200&page=1",
            TmdbPaths.discover("tv", false, 1, today, order = TmdbPaths.Order.TOP_RATED),
        )
    }

    @Test fun newestStopsAtTodayAndMatchesTheOldLatestList() {
        val newest = TmdbPaths.discover("movie", false, 1, today, order = TmdbPaths.Order.NEWEST)
        assertEquals(TmdbPaths.discover("movie", true, 1, today), newest)
        assertTrue("primary_release_date.lte=$today" in newest)
        val tv = TmdbPaths.discover("tv", false, 1, today, genre = "35", year = 2024, order = TmdbPaths.Order.NEWEST)
        assertTrue("first_air_date.lte=$today" in tv)
        assertTrue(tv.endsWith("&with_genres=35&first_air_date_year=2024&page=1"))
    }

    @Test fun aBadGenreOrYearIsLeftOut() {
        val plain = TmdbPaths.discover("movie", false, 1, today)
        assertEquals(plain, TmdbPaths.discover("movie", false, 1, today, null, "28&with_cast=1", 99))
        assertEquals(plain, TmdbPaths.discover("movie", false, 1, today, null, "", 3000))
    }

    @Test fun animeKeepsItsSiteQueryAndTakesGenreYearAndOrder() {
        val site = "/discover/tv?with_genres=16&with_keywords=210024&sort_by=popularity.desc" +
            "&include_adult=false&without_genres=10749&certification_country=US&certification.lte=TV-MA&page=1"
        assertEquals(site, TmdbPaths.anime(1))
        // Animation (16) AND the chosen genre.
        assertTrue("with_genres=16,10765&" in TmdbPaths.anime(1, genre = "10765"))
        assertTrue("sort_by=vote_average.desc&vote_count.gte=200&" in TmdbPaths.anime(2, order = TmdbPaths.Order.TOP_RATED))
        val newest = TmdbPaths.anime(1, order = TmdbPaths.Order.NEWEST, today = today)
        assertTrue("sort_by=first_air_date.desc&first_air_date.lte=$today&vote_count.gte=5&" in newest)
        val dated = TmdbPaths.anime(4, year = 2019)
        assertTrue(dated.endsWith("&first_air_date_year=2019&page=4"))
        assertFalse("with_origin_country" in dated)
    }

    @Test fun imageSizesAreTheSmallOnesTheScreenNeeds() {
        assertEquals("w342", TmdbPaths.POSTER)
        assertEquals("w1280", TmdbPaths.BACKDROP)
        assertEquals("w300", TmdbPaths.STILL)
        assertEquals("w500", TmdbPaths.LOGO)
        assertEquals("w185", TmdbPaths.PROFILE)
    }

    // ---- what each grid offers ----

    @Test fun tmdbMoviesAndShowsOfferEveryFilterExceptStatus() {
        for (library in listOf(Library.CINEWAVE, Library.CINEJOY, Library.REELIX)) {
            for (kind in listOf(MediaKind.MOVIE, MediaKind.TV)) {
                val caps = SourceFilters.capabilities(library, kind, year)
                assertEquals("$library $kind countries", 9, caps.countries.size)
                assertTrue("$library $kind genres", caps.genres.isNotEmpty())
                assertEquals(1950..year, caps.years)
                assertEquals(listOf(BrowseSort.POPULAR, BrowseSort.TOP_RATED, BrowseSort.NEWEST), caps.sorts)
                assertTrue(caps.statuses.isEmpty())
            }
        }
    }

    @Test fun movieAndShowGenreIdsDiffer() {
        val movie = SourceFilters.capabilities(Library.CINEWAVE, MediaKind.MOVIE, year).genres.associate { it.label to it.id }
        val tv = SourceFilters.capabilities(Library.CINEWAVE, MediaKind.TV, year).genres.associate { it.label to it.id }
        assertEquals("28", movie["Action"])
        assertEquals("10759", tv["Action & Adventure"])
        assertEquals("878", movie["Science Fiction"])
        assertEquals("10765", tv["Sci-Fi & Fantasy"])
        assertNull(tv["Action"])
        assertNull(movie["Kids"])
    }

    @Test fun animeGridsHaveNoCountry() {
        for (library in listOf(Library.CINEWAVE, Library.CINEJOY, Library.REELIX)) {
            val caps = SourceFilters.capabilities(library, MediaKind.ANIME, year)
            assertTrue(caps.countries.isEmpty())
            assertTrue(caps.genres.isNotEmpty())
            assertFalse(caps.genres.any { it.id == "16" }) // Animation is already in the query
        }
    }

    @Test fun kissKhOffersCountryAndOrderAndStatusOnlyForSeries() {
        val series = SourceFilters.capabilities(Library.KISSKH, MediaKind.TV, year)
        val movies = SourceFilters.capabilities(Library.KISSKH, MediaKind.MOVIE, year)
        assertEquals(listOf("KR", "CN", "US", "TH", "PH", "JP", "HK", "TW"), series.countries.map { it.id })
        assertEquals(listOf("Ongoing", "Completed", "Upcoming"), series.statuses.map { it.label })
        assertTrue(movies.statuses.isEmpty())
        assertTrue(series.genres.isEmpty())
        assertNull(series.years)
        assertEquals(listOf(BrowseSort.POPULAR, BrowseSort.UPDATED, BrowseSort.NEWEST), movies.sorts)
    }

    @Test fun anisugeAndNexusOfferWhatTheirSitesFilter() {
        val anisuge = SourceFilters.capabilities(Library.ANISUGE, MediaKind.ANIME, year)
        assertTrue(anisuge.countries.isEmpty())
        assertTrue(anisuge.genres.isNotEmpty())
        assertEquals(1980..year, anisuge.years)
        assertEquals(listOf("currently-airing", "finished-airing"), anisuge.statuses.map { it.id })
        assertEquals(4, anisuge.sorts.size)
        assertFalse(anisuge.genres.any { it.label == "Hentai" || it.label == "Erotica" })

        val nexus = SourceFilters.capabilities(Library.NEXUS, MediaKind.ANIME, year)
        assertTrue(nexus.countries.isEmpty())
        assertNull(nexus.years) // the API has no year parameter
        assertTrue(nexus.genres.all { it.id.length == 36 }) // UUIDs
        assertEquals(listOf("Currently Airing", "Finished Airing"), nexus.statuses.map { it.id })
        assertEquals(BrowseSort.NEWEST, nexus.defaultSort)
        assertFalse(BrowseSort.POPULAR in nexus.sorts)
    }

    // ---- normalizing ----

    @Test fun normalizeDropsEverythingTheGridCannotFilter() {
        val messy = BrowseFilters(country = "kr", genre = "28", year = 2019, sort = BrowseSort.TOP_RATED, status = "Ongoing")
        // Reelix movies: no status.
        assertEquals(
            BrowseFilters(country = "KR", genre = "28", year = 2019, sort = BrowseSort.TOP_RATED),
            SourceFilters.normalize(Library.REELIX, MediaKind.MOVIE, messy, year),
        )
        // Anime Nexus: no country, no year, a movie genre id is not a Nexus genre, Top rated is not offered.
        assertEquals(
            BrowseFilters(sort = BrowseSort.NEWEST),
            SourceFilters.normalize(Library.NEXUS, MediaKind.ANIME, messy, year),
        )
        // A year outside the range is dropped.
        assertNull(SourceFilters.normalize(Library.CINEWAVE, MediaKind.MOVIE, BrowseFilters(year = 1800), year).year)
        assertNull(SourceFilters.normalize(Library.CINEWAVE, MediaKind.MOVIE, BrowseFilters(year = year + 1), year).year)
    }

    @Test fun isDefaultMeansNothingNarrowedAndTheDefaultOrder() {
        val caps = SourceFilters.capabilities(Library.REELIX, MediaKind.MOVIE, year)
        assertTrue(caps.isDefault(BrowseFilters()))
        assertFalse(caps.isDefault(BrowseFilters(sort = BrowseSort.NEWEST)))
        assertFalse(caps.isDefault(BrowseFilters(genre = "28")))
    }

    @Test fun keysAreStableAndDistinct() {
        assertEquals("all", BrowseFilters().key())
        assertEquals("c=KR", BrowseFilters(country = "kr").key())
        val a = BrowseFilters(country = "KR", genre = "18", year = 2019, sort = BrowseSort.NEWEST).key()
        assertEquals("c=KR&g=18&y=2019&s=NEWEST", a)
        assertTrue(BrowseFilters(genre = "18").key() != BrowseFilters(genre = "35").key())
    }

    // ---- KissKH ----

    @Test fun kissKhQueryMatchesTheCountriesTab() {
        val query = SourceFilters.kissQuery(
            MediaKind.TV,
            BrowseFilters(country = "KR", status = "COMPLETED", sort = BrowseSort.NEWEST),
        )
        assertEquals(CatalogQuery(ContentType.SERIES, Country.SOUTH_KOREA, Status.COMPLETED, SortOrder.RELEASE_DATE), query)
        assertEquals(
            CatalogQuery(ContentType.MOVIE, Country.JAPAN, Status.ALL, SortOrder.LAST_UPDATE),
            SourceFilters.kissQuery(MediaKind.MOVIE, BrowseFilters(country = "jp", sort = BrowseSort.UPDATED)),
        )
        // Nothing set is the plain list.
        assertEquals(CatalogQuery(ContentType.SERIES), SourceFilters.kissQuery(MediaKind.TV, BrowseFilters()))
        // Status does nothing on movies, and India is not a KissKH country: both are dropped.
        assertEquals(
            CatalogQuery(ContentType.MOVIE),
            SourceFilters.kissQuery(MediaKind.MOVIE, BrowseFilters(country = "IN", status = "ONGOING")),
        )
    }

    // ---- Reelix filtered cards ----

    @Test fun aTmdbTitleBecomesAReelixCardWithTheTmdbIdAndNoSlug() {
        val title = TmdbTitle(
            id = 438631, mediaType = "movie", name = "Dune", overview = "Paul", poster = "https://image.tmdb.org/t/p/w342/p.jpg",
            backdrop = "https://image.tmdb.org/t/p/w1280/b.jpg", vote = 7.84, year = "2021", releaseDate = "2021-10-22",
        )
        val card = title.card(Library.REELIX, MediaKind.MOVIE)
        assertEquals(Library.REELIX, card.source)
        assertEquals("438631", card.id)
        assertEquals(MediaKind.MOVIE, card.kind)
        assertEquals("", card.slug)
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", card.poster)
        assertEquals("2021-10-22", card.releaseDate)
        assertEquals("7.8", card.meta)
        // Reelix anime are TV records.
        val show = title.copy(id = 1396, mediaType = "tv").card(Library.REELIX, MediaKind.TV)
        assertEquals(MediaKind.TV, show.kind)
    }
}
