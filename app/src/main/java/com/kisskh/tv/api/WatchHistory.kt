package com.kisskh.tv.api

import android.content.SharedPreferences

/** What to do with a playback position. Produced by [ResumeRules.decide]. */
sealed interface ResumeDecision {
    /** Remember this position. */
    data class Save(val positionMs: Long) : ResumeDecision

    /** The viewer reached the end: forget the position. */
    data object Finished : ResumeDecision

    /** Too early, or the duration is not known yet: change nothing. */
    data object Ignore : ResumeDecision
}

/** The one place that decides which playback positions are worth keeping. */
object ResumeRules {
    /** Positions at or below this are "just started" and are not saved. */
    const val MIN_POSITION_MS = 3_000L

    /** Within this distance of the end counts as finished. */
    const val END_MARGIN_MS = 5_000L

    /**
     * An unknown duration (Media3 `C.TIME_UNSET` is negative, some streams report 0) or an unknown
     * position is [ResumeDecision.Ignore]: it must never erase a good saved position.
     */
    fun decide(positionMs: Long, durationMs: Long): ResumeDecision = when {
        durationMs <= 0 || positionMs < 0 -> ResumeDecision.Ignore
        positionMs >= durationMs - END_MARGIN_MS -> ResumeDecision.Finished
        positionMs <= MIN_POSITION_MS -> ResumeDecision.Ignore
        else -> ResumeDecision.Save(positionMs)
    }
}

/**
 * The last episode watched for one title.
 *
 * - [positionMs] is 0 when [finished] is true.
 * - [durationMs] may be 0 if it was never known.
 */
data class WatchEntry(
    val dramaId: Int,
    val episodeId: Int,
    val episodeNumber: Float,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val title: String?,
    val thumbnail: String?,
    val finished: Boolean,
) {
    /** True if there is a spot inside the episode to resume from. */
    val resumable: Boolean get() = !finished && positionMs > 0

    /** 0..1, or null when the duration is unknown. */
    val progress: Float? get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
}

/**
 * Durable watch progress, in two SharedPreferences files:
 *
 * - "resume": `ep_<episodeId>` -> position in ms. Written since the first version of the app. Keep it.
 * - "history": one entry per title (its last episode), keys `<dramaId>.<field>`.
 *
 * Both follow [ResumeRules]. History is never pruned (one small entry per title watched).
 */
class WatchHistory(
    private val resume: SharedPreferences,
    private val history: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Saved position for an episode, or 0 if none. */
    fun loadPosition(episodeId: Int): Long = resume.getLong(resumeKey(episodeId), 0L)

    /** Per-episode position only (no title entry). Same rules as [record]. */
    fun savePosition(episodeId: Int, positionMs: Long, durationMs: Long) {
        when (val d = ResumeRules.decide(positionMs, durationMs)) {
            is ResumeDecision.Save -> resume.edit().putLong(resumeKey(episodeId), d.positionMs).apply()
            ResumeDecision.Finished -> resume.edit().remove(resumeKey(episodeId)).apply()
            ResumeDecision.Ignore -> Unit
        }
    }

    /**
     * Save progress for an episode: its position, and the title's "last watched" entry.
     * Call it often while playing and again when playback stops.
     */
    fun record(
        dramaId: Int,
        episodeId: Int,
        episodeNumber: Float,
        title: String?,
        thumbnail: String?,
        positionMs: Long,
        durationMs: Long,
    ) {
        val decision = ResumeRules.decide(positionMs, durationMs)
        if (decision == ResumeDecision.Ignore) return
        savePosition(episodeId, positionMs, durationMs)
        val finished = decision == ResumeDecision.Finished
        history.edit()
            .putInt(key(dramaId, EP), episodeId)
            .putFloat(key(dramaId, NUM), episodeNumber)
            .putLong(key(dramaId, POS), if (finished) 0L else positionMs)
            .putLong(key(dramaId, DUR), durationMs)
            .putLong(key(dramaId, AT), clock())
            .putString(key(dramaId, TITLE), title)
            .putString(key(dramaId, THUMB), thumbnail)
            .putBoolean(key(dramaId, DONE), finished)
            .apply()
    }

    /** The last episode watched for this title, or null if none. */
    fun entry(dramaId: Int): WatchEntry? {
        if (!history.contains(key(dramaId, EP))) return null
        return WatchEntry(
            dramaId = dramaId,
            episodeId = history.getInt(key(dramaId, EP), 0),
            episodeNumber = history.getFloat(key(dramaId, NUM), 0f),
            positionMs = history.getLong(key(dramaId, POS), 0L),
            durationMs = history.getLong(key(dramaId, DUR), 0L),
            updatedAt = history.getLong(key(dramaId, AT), 0L),
            title = history.getString(key(dramaId, TITLE), null),
            thumbnail = history.getString(key(dramaId, THUMB), null),
            finished = history.getBoolean(key(dramaId, DONE), false),
        )
    }

    /** Titles with a position to resume from, newest first (for a Continue Watching row). */
    fun recent(limit: Int = 20): List<WatchEntry> =
        history.all.keys
            .filter { it.endsWith(".$EP") }
            .mapNotNull { it.removeSuffix(".$EP").toIntOrNull() }
            .mapNotNull { entry(it) }
            .filter { it.resumable }
            .sortedByDescending { it.updatedAt }
            .take(limit)

    private fun resumeKey(episodeId: Int) = "ep_$episodeId"
    private fun key(dramaId: Int, field: String) = "$dramaId.$field"

    companion object {
        private const val EP = "ep"
        private const val NUM = "num"
        private const val POS = "pos"
        private const val DUR = "dur"
        private const val AT = "at"
        private const val TITLE = "title"
        private const val THUMB = "thumb"
        private const val DONE = "done"

        fun create(context: android.content.Context): WatchHistory {
            val app = context.applicationContext
            return WatchHistory(
                resume = app.getSharedPreferences("resume", android.content.Context.MODE_PRIVATE),
                history = app.getSharedPreferences("history", android.content.Context.MODE_PRIVATE),
            )
        }
    }
}
