package com.kisskh.tv.api

import com.kisskh.tv.api.media.CineJoyToken
import com.kisskh.tv.api.media.EmbedFailover
import com.kisskh.tv.api.media.EmbedNav
import com.kisskh.tv.api.media.EmbedPage
import com.kisskh.tv.api.media.EmbedPolicy
import com.kisskh.tv.api.media.EmbedServers
import com.kisskh.tv.api.media.FebParse
import com.kisskh.tv.api.media.FebSource
import com.kisskh.tv.api.media.Febbox
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKeys
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.MediaProgress
import com.kisskh.tv.api.media.PlayRef
import com.kisskh.tv.api.media.PlayToken
import com.kisskh.tv.api.media.TmdbEpisode
import com.kisskh.tv.api.media.TmdbJson
import com.kisskh.tv.api.media.TmdbPaths
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.NavState
import com.kisskh.tv.ui.RootTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourcesTest {
    @Test
    fun moviePlayTokenMatchesCineWave() {
        assertEquals("DgYYDBJMQ1BT", PlayToken.movie(550))
        assertEquals(PlayRef.Movie(550), PlayToken.decode("DgYYDBJMQ1BT"))
    }

    @Test
    fun tvPlayTokenRoundTrips() {
        val token = PlayToken.tv(1396, 1, 1)
        assertFalse(token.contains('+') || token.contains('/') || token.contains('='))
        assertEquals(PlayRef.Tv(1396, 1, 1), PlayToken.decode(token))
        assertEquals("Fx9DVERYQEhSRF8", token)
    }

    @Test
    fun embedUrlsFollowTheSiteTemplates() {
        val servers = EmbedServers.playable
        assertEquals("VCC", servers.first().code)
        assertEquals("https://vsembed.ru/embed/movie/550", servers.first().url(MediaKind.MOVIE, 550))
        assertEquals("https://vsembed.ru/embed/tv/1396/2/3", EmbedServers.all.first().url(MediaKind.TV, 1396, 2, 3))
        val two = EmbedServers.all.first { it.code == "2EM" }
        assertEquals("https://2embed.cc/embed/movie/550", two.url(MediaKind.MOVIE, 550))
        assertEquals("https://2embed.cc/embed/tv/1396&s=2&e=3", two.url(MediaKind.TV, 1396, 2, 3))
        val rive = EmbedServers.all.first { it.code == "RIVE" }
        assertEquals("https://rivestream.org/embed?type=movie&id=550", rive.url(MediaKind.MOVIE, 550))
        assertTrue(EmbedServers.all.any { it.code == "HDH" && !it.web })
        assertFalse(servers.any { it.code == "HDH" || it.movie.startsWith("hdhub") })
        assertEquals(EmbedServers.all.size - 1, servers.size)
    }

    @Test
    fun failoverTriesEachServerOnce() {
        assertEquals(listOf(0, 1, 2), EmbedFailover.order(0, 3))
        assertEquals(listOf(2, 3, 0, 1), EmbedFailover.order(2, 4))
        assertEquals(1, EmbedFailover.next(0, 3, setOf(0)))
        assertNull(EmbedFailover.next(0, 3, setOf(0, 1, 2)))
        assertEquals(0, EmbedFailover.next(2, 3, setOf(2)))
    }

    @Test
    fun deadPagesFailOverButPlayersAndRealSitesDoNot() {
        // Seen on a Chromecast with Google TV for movie 550.
        assertTrue(EmbedPage.unavailable("404 Oops! Page not found Return to Home", players = 0))
        assertTrue(EmbedPage.unavailable("Origin is unreachable Error code 523", players = 0))
        assertFalse(EmbedPage.unavailable("FIGHT CLUB 1999", players = 1))
        assertFalse(EmbedPage.unavailable("404 Not found", players = 1))
        assertFalse(EmbedPage.unavailable("Fight Club " + "x".repeat(700) + " not found", players = 0))
        assertTrue(EmbedPage.sentHome("https://airflix1.com/movie/550", "https://airflix1.com/"))
        assertFalse(EmbedPage.sentHome("https://player.videasy.net/movie/550", "https://player.videasy.to/movie/550"))
        assertFalse(EmbedPage.sentHome("https://vsembed.ru/embed/movie/550", "about:blank"))
        assertTrue(EmbedPage.blanked("about:blank"))
        assertEquals("server not found", EmbedPage.reason("net::ERR_NAME_NOT_RESOLVED"))
    }

    @Test
    fun adHostsAreBlockedAndOrdinaryPagesAreAllowed() {
        assertEquals(EmbedNav.FAIL, EmbedPolicy.decide("https://dz.detergesateens.com/r8", true))
        assertEquals(EmbedNav.BLOCK, EmbedPolicy.decide("https://dz.detergesateens.com/r8", false))
        assertEquals(EmbedNav.BLOCK, EmbedPolicy.decide("javascript:alert(1)", true))
        assertEquals(EmbedNav.ALLOW, EmbedPolicy.decide("about:blank", true))
        assertEquals(EmbedNav.ALLOW, EmbedPolicy.decide("https://player.videasy.net/movie/550", true))
        assertTrue(EmbedPolicy.blocked("ads.doubleclick.net"))
    }

    @Test
    fun episodesAfterTodayAreNotAiredYet() {
        val today = "2026-10-21"
        assertTrue(TmdbEpisode(6, "a", "", null, 22, "2026-10-16").aired(today))
        assertTrue(TmdbEpisode(6, "a", "", null, 22, "2026-10-21").aired(today))
        assertFalse(TmdbEpisode(7, "b", "", null, null, "2026-10-23").aired(today))
        assertTrue(TmdbEpisode(8, "c", "", null, null, null).aired(today))
    }

    @Test
    fun aTapThatLeavesForAnotherSiteIsAHijack() {
        val page = "https://vidsrc.cc/v2/embed/movie/550"
        assertTrue(EmbedPolicy.hijack(page, "https://ad.example.com/landing", afterTap = true, redirect = false))
        assertFalse(EmbedPolicy.hijack(page, "https://player.vidsrc.cc/next", afterTap = true, redirect = false))
        assertFalse(EmbedPolicy.hijack(page, "https://ad.example.com/landing", afterTap = false, redirect = false))
        assertFalse(EmbedPolicy.hijack(page, "https://mirror.example.net/movie/550", afterTap = true, redirect = true))
    }

    @Test
    fun tmdbPathsMatchTheSites() {
        assertEquals(
            "https://api.themoviedb.org/3/trending/movie/day?api_key=8476a7ab80ad76f0936744df0430e67c",
            TmdbPaths.withKey(TmdbPaths.trendingMovies()),
        )
        assertEquals(
            "/discover/tv?with_genres=16&with_keywords=210024&sort_by=popularity.desc" +
                "&include_adult=false&without_genres=10749&certification_country=US" +
                "&certification.lte=TV-MA&page=1",
            TmdbPaths.anime(1),
        )
        assertEquals(
            "/discover/movie?sort_by=primary_release_date.desc&primary_release_date.lte=2026-10-18" +
                "&vote_count.gte=5&include_null_first_air_dates=false&page=1",
            TmdbPaths.discover("movie", true, 1, "2026-10-18"),
        )
        assertEquals(
            "/discover/tv?sort_by=first_air_date.desc&first_air_date.lte=2026-10-18" +
                "&vote_count.gte=5&include_null_first_air_dates=false&page=2",
            TmdbPaths.discover("tv", true, 2, "2026-10-18"),
        )
        assertEquals("/tv/on_the_air?page=1", TmdbPaths.onTheAir())
        assertEquals("/search/multi?query=fight%20club", TmdbPaths.search("fight club"))
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", TmdbPaths.image("/p.jpg", "w500"))
        assertNull(TmdbPaths.image(null, "w500"))
        assertNull(TmdbPaths.image("", "w500"))
    }

    @Test
    fun tmdbPageDropsPeopleAndReadsTitles() {
        val page = TmdbJson.page(
            """
            {"page":1,"total_pages":3,"results":[
              {"id":550,"media_type":"movie","title":"Fight Club","overview":"A man","poster_path":"/p.jpg","backdrop_path":"/b.jpg","vote_average":8.4,"release_date":"1999-10-15"},
              {"id":9,"media_type":"person","name":"Someone"},
              {"id":1396,"media_type":"tv","name":"Breaking Bad","first_air_date":"2008-01-20"}
            ]}
            """.trimIndent(),
            null,
        )
        assertEquals(2, page.items.size)
        assertEquals("Fight Club", page.items[0].name)
        assertEquals("1999", page.items[0].year)
        assertEquals("https://image.tmdb.org/t/p/w780/b.jpg", page.items[0].backdrop)
        assertEquals("tv", page.items[1].mediaType)
        assertEquals(3, page.totalPages)
    }

    @Test
    fun movieDetailReadsUsCertificationAndSkipsEmptySeasons() {
        val details = TmdbJson.details(
            """
            {"id":550,"title":"Fight Club","overview":"A man","poster_path":"/p.jpg","vote_average":8.433,"release_date":"1999-10-15",
             "release_dates":{"results":[
               {"iso_3166_1":"FR","release_dates":[{"certification":"16"}]},
               {"iso_3166_1":"US","release_dates":[{"certification":"R"}]}
             ]},
             "recommendations":{"results":[{"id":1,"title":"Other","media_type":"movie","poster_path":null}]}}
            """.trimIndent(),
            "movie",
        )
        assertEquals("R", details.certification)
        assertEquals("8.4", details.title.voteText())
        assertEquals(1, details.recommendations.size)
        assertNull(details.recommendations[0].poster)
        val show = TmdbJson.details(
            """
            {"id":1396,"name":"Breaking Bad","overview":"","first_air_date":"2008-01-20",
             "content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]},
             "seasons":[
               {"season_number":0,"name":"Specials","episode_count":0},
               {"season_number":1,"name":"Season 1","episode_count":7}
             ]}
            """.trimIndent(),
            "tv",
        )
        assertEquals("TV-MA", show.certification)
        assertEquals(listOf(1), show.seasons.map { it.number })
        val episodes = TmdbJson.episodes(
            """
            {"episodes":[
              {"episode_number":2,"name":"Two","overview":"","still_path":"/s.jpg","runtime":47},
              {"episode_number":1,"name":"","overview":"Hi","still_path":null}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf(1, 2), episodes.map { it.number })
        assertEquals("Episode 1", episodes[0].name)
        assertEquals("https://image.tmdb.org/t/p/w300/s.jpg", episodes[1].still)
    }

    @Test
    fun febboxUrlRequiresATokenAndPrefersTheBetterM3u8() {
        assertNull(Febbox.requestUrl(true, 550, 1, 1, "  "))
        assertEquals("https://feb.wing.st/movie/550?ui=abc%20def", Febbox.requestUrl(true, 550, 1, 1, "abc def"))
        assertEquals(
            "https://feb.wing.st/tv/1396/1/2?ui=tok%2Fen",
            Febbox.requestUrl(false, 1396, 1, 2, "tok/en"),
        )
        val picked = Febbox.select(
            listOf(
                FebSource("https://cdn/low.m3u8", "360P"),
                FebSource("https://cdn/file.mp4", "4K"),
                FebSource("https://cdn/best.m3u8", "1080p"),
                FebSource("https://cdn/also.m3u8", "1080P"),
            ),
        )
        assertEquals("https://cdn/best.m3u8", picked?.url)
        val parsed = Febbox.parse(
            """
            {"sources":[
              {"url":"https://x/a.m3u8","quality":"360P"},
              {"url":"https://x/b.mp4","quality":"1080P"},
              {"url":"https://x/c.m3u8","quality":"1080P"}
            ],"subtitles":[
              {"url":"https://s/a.en.vtt","label":"English"},
              {"url":"https://s/a.en.vtt","label":"English"}
            ]}
            """.trimIndent(),
        ) as FebParse.Ready
        assertEquals("https://x/c.m3u8", parsed.url)
        assertEquals(1, parsed.subtitles.size)
        assertEquals("en", parsed.subtitles[0].lang)
        val rejected = Febbox.parse("""{"error":"?ui=token is required"}""") as FebParse.Rejected
        assertTrue(rejected.message.contains("token"))
        assertEquals(FebParse.Empty, Febbox.parse("""{"sources":[]}"""))
    }

    @Test
    fun progressKeysStayOutOfKissKhPrefs() {
        val movie = MediaKeys.movie("cw", 550)
        val episode = MediaKeys.episode("cj", 1396, 1, 2)
        assertEquals("cw:movie:550", movie)
        assertEquals("cj:tv:1396:1:2", episode)
        assertTrue(MediaKeys.valid(movie) && MediaKeys.valid(episode))
        assertFalse(movie.startsWith("ep_") || episode.startsWith("ep_"))
        assertFalse(CineJoyToken.KEY.startsWith("ep_"))
        assertEquals("fem_token", CineJoyToken.KEY)
        assertEquals("media_progress", MediaProgress.PREFS)
        assertEquals("cinejoy", CineJoyToken.PREFS)

        val prefs = FakePrefs()
        val progress = MediaProgress(prefs) { 1_000L }
        progress.remember(movie, "Fight Club", "https://image/p.jpg")
        assertEquals(0L, progress.position(movie))
        assertFalse(progress.save(movie, "Fight Club", null, 1_000L, 100_000L))
        assertEquals(0L, progress.position(movie))
        assertTrue(progress.save(movie, "Fight Club", null, 50_000L, 100_000L))
        assertEquals(50_000L, progress.position(movie))
        assertTrue(progress.save(movie, "Fight Club", null, 96_000L, 100_000L))
        assertEquals(0L, progress.position(movie))
        assertTrue(progress.entries().single().finished)
        assertTrue(prefs.all.keys.none { it.startsWith("ep_") || it.matches(Regex("""\d+\..+""")) })

        val tokenPrefs = FakePrefs()
        val token = CineJoyToken(tokenPrefs)
        token.set("  secret  ")
        assertEquals("secret", token.get())
        token.set("  ")
        assertEquals("", token.get())
        assertFalse(tokenPrefs.all.keys.any { it.startsWith("ep_") })
    }

    @Test
    fun cineJoyWatchPageUrls() {
        assertEquals("https://cinejoy.pk/watch/movie/550", com.kisskh.tv.api.media.CineJoyWeb.watchUrl(true, 550, 1, 1))
        assertEquals("https://cinejoy.pk/watch/tv/1396/1/2", com.kisskh.tv.api.media.CineJoyWeb.watchUrl(false, 1396, 1, 2))
        assertEquals("https://cinejoy.pk/watch/tv/1396/1/1", com.kisskh.tv.api.media.CineJoyWeb.watchUrl(false, 1396, 0, 0))
    }

    @Test
    fun appOpensOnCineWaveAndListsItFirst() {
        assertEquals(Library.CINEWAVE, com.kisskh.tv.api.media.DEFAULT_LIBRARY)
        assertEquals(Library.CINEWAVE, com.kisskh.tv.api.media.LIBRARY_ORDER.first())
        assertEquals(Library.entries.toSet(), com.kisskh.tv.api.media.LIBRARY_ORDER.toSet())
    }

    @Test
    fun switchingLibraryDoesNotDisturbKissKhTabs() {
        val kiss = NavState().selectTab(RootTab.MOVIES)
        assertTrue(kiss.current is Dest.Movies)
        assertEquals(Library.KISSKH, kiss.library)

        val wave = kiss.selectLibrary(Library.CINEWAVE)
        assertEquals(Library.CINEWAVE, wave.library)
        assertTrue(wave.current is Dest.Home)
        assertEquals(Dest.MediaBrowse(com.kisskh.tv.api.media.MediaKind.MOVIE), wave.selectTab(RootTab.MOVIES).current)
        assertEquals(Dest.MediaBrowse(com.kisskh.tv.api.media.MediaKind.TV), wave.selectTab(RootTab.SERIES).current)
        assertEquals(Dest.MediaBrowse(com.kisskh.tv.api.media.MediaKind.ANIME), wave.selectTab(RootTab.ANIME).current)
        assertEquals(wave, wave.selectTab(RootTab.COUNTRIES))

        val joy = NavState(library = Library.CINEJOY)
        assertEquals(joy, joy.selectTab(RootTab.ANIME))
        assertTrue(joy.selectTab(RootTab.SERIES).current is Dest.MediaBrowse)
    }
}
