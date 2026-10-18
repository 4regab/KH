package com.kisskh.tv.ui

import com.kisskh.tv.api.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeRangesTest {
    private fun eps(vararg numbers: Float) = numbers.mapIndexed { i, n -> Episode(id = 100 + i, number = n) }

    @Test fun fractionalEpisodeStaysInTheFirstBlock() {
        val ranges = episodeRanges(eps(29.6f, 1f, 50f))
        assertEquals(1, ranges.size)
        assertEquals(1, ranges[0].start)
        assertEquals(50, ranges[0].end)
        assertEquals(listOf(1f, 29.6f, 50f), ranges[0].episodes.map { it.number })
    }

    @Test fun fiftyOneStartsTheNextBlock() {
        val ranges = episodeRanges(eps(50f, 51f, 50.9f))
        assertEquals(listOf(1, 51), ranges.map { it.start })
        assertEquals(listOf(50f, 50.9f), ranges[0].episodes.map { it.number })
        assertEquals(listOf(51f), ranges[1].episodes.map { it.number })
    }

    @Test fun emptyInputHasNoRanges() {
        assertTrue(episodeRanges(emptyList()).isEmpty())
    }

    @Test fun eightHundredEpisodesMakeSixteenBlocksAndSkipNothing() {
        val episodes = (1..800).map { Episode(id = it, number = it.toFloat()) }
        val ranges = episodeRanges(episodes)
        assertEquals(16, ranges.size)
        assertEquals(1, ranges.first().start)
        assertEquals(50, ranges.first().end)
        assertEquals(751, ranges.last().start)
        assertEquals(800, ranges.last().end)
        assertEquals(800, ranges.sumOf { it.episodes.size })
        assertEquals(listOf(751f, 800f), listOf(ranges.last().episodes.first().number, ranges.last().episodes.last().number))
    }

    @Test fun aGapDoesNotCreateAnEmptyBlock() {
        val ranges = episodeRanges(eps(1f, 120f))
        assertEquals(listOf("1-50", "101-150"), ranges.map { "${it.start}-${it.end}" })
        assertTrue(ranges.all { it.episodes.isNotEmpty() })
    }

    @Test fun newestFirstInputIsShownInWatchOrderInsideTheBlock() {
        val ranges = episodeRanges(eps(3f, 1f, 2f))
        assertEquals(listOf(1f, 2f, 3f), ranges.single().episodes.map { it.number })
    }
}
