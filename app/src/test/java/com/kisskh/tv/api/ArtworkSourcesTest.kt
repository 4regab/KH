package com.kisskh.tv.api

import com.kisskh.tv.api.source.Anisuge
import com.kisskh.tv.api.source.Nexus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Posters and backdrops from Anime Nexus and Anisuge, parsed from the shapes the real sites send. */
class ArtworkSourcesTest {
    private val posterPath = "/Z0Wi-Tn1qA/rs:fill/q:70/w:640/h:960/aHR0cHM6Ly9hc3NldHM/ZW5n.avif"

    @Test
    fun nexusPosterObjectGivesThe640RenditionAndNeverTheBlur() {
        // resized_blur comes first on purpose: a reader that takes "the first path" would pick it.
        val json = """
            {"data":[{"id":"9e01bcf0-14ec-4ab0-aebb-9112064725f5","slug":"bleach","name":"Bleach","description":"Final part.",
            "poster":{"resized_blur":{"1560x2340":"/blur/w:1560/bl:50/x.avif","640x960":"/blur/w:640/bl:50/x.avif"},
            "resized":{"1560x2340":"/cQk/rs:fill/q:70/w:1560/h:2340/aHR0.avif","640x960":"$posterPath","480x720":"/z/w:480/h:720/a.avif","240x360":"/z/w:240/h:360/a.avif"}},
            "backdrop":null,"release_date":"2026-07-25"}]}
        """.trimIndent()
        val card = Nexus.parseList(json).single()
        assertEquals("https://anime.delivery$posterPath", card.poster)
        assertTrue(card.poster!!.endsWith(".avif"))
        assertNull(card.backdrop)
        assertEquals("Bleach", card.name)
        assertEquals("2026", card.year)
    }

    @Test
    fun nexusBackdropObjectAndPlainStringsStillWork() {
        val json = """
            {"data":[
             {"id":"a","slug":"a","name":"A","poster":"/plain.avif","backdrop":{"resized":{"1280x720":"/wide.avif","640x360":"/mid.avif"}}},
             {"id":"b","slug":"b","name":"B","poster":{"resized":{"480x720":"/only480.avif"}},"backdrop":"https://cdn.example/b.avif"},
             {"id":"c","slug":"c","name":"C","poster":{"resized_blur":{"640x960":"/blur.avif"}},"backdrop":null}
            ]}
        """.trimIndent()
        val (a, b, c) = Nexus.parseList(json)
        assertEquals("https://anime.delivery/plain.avif", a.poster)
        assertEquals("https://anime.delivery/mid.avif", a.backdrop)
        assertEquals("https://anime.delivery/only480.avif", b.poster)
        assertEquals("https://cdn.example/b.avif", b.backdrop)
        // A card that only has a blurred preview has no picture, never the blur.
        assertNull(c.poster)
    }

    @Test
    fun nexusEpisodeStillSkipsTheBlur() {
        val json = """
            {"data":[{"id":"e1","title":"Pilot","slug":"pilot","number":1,
            "image":{"resized_blur":{"640x360":"/blur.avif"},"resized":{"1280x720":"/big.avif","640x360":"/still.avif"}}}]}
        """.trimIndent()
        assertEquals("https://anime.delivery/still.avif", Nexus.parseEpisodes(json).single().still)
    }

    @Test
    fun anisugeHomePosterIsTakenFromDataSrc() {
        val html = """
            <a class="poster"
                                href="https://anisuge.tv/anime/beyblade-x-aj6fn/ep-136"
                                data-tip="6414">
                                <div>
                                  <img class="lazyload"
                                    data-src="https://cdn.anipixcdn.co/thumbnail/be19b5f3f1fab3af9028503f50c864db.png"
                                    alt="BEYBLADE X" />
        """.trimIndent()
        val card = Anisuge.parsePosters(html).single()
        assertEquals("beyblade-x-aj6fn", card.id)
        assertEquals("BEYBLADE X", card.name)
        assertEquals("https://cdn.anipixcdn.co/thumbnail/be19b5f3f1fab3af9028503f50c864db.png", card.poster)
        assertNull(card.backdrop)
    }

