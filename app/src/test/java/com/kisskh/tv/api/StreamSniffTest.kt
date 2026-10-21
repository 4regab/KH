package com.kisskh.tv.api

import com.kisskh.tv.api.media.CineJoyWeb
import com.kisskh.tv.api.media.EmbedServers
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.StreamSniff
import com.kisskh.tv.api.media.SubLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSniffTest {
    @Test
    fun picksPlaylistsAndSkipsDecoysAndAds() {
        assertTrue(StreamSniff.isStream("https://cdn2.fastcloud.live/movie/abc.ZRhi/bigtits.m3u8"))
        assertTrue(StreamSniff.isStream("https://nebula.bright67.online/hls/195a/master.m3u8?x=1"))
        assertFalse(StreamSniff.isStream("https://vidrock.net/demo-video.mp4"))
        assertFalse(StreamSniff.isStream("https://titsdb.fastcloud.live/thumbnail/movie/550/thumbnail.vtt"))
        assertFalse(StreamSniff.isStream("https://ads.doubleclick.net/x/master.m3u8"))
        assertFalse(StreamSniff.isStream("blob:https://player.vidzee.wtf/f745"))
        assertFalse(StreamSniff.isStream("https://nebula.bright67.online/hls/195a/1080p/playlist.jpg"))
    }

    @Test
    fun keepsRefererOrFallsBackToThePage() {
        assertEquals(
            mapOf("Referer" to "https://player.vidzee.wtf/"),
            StreamSniff.headers(mapOf("referer" to "https://player.vidzee.wtf/", "Accept" to "*/*"), "https://player.vidzee.wtf/embed/movie/550"),
        )
        assertEquals(
            mapOf("Referer" to "https://vidrock.net/"),
            StreamSniff.headers(emptyMap(), "https://vidrock.net/embed/movie/550"),
        )
        assertNull(StreamSniff.origin("not a url"))
    }

    @Test
    fun cineJoyWatchAndSubtitleLinks() {
        assertEquals("https://cinejoy.pk/watch/movie/550", CineJoyWeb.watchUrl(true, 550, 1, 1))
        assertEquals("https://cinejoy.pk/watch/tv/1399/1/1", CineJoyWeb.watchUrl(false, 1399, 0, 0))
        assertEquals(
            "https://subs.wing.st/subtitles?type=tv&tmdb=1399&season=2&episode=3",
            CineJoyWeb.subtitlesUrl(false, 1399, 2, 3),
        )
    }

    @Test
    fun parsesWingSubtitlesEnglishFirstAndCapped() {
        val json = """{"total":4,"subtitles":[
            {"id":"a","language":"ar","url":"https://subs.wing.st/sub/a","type":"srt","display":"Arabic"},
            {"id":"b","language":"en","url":"https://subs.wing.st/sub/b","type":"srt","display":"English (YIFY)"},
            {"id":"c","language":"en","url":"https://subs.wing.st/sub/c","type":"sub","display":"English"},
            {"id":"d","language":"ar","url":"https://subs.wing.st/sub/d","type":"srt","display":"Arabic 2"}]}"""
        val subs = SubLists.parseWing(json)
        assertEquals(listOf("https://subs.wing.st/sub/b", "https://subs.wing.st/sub/a"), subs.map { it.url })
        assertEquals("en", subs.first().lang)
        assertEquals("English (YIFY)", subs.first().label)
    }

    @Test
    fun parsesLabelFileSubtitles() {
        val json = """[{"label":"Bulgarian","file":"https://rezesubs.com/bg.vtt"},{"label":"English","file":"https://rezesubs.com/en.vtt"},{"label":"English2","file":"https://rezesubs.com/en2.vtt"}]"""
        val subs = SubLists.parseLabelFile(json)
        assertEquals(listOf("en", "en", "bg"), subs.map { it.lang })
        assertTrue(SubLists.parseLabelFile("{\"error\":1}").isEmpty())
    }

    @Test
    fun workingServersComeFirstAndVidZeeTvUsesPathEpisodes() {
        assertEquals(listOf("ZEE", "ROCK", "MAP", "111M"), EmbedServers.playable.take(4).map { it.code })
        assertEquals(EmbedServers.all.count { it.web }, EmbedServers.playable.size)
        val zee = EmbedServers.all.first { it.code == "ZEE" }
        assertEquals("https://player.vidzee.wtf/embed/tv/1399/1/2", zee.url(MediaKind.TV, 1399, 1, 2))
        assertEquals("https://core.vidzee.wtf/subs/movie/550", zee.subsUrl(MediaKind.MOVIE, 550))
        assertFalse(EmbedServers.all.first { it.code == "MAP" }.sniffs)
        assertNull(EmbedServers.all.first { it.code == "VCC" }.subsUrl(MediaKind.MOVIE, 550))
    }
}
