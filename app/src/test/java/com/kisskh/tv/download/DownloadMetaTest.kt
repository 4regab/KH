package com.kisskh.tv.download

import com.kisskh.tv.api.SubtitleFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadMetaTest {
    private val offer = DownloadOffer(
        id = DownloadIds.media("cw:tv:1396:1:2"),
        url = "https://cdn.example/master.m3u8",
        headers = mapOf("Referer" to "https://site.example/", "User-Agent" to "Test/1.0"),
        subs = listOf(SubtitleFile("https://cdn.example/en.vtt", "English", "en")),
        title = "Breaking \"Quotes\" \u2014 Test",
        label = "S1 E2 \u00B7 Cat's in the Bag",
        poster = null,
        source = "CineWave",
        kind = KIND_EPISODE,
        progressKey = "cw:tv:1396:1:2",
        episodeNumber = 2.5f,
    )

    @Test
    fun metadataSurvivesJson() {
        val saved = listOf(DownloadSub("/data/files/downloads/subs/cw_tv_1396_1_2/0.vtt", "English", "en"))
        val meta = offer.toMeta(saved, now = 1_700_000_000_000L)
        val back = DownloadMeta.fromJson(meta.toJson().toByteArray(Charsets.UTF_8))
        assertEquals(meta, back)
        assertEquals("Referer and User-Agent are kept", offer.headers, back?.headers)
    }

    @Test
    fun damagedOrEmptyDataGivesNull() {
        assertNull(DownloadMeta.fromJson(null))
        assertNull(DownloadMeta.fromJson(ByteArray(0)))
        assertNull(DownloadMeta.fromJson("not json".toByteArray()))
        assertNull(DownloadMeta.fromJson("""{"title":"no id"}""".toByteArray()))
    }

    @Test
    fun idsAreStablePerTitle() {
        assertEquals("kh:ep:42", DownloadIds.kisskh(42))
        assertEquals(DownloadIds.kisskh(42), DownloadIds.kisskh(42))
        assertEquals("cw:movie:550", DownloadIds.media("cw:movie:550"))
        assertEquals("kh_ep_42", DownloadIds.dirName("kh:ep:42"))
        assertEquals("nx_tv_9e01-bcf0_1_3", DownloadIds.dirName("nx:tv:9e01-bcf0:1:3"))
    }

    @Test
    fun movieWithoutLabelIsCalledMovie() {
        val meta = offer.copy(kind = KIND_MOVIE, label = "").toMeta(emptyList(), now = 1L)
        assertEquals("Movie", meta.label)
    }

    @Test
    fun statusLinesRead() {
        assertEquals("Downloading 42%", downloadStatus(DownloadPhase.DOWNLOADING, 42.7f, 100L, -1L))
        assertEquals("Queued", downloadStatus(DownloadPhase.QUEUED, -1f, 0L, -1L))
        assertEquals("Paused - waiting for network", downloadStatus(DownloadPhase.WAITING_NETWORK, 10f, 0L, -1L))
        assertEquals("Failed", downloadStatus(DownloadPhase.FAILED, 10f, 0L, -1L))
        assertEquals("Ready \u00B7 1.2 GB", downloadStatus(DownloadPhase.COMPLETED, 100f, 0L, 1_288_490_189L))
        assertEquals("Ready \u00B7 350 MB", downloadStatus(DownloadPhase.COMPLETED, 100f, 0L, 367_001_600L))
    }
}