    @Test
    fun anisugeRankedListPairsEachPosterWithItsOwnLink() {
        // The "Most Viewed" layout: the link wraps the poster, and the next item follows straight after.
        val html = """
            <a class="item" href="https://anisuge.tv/anime/one-piece-odmau">
              <div class="rank">02</div>
              <div class="detail"><p class="name" data-jp="One Piece">One Piece</p></div>
              <div class="poster" data-tip="">
                <div>
                  <img src="https://cdn.anipixcdn.co/thumbnail/f899139df5e1059396431415e770c6dd.jpg"
                    alt=" One Piece" />
                </div>
              </div>
            </a>
            <a class="item" href="https://anisuge.tv/anime/black-clover-season-2-2jxhg">
              <div class="rank">03</div>
              <div class="poster" data-tip="">
                <div>
                  <img src="https://cdn.anipixcdn.co/thumbnail/e7093313bd18aab07f36fbb18a54fdf7.jpg"
                    alt=" Black Clover Season 2" />
                </div>
              </div>
            </a>
        """.trimIndent()
        val cards = Anisuge.parsePosters(html)
        assertEquals(listOf("one-piece-odmau", "black-clover-season-2-2jxhg"), cards.map { it.id })
        assertEquals(listOf("One Piece", "Black Clover Season 2"), cards.map { it.name })
        assertTrue(cards[0].poster!!.endsWith("f899139df5e1059396431415e770c6dd.jpg"))
        assertTrue(cards[1].poster!!.endsWith("e7093313bd18aab07f36fbb18a54fdf7.jpg"))
    }

    @Test
    fun anisugeSearchResultsHavePosters() {
        val html = """
            <div class="original anime search-card">
              <a class="item" href="https://anisuge.tv/anime/naruto-shippuuden-movie-5-blood-prison-5hpdu">
                <div class="poster">
                  <div>
                    <img src="https://cdn.anipixcdn.co/thumbnail/81c650caac28cdefce4de5ddc18befa0.jpg">
                  </div>
                </div>
                <div class="detail">
                  <div class="name" data-jp="Naruto: Shippuuden Movie 5 - Blood Prison">Naruto: Shippuuden Movie 5 - Blood Prison</div>
                </div>
              </a>
              <a class="item" href="https://anisuge.tv/anime/boruto-naruto-the-movie-nebuz">
                <div class="poster"><div><img src="https://cdn.anipixcdn.co/thumbnail/7f53f8c6c730af6aeb52e66eb74d8507.jpg"></div></div>
                <div class="detail"><div class="name">Boruto: Naruto the Movie</div></div>
              </a>
        """.trimIndent()
        val cards = Anisuge.parseSearchHtml(html)
        assertEquals(2, cards.size)
        assertEquals("Naruto: Shippuuden Movie 5 - Blood Prison", cards[0].name)
        assertEquals("https://cdn.anipixcdn.co/thumbnail/81c650caac28cdefce4de5ddc18befa0.jpg", cards[0].poster)
        assertEquals("https://cdn.anipixcdn.co/thumbnail/7f53f8c6c730af6aeb52e66eb74d8507.jpg", cards[1].poster)
    }

    @Test
    fun anisugeHomeKeepsFeaturedBannerAndBorrowsThePosterFromTheRowsBelow() {
        val html = """
            <a href="https://anisuge.tv/anime/one-piece-odmau" title="One Piece"
              class="swiper-slide"
              style="background-image: url('https://s4.anilist.co/banner/one-piece.jpg');"></a>
            <a href="https://anisuge.tv/anime/no-poster-xyz" title="No Poster"
              class="swiper-slide"
              style="background-image: url('https://s4.anilist.co/banner/none.jpg');"></a>
            <h2>Latest Episode</h2>
            <a class="poster" href="https://anisuge.tv/anime/one-piece-odmau/ep-1" data-tip="1">
              <div><img class="lazyload" data-src="https://cdn.anipixcdn.co/thumbnail/op.jpg" alt="One Piece" /></div>
            </a>
        """.trimIndent()
        val rows = Anisuge.parseHome(html)
        assertEquals(listOf("Featured", "Latest Episode"), rows.map { it.title })
        val featured = rows[0].items
        assertEquals("https://s4.anilist.co/banner/one-piece.jpg", featured[0].backdrop)
        assertEquals("https://cdn.anipixcdn.co/thumbnail/op.jpg", featured[0].poster)
        assertNull(featured[1].poster)
        assertNotNull(featured[1].backdrop)
        assertFalse(rows[1].items.single().poster.isNullOrBlank())
    }

    @Test
    fun anisugeShowPosterComesFromOgImage() {
        val html = """<meta property="og:image" content="https://cdn.anipixcdn.co/thumbnail/f899139df5e1059396431415e770c6dd.jpg" />"""
        assertEquals("https://cdn.anipixcdn.co/thumbnail/f899139df5e1059396431415e770c6dd.jpg", Anisuge.showPoster(html))
        assertNull(Anisuge.showPoster("<html></html>"))
    }
}
