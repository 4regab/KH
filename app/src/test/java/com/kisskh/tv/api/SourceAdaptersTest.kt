package com.kisskh.tv.api

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKeys
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.MediaProgress
import com.kisskh.tv.api.source.Anisuge
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.MegaPlay
import com.kisskh.tv.api.source.HtmlText
import com.kisskh.tv.api.source.Nexus
import com.kisskh.tv.api.source.Reelix
import com.kisskh.tv.api.source.SeasonBlock
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.api.source.TitleDetail
import com.kisskh.tv.api.source.progressKey
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.NavState
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.tabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceAdaptersTest {
    @Test
    fun anisugeVrfMatchesTheLiveEpisodeList() {
        assertEquals("F01BB19fDHV=", Anisuge.vrf(1642))
        assertEquals("F01BB19fDHV=", Anisuge.vrf("1642"))
    }

    @Test
    fun anisugeParsesEpisodesServersAndSearch() {
        val html = """
            <a href="#" data-id="30298" title="I&#039;m Luffy!" data-slug="1" data-sub="1" data-dub="1" data-ids="TOK">
            <a href="#" data-id="30299" title="Episode 2" data-slug="2" data-sub="1" data-dub="0" data-ids="TOK2">
        """.trimIndent()
        val episodes = Anisuge.parseEpisodes(html)
        assertEquals(listOf(1, 2), episodes.map { it.number })
        assertEquals("I'm Luffy!", episodes[0].name)
        assertEquals("TOK", episodes[0].token)
        assertTrue(episodes[0].dub)
        assertFalse(episodes[1].dub)

        val servers = Anisuge.parseServers(
            """
            <div class="server-type type mb-2" data-type="sub">
              <div class="notice" data-link-id="NOPE"><span>If the current server doesn't work</span></div>
              <div class="server" data-link-id="SUB1"><span>HD-1</span></div>
              <div class="server" data-link-id="SUB2"><span>Vidstream-2</span></div>
            </div>
            <div class="server-type type mb-2" data-type="dub">
              <div class="server" data-link-id="DUB1"><span>HD-1</span></div>
            </div>
            """.trimIndent(),
        )
        assertEquals(listOf("sub", "sub", "dub"), servers.map { it.audio })
        assertEquals(listOf("HD-1", "Vidstream-2", "HD-1"), servers.map { it.name })
        assertEquals("SUB1", servers[0].linkId)
        assertEquals(
            "https://megaplay.buzz/stream/s-2/2142/sub",
            Anisuge.parseServerUrl("""{"status":200,"result":{"url":"https://megaplay.buzz/stream/s-2/2142/sub","skip_data":{}}}"""),
        )
        val found = Anisuge.parseSearch(
            """{"status":200,"result":{"html":"<a class=\"item\" href=\"https://anisuge.tv/anime/one-piece-odmau\"><img src=\"https://cdn/p.jpg\"><div class=\"name\" data-jp=\"One Piece\">One Piece</div></a>"}}""",
        )
        assertEquals("one-piece-odmau", found.single().id)
        assertEquals("One Piece", found.single().name)
        assertEquals(MediaKind.ANIME, found.single().kind)
        assertEquals("one-piece-odmau", Anisuge.slugOf("https://anisuge.tv/anime/one-piece-odmau/ep-19"))
        assertEquals("1642", Anisuge.mangaId("""<div data-manga-id="1642"></div>"""))
    }

    @Test
    fun megaplayDecryptsTheSourceBlobAndSignsThePlaylist() {
        val enc = "wdeBruh3qqn_i5wUNnyaPcXqidp1UWP84FfPHzGyKXA2hDZBfMCmZ4FLvs7_pQuH549Eptax8UOjAJyRIZfrRhUGUKy9OBeGh2yB_-m_JLAlLnWTLzYZC3_C5I4ltveYoiaU66Do9RgI9bCetmk_o87-sd66brXnWV1MbjhLnjw"
        val file = "https://fetch.nexabloom.top/anime/f899139df5e1059396431415e770c6dd/61b87186ab260d05003427e16ccf5657/master.m3u8"
        assertEquals("""{"file":"$file"}""", MegaPlay.decrypt(enc))
        val signed = MegaPlay.sign(
            "https://cdn.example/anime/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/master.m3u8",
            1_700_000_000L,
        )
        assertEquals(
            "https://cdn.example/anime/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/master.m3u8?token=MTcwMDAwMDA5MHxhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYS9iYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYg.MBX02sGHErfEnDmWTzp3xvQTGRDypONCAlqPpMiFvyc",
            signed,
        )
        assertEquals(file, MegaPlay.sign("$file?token=already", 1_700_000_000L).substringBefore("?"))
        assertEquals("https://cdn.example/plain.mp4", MegaPlay.sign("https://cdn.example/plain.mp4", 1_700_000_000L))
        assertEquals("2142", MegaPlay.streamId("https://megaplay.buzz/stream/s-2/2142/sub?s=tcdn"))
        assertEquals(
            "36396",
            MegaPlay.dataId("""<div class="fix-area" id="megaplay-player"
                data-id="36396"
                data-realid="2142">"""),
        )
        assertTrue(MegaPlay.handles("https://megaplay.buzz/stream/s-2/2142/sub"))
        val resolved = MegaPlay.resolve(
            """{"enc":"$enc","tracks":[{"file":"https://cdn.example/eng.vtt","label":"English","kind":"captions"},{"file":"https://cdn.example/thumbs.vtt","kind":"thumbnails"}]}""",
            1_700_000_000L,
        )
        assertEquals(MegaPlay.sign(file, 1_700_000_000L), resolved?.url)
        assertEquals("English", resolved?.subs?.single()?.label)
        assertEquals("https://cdn.example/eng.vtt", resolved?.subs?.single()?.uri)
    }

    @Test
    fun reelixCardsSearchSeasonAndPlayers() {
        val cards = Reelix.parseCards(
            """
            <article class="media-card" data-title-card data-type="tv" data-id="1396" data-title="Breaking Bad" data-poster="https://image.tmdb.org/t/p/w342/p.jpg" data-year="2008" data-rating="8.9" data-href="/tv/breaking-bad-o15I4mNVwy">
            """.trimIndent(),
        )
        assertEquals("1396", cards.single().id)
        assertEquals(MediaKind.TV, cards.single().kind)
        assertEquals("breaking-bad-o15I4mNVwy", cards.single().slug)
        assertEquals("Breaking Bad", cards.single().name)
        val found = Reelix.parseSearch(
            """
            {"titles":[{"id":438631,"slug":"dune-79pBohVyYkG","title":"Dune","overview":"Paul","poster":"https://image/p.jpg","backdrop":"https://image/b.jpg","type":"movie","year":2021,"rating":7.8}]}
            """.trimIndent(),
        )
        assertEquals("438631", found.single().id)
        assertEquals(MediaKind.MOVIE, found.single().kind)
        assertEquals("2021", found.single().year)
        assertEquals("Paul", found.single().overview)
        val episodes = Reelix.parseSeason(
            """{"season":{"number":1,"name":"Season 1"},"episodes":[{"number":2,"title":"Cat","still":"https://image/s.jpg"},{"number":1,"title":"Pilot","still":null}]}""",
            1,
        )
        assertEquals(listOf(1, 2), episodes.map { it.number })
        assertEquals("Pilot", episodes[0].name)
        assertEquals(listOf(1, 2, 5), Reelix.seasons("""<button data-season="2"></button><button data-season="1"></button><button data-season="5"></button>"""))
        assertTrue(Reelix.hasNextPage("""<a href="/movies?page=2">""", 1))
        assertEquals(
            "https://vidcore.io/movie/438631?theme=d7dee2&autoPlay=true&autoNext=true&poster=true&title=true&chromecast=true&sub=en",
            Reelix.vidcore(true, "438631", 1, 1),
        )
        assertEquals(
            "https://vidcore.io/tv/1396/1/1?theme=d7dee2&autoPlay=true&autoNext=true&poster=true&title=true&chromecast=true&sub=en&nextButton=true",
            Reelix.vidcore(false, "1396", 1, 1),
        )
        val movie = Reelix.targets(true, "438631", 1, 1)
        assertEquals("VidCore", movie.first().name)
        assertEquals("https://vsembed.su/embed/movie/438631", movie.first { it.name == "vsembed" }.page)
        assertEquals("https://multiembed.mov/?video_id=438631&tmdb=1", movie.first { it.name == "superembed" }.page)
        assertEquals("https://vsembed.su/embed/tv/1396/1/1", Reelix.embeds(false, "1396", 1, 1).first().page)
        assertTrue(movie.first().sniff)
        assertFalse(movie.first { it.name == "vsembed" }.sniff)
        assertEquals("Paul Atreides", HtmlText.paragraph("""<p class="reel-title-overview">Paul Atreides</p>""", "reel-title-overview"))
    }

    @Test
    fun nexusImagesSearchAndEpisodes() {
        assertEquals(
            "https://anime.delivery/insecure/rs:fill:640:960/poster.jpg",
            Nexus.image("/insecure/rs:fill:640:960/poster.jpg"),
        )
        assertTrue(Nexus.searchUrl("naruto").endsWith("search=naruto"))
        assertFalse(Nexus.searchUrl("naruto").contains("keyword="))
        assertTrue(Nexus.headers().containsKey("X-Client-Fingerprint"))
        val cards = Nexus.parseList(
            """
            {"data":[{"id":"9e01bcf0-14ec-4ab0-aebb-9112064725f5","slug":"bleach","name":"Bleach","poster":"/p.jpg","backdrop":"/b.jpg","description":"Final part.","release_date":"2026-07-25"}]}
            """.trimIndent(),
        )
        assertEquals("Bleach", cards.single().name)
        assertEquals("https://anime.delivery/p.jpg", cards.single().poster)
        assertEquals("2026", cards.single().year)
        assertEquals(MediaKind.ANIME, cards.single().kind)
        val episodes = Nexus.parseEpisodes(
            """
            {"data":[{"id":"019f99d1-0bf5-71f0-b763-c290273cc0e0","title":"God of Thunder","slug":"god-of-thunder","number":1,"duration":1475,"image":{"resized":{"1560x2340":"/big.jpg","640x960":"/small.jpg"}}}],"meta":{"last_page":3}}
            """.trimIndent(),
        )
        assertEquals("019f99d1-0bf5-71f0-b763-c290273cc0e0", episodes.single().id)
        assertEquals(1, episodes.single().number)
        assertEquals("https://anime.delivery/small.jpg", episodes.single().still)
        assertEquals("https://anime.nexus/watch/019f99d1-0bf5-71f0-b763-c290273cc0e0/god-of-thunder", Nexus.watchUrl(episodes.single().id, episodes.single().slug))
        assertFalse(Nexus.player(episodes.single().id, episodes.single().slug).sniff)
        assertEquals(3, Nexus.lastPage("""{"meta":{"last_page":3}}"""))
    }

    @Test
    fun progressKeysAcceptTheNewSourcesAndKeepKissKhSeparate() {
        val movie = MediaKeys.movie("rx", "438631")
        val show = MediaKeys.episode("nx", "9e01bcf0-14ec-4ab0-aebb-9112064725f5", 1, 3)
        val anisuge = MediaKeys.episode("as", "one-piece-odmau", 1, 12)
        assertEquals("rx:movie:438631", movie)
        assertTrue(MediaKeys.valid(movie))
        assertTrue(MediaKeys.valid(show))
        assertTrue(MediaKeys.valid(anisuge))
        assertFalse(MediaKeys.valid("ep_12"))
        assertFalse(MediaKeys.valid("kh:movie:1"))
        assertEquals(show, progressKey("nx", MediaKind.ANIME, "9e01bcf0-14ec-4ab0-aebb-9112064725f5", 1, 3))

        val prefs = FakePrefs()
        val progress = MediaProgress(prefs) { 5_000L }
        progress.remember(show, "Bleach", "https://anime.delivery/p.jpg")
        assertTrue(progress.save(show, "Bleach", null, 20_000L, 100_000L))
        val entry = progress.entries().single()
        assertEquals("9e01bcf0-14ec-4ab0-aebb-9112064725f5", entry.token)
        assertEquals(0, entry.id)
        assertEquals(3, entry.episode)
        assertTrue(prefs.all.keys.none { it.startsWith("ep_") || it.matches(Regex("""\d+\..+""")) })
    }

    @Test
    fun shelvesAreSharedAndANewSourceDoesNotLeakTabs() {
        assertEquals(
            listOf(RootTab.HOME, RootTab.SEARCH, RootTab.MOVIES, RootTab.SERIES, RootTab.ANIME, RootTab.MINE),
            Library.REELIX.tabs(),
        )
        assertEquals(Library.REELIX.tabs(), Library.CINEJOY.tabs())
        assertEquals(listOf(RootTab.HOME, RootTab.SEARCH, RootTab.ANIME, RootTab.MINE), Library.ANISUGE.tabs())
        assertEquals(Library.NEXUS.tabs(), Library.ANISUGE.tabs())
        val home = NavState(library = Library.ANISUGE)
        assertEquals(home, home.selectTab(RootTab.MOVIES))
        assertEquals(home, home.selectTab(RootTab.COUNTRIES))
        val anime = home.selectTab(RootTab.ANIME)
        assertEquals(Dest.Shelf(MediaKind.ANIME), anime.current)
        val back = anime.selectLibrary(Library.KISSKH)
        assertEquals(Library.KISSKH, back.library)
        assertTrue(back.current is Dest.Home)
        assertTrue(back.selectTab(RootTab.MOVIES).current is Dest.Movies)

        val detail = TitleDetail(
            card = TitleCard(Library.REELIX, "1396", MediaKind.TV, "Breaking Bad", null, null, "2008", null),
            description = "",
            seasons = listOf(
                SeasonBlock(
                    1,
                    "Season 1",
                    listOf(
                        EpisodeRef("1", 1, 1, "Pilot", null),
                        EpisodeRef("2", 1, 2, "Cat", null),
                    ),
                ),
            ),
        )
        assertEquals("Pilot", detail.neighbors(detail.seasons[0].episodes[1]).first?.name)
        assertEquals(null, detail.neighbors(detail.seasons[0].episodes[1]).second)
    }
}
