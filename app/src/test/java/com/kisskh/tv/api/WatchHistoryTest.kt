package com.kisskh.tv.api

import android.content.SharedPreferences
import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory SharedPreferences. */
class FakePrefs : SharedPreferences {
    val map = HashMap<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String?, defValue: String?) = map[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String?) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val puts = HashMap<String, Any?>()
        private var clear = false
        private fun put(k: String?, v: Any?): SharedPreferences.Editor {
            puts[k!!] = v
            return this
        }
        override fun putString(k: String?, v: String?) = put(k, v)
        override fun putStringSet(k: String?, v: MutableSet<String>?) = put(k, v)
        override fun putInt(k: String?, v: Int) = put(k, v)
        override fun putLong(k: String?, v: Long) = put(k, v)
        override fun putFloat(k: String?, v: Float) = put(k, v)
        override fun putBoolean(k: String?, v: Boolean) = put(k, v)
        override fun remove(k: String?) = put(k, null)
        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }
        override fun commit(): Boolean {
            if (clear) map.clear()
            for ((k, v) in puts) if (v == null) map.remove(k) else map[k] = v
            return true
        }
        override fun apply() { commit() }
    }
}

class ResumeRulesTest {
    private val dur = 100_000L

    @Test
    fun midEpisodeIsSaved() {
        assertEquals(ResumeDecision.Save(50_000), ResumeRules.decide(50_000, dur))
    }

    @Test
    fun nearStartIsIgnored() {
        assertEquals(ResumeDecision.Ignore, ResumeRules.decide(0, dur))
        assertEquals(ResumeDecision.Ignore, ResumeRules.decide(3_000, dur))
        assertEquals(ResumeDecision.Save(3_001), ResumeRules.decide(3_001, dur))
    }

    @Test
    fun nearEndIsFinished() {
        assertEquals(ResumeDecision.Save(94_999), ResumeRules.decide(94_999, dur))
        assertEquals(ResumeDecision.Finished, ResumeRules.decide(95_000, dur))
        assertEquals(ResumeDecision.Finished, ResumeRules.decide(dur, dur))
    }

    @Test
    fun unknownDurationOrPositionIsIgnored() {
        assertEquals(ResumeDecision.Ignore, ResumeRules.decide(50_000, C.TIME_UNSET))
        assertEquals(ResumeDecision.Ignore, ResumeRules.decide(50_000, 0))
        assertEquals(ResumeDecision.Ignore, ResumeRules.decide(50_000, -1))
        assertEquals(ResumeDecision.Ignore, ResumeRules.decide(C.TIME_UNSET, dur))
    }
}

class WatchHistoryTest {
    private val resume = FakePrefs()
    private val history = FakePrefs()
    private var now = 1_000L
    private val h = WatchHistory(resume, history, clock = { now })

    private fun record(drama: Int = 7, ep: Int = 70, pos: Long, dur: Long = 100_000, num: Float = 5f) =
        h.record(drama, ep, num, "Show", "http://img/1.jpg", pos, dur)

    @Test
    fun keepsTheOldResumeKeyAndFile() {
        record(pos = 50_000)
        assertEquals(50_000L, resume.map["ep_70"]) // same prefs name "resume" / key as the first app version
        assertEquals(50_000L, h.loadPosition(70))
    }

    @Test
    fun legacySavePositionBehavesLikeRecordWithoutHistory() {
        h.savePosition(70, 40_000, 100_000)
        assertEquals(40_000L, h.loadPosition(70))
        h.savePosition(70, 99_000, 100_000) // finished clears
        assertEquals(0L, h.loadPosition(70))
        assertTrue(history.map.isEmpty())
    }

    @Test
    fun recordsTheTitleEntry() {
        now = 5_000
        record(pos = 50_000, num = 12.5f)
        val e = h.entry(7)!!
        assertEquals(WatchEntry(7, 70, 12.5f, 50_000, 100_000, 5_000, "Show", "http://img/1.jpg", finished = false), e)
        assertTrue(e.resumable)
        assertEquals(0.5f, e.progress!!, 0.0001f)
    }

    @Test
    fun noEntryForATitleNeverWatched() {
        assertNull(h.entry(1))
        assertTrue(h.recent().isEmpty())
    }

    @Test
    fun nearStartIsIgnoredAndKeepsEarlierProgress() {
        record(pos = 50_000)
        now = 2_000
        record(pos = 1_000) // user opened it again and left at once
        assertEquals(50_000L, h.loadPosition(70))
        assertEquals(50_000L, h.entry(7)!!.positionMs)
        assertEquals(1_000L, h.entry(7)!!.updatedAt)
    }

    @Test
    fun nearStartCreatesNothing() {
        record(pos = 2_000)
        assertNull(h.entry(7))
        assertEquals(0L, h.loadPosition(70))
    }

    @Test
    fun unknownDurationNeverErasesProgress() {
        record(pos = 50_000)
        record(pos = 0, dur = C.TIME_UNSET)
        record(pos = 60_000, dur = 0)
        assertEquals(50_000L, h.loadPosition(70))
        assertEquals(50_000L, h.entry(7)!!.positionMs)
    }

    @Test
    fun nearEndClearsThePositionAndMarksFinished() {
        record(pos = 50_000)
        now = 3_000
        record(pos = 98_000)
        assertEquals(0L, h.loadPosition(70))
        assertFalse(resume.map.containsKey("ep_70"))
        val e = h.entry(7)!!
        assertTrue(e.finished)
        assertFalse(e.resumable)
        assertEquals(0L, e.positionMs)
        assertEquals(70, e.episodeId) // we still know which episode was last
        assertEquals(3_000L, e.updatedAt)
    }

    @Test
    fun entryFollowsTheLatestEpisode() {
        record(ep = 70, pos = 98_000, num = 5f) // finished ep 5
        record(ep = 71, pos = 30_000, num = 6f) // started ep 6
        val e = h.entry(7)!!
        assertEquals(71, e.episodeId)
        assertEquals(6f, e.episodeNumber)
        assertFalse(e.finished)
        assertEquals(30_000L, e.positionMs)
    }

    @Test
    fun progressIsNullWhenDurationWasNeverKnown() {
        val e = WatchEntry(1, 2, 1f, 5_000, 0, 0, null, null, finished = false)
        assertNull(e.progress)
    }

    @Test
    fun recentListsResumableTitlesNewestFirst() {
        now = 1; h.record(1, 10, 1f, "A", null, 30_000, 100_000)
        now = 3; h.record(2, 20, 1f, "B", null, 30_000, 100_000)
        now = 2; h.record(3, 30, 1f, "C", null, 30_000, 100_000)
        now = 4; h.record(4, 40, 1f, "D", null, 99_000, 100_000) // finished: not listed
        assertEquals(listOf(2, 3, 1), h.recent().map { it.dramaId })
        assertEquals(listOf(2), h.recent(limit = 1).map { it.dramaId })
    }

    @Test
    fun titleAndThumbnailMayBeMissing() {
        h.record(9, 90, 1f, null, null, 30_000, 100_000)
        val e = h.entry(9)!!
        assertNull(e.title)
        assertNull(e.thumbnail)
    }
}
