package com.kisskh.tv.download

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.TitleCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUiTest {
    private fun card(source: Library, id: String, kind: MediaKind = MediaKind.TV, backdrop: String? = "wide", poster: String? = "tall") =
        TitleCard(source, id, kind, "Name", poster, backdrop, null, null)

    private fun item(phase: DownloadPhase, percent: Float = -1f): DownloadItem {
        val meta = DownloadMeta("id", "T", "L", null, "S", KIND_EPISODE, null, 0, 0, 0f, emptyList(), emptyMap(), 0L)
        return DownloadItem("id", meta, phase, percent, 0L, -1L)
    }

    @Test
    fun noDownloadStartsAsDownload() {
        assertEquals(DownloadStage.START, downloadUi(null, finding = false, preparing = false).stage)
        assertEquals("Download", downloadUi(null, false, false).label)
    }

    @Test
    fun findingOrPreparingShowsTheSpinner() {
        assertEquals(DownloadStage.FINDING, downloadUi(null, finding = true, preparing = false).stage)
        assertEquals(DownloadStage.FINDING, downloadUi(null, finding = false, preparing = true).stage)
    }

    @Test
    fun failedDownloadOffersRetryUnlessItIsStartingAgain() {
        assertEquals(DownloadStage.RETRY, downloadUi(item(DownloadPhase.FAILED), false, false).stage)
        assertEquals(DownloadStage.FINDING, downloadUi(item(DownloadPhase.FAILED), true, false).stage)
    }

    @Test
    fun runningDownloadShowsPercent() {
        val ui = downloadUi(item(DownloadPhase.DOWNLOADING, 42.7f), false, false)
        assertEquals(DownloadStage.DOWNLOADING, ui.stage)
        assertEquals(42, ui.percent)
        assertEquals("Downloading 42%", ui.label)
        assertEquals("Downloading", downloadUi(item(DownloadPhase.DOWNLOADING, -1f), false, false).label)
    }

    @Test
    fun otherPhasesMapOneToOne() {
        assertEquals(DownloadStage.QUEUED, downloadUi(item(DownloadPhase.QUEUED), false, false).stage)
        assertEquals(DownloadStage.WAITING, downloadUi(item(DownloadPhase.WAITING_NETWORK), false, false).stage)
        assertEquals(DownloadStage.PAUSED, downloadUi(item(DownloadPhase.PAUSED), false, false).stage)
        val done = downloadUi(item(DownloadPhase.COMPLETED, 100f), false, false)
        assertEquals(DownloadStage.DONE, done.stage)
        assertEquals("Downloaded", done.label)
    }

    @Test
    fun everyStageHasADescriptionWithTheName() {
        for (stage in DownloadStage.entries) {
            assertTrue("$stage", "Pilot" in DownloadUi(stage, 10).description("Pilot"))
        }
    }

    @Test
    fun idsMatchTheOnesTheOldPlayerButtonUsed() {
        val episode = EpisodeRef("9", 1, 2, "Cat's in the Bag", null)
        assertEquals("cw:tv:1396:1:2", downloadIdFor(Library.CINEWAVE, card(Library.CINEWAVE, "1396"), episode))
        assertEquals("rx:tv:77:1:2", downloadIdFor(Library.REELIX, card(Library.REELIX, "77"), episode))
        assertEquals("cw:movie:603", downloadIdFor(Library.CINEWAVE, card(Library.CINEWAVE, "603", MediaKind.MOVIE), EpisodeRef("603", 0, 1, "Movie", null)))
        assertEquals("kh:ep:9", downloadIdFor(Library.KISSKH, card(Library.KISSKH, "55"), episode))
    }

    @Test
    fun episodesWithoutAUsableIdCannotBeDownloaded() {
        assertNull(downloadIdFor(Library.KISSKH, card(Library.KISSKH, "55"), EpisodeRef("abc", 1, 1, "", null)))
        assertNull(downloadIdFor(Library.KISSKH, card(Library.KISSKH, "x"), EpisodeRef("9", 1, 1, "", null)))
    }

    @Test
    fun playerDestMatchesTheTitlePageEpisode() {
        val dest = watchDest(Library.CINEWAVE, card(Library.CINEWAVE, "1396"), EpisodeRef("e", 0, 0, "Pilot", null, slug = "s", token = "t"))
        assertEquals(1, dest.season)
        assertEquals(1, dest.episode)
        assertEquals("t", dest.episodeToken)
        assertEquals("tall", dest.poster)
    }

    @Test
    fun previewPrefersWidePictures() {
        val episode = EpisodeRef("1", 1, 1, "", "still")
        assertEquals("still", previewFor(card(Library.CINEWAVE, "1"), episode))
        assertEquals("wide", previewFor(card(Library.CINEWAVE, "1", MediaKind.MOVIE), episode))
        assertEquals("wide", previewFor(card(Library.CINEWAVE, "1"), EpisodeRef("1", 1, 1, "", null)))
        assertEquals("tall", previewFor(card(Library.CINEWAVE, "1", backdrop = null), EpisodeRef("1", 1, 1, "", null)))
        assertNull(previewFor(card(Library.CINEWAVE, "1", backdrop = null, poster = null), EpisodeRef("1", 1, 1, "", " ")))
    }

    @Test
    fun kisskhLabelsMatchThePlayer() {
        val a = EpisodeRef("1", 1, 1, "Episode 1", null)
        val b = EpisodeRef("2", 1, 2, "Episode 2", null)
        assertEquals("Episode 2", kisskhLabel(card(Library.KISSKH, "5"), b, listOf(a, b)))
        assertEquals("Movie", kisskhLabel(card(Library.KISSKH, "5", MediaKind.MOVIE), a, listOf(a)))
        assertEquals("Part 2", kisskhLabel(card(Library.KISSKH, "5", MediaKind.MOVIE), b, listOf(a, b)))
    }
}
