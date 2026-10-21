package com.kisskh.tv.api.media

import android.content.SharedPreferences
import com.kisskh.tv.api.ResumeDecision
import com.kisskh.tv.api.ResumeRules

/**
 * Progress for every source except KissKH.
 *
 * KissKH stays in the `resume` and `history` files (`ep_<id>` and `<dramaId>.<field>`). Nothing
 * here writes those keys. Keys look like `cw:movie:550`, `cj:tv:1396:1:2` and
 * `nx:tv:9e01bcf0-14ec-4ab0-aebb-9112064725f5:1:3`. The id segment is text so a UUID fits.
 *
 * An embed has no readable playback position (the video is a third-party page), so opening one
 * only records the title. A direct stream uses the same save rules as KissKH.
 */
object MediaKeys {
    private val sources = setOf("cw", "cj", "rx", "as", "nx")

    fun movie(source: String, id: Int) = movie(source, id.toString())
    fun movie(source: String, id: String) = "$source:movie:$id"
    fun episode(source: String, id: Int, season: Int, episode: Int) = episode(source, id.toString(), season, episode)
    fun episode(source: String, id: String, season: Int, episode: Int) = "$source:tv:$id:$season:$episode"

    fun valid(key: String): Boolean {
        if (key.startsWith("ep_")) return false
        val parts = key.split(":")
        if (parts.size < 3 || parts[0] !in sources || parts[2].isBlank()) return false
        return (parts[1] == "movie" && parts.size == 3) || (parts[1] == "tv" && parts.size == 5)
    }
}

data class MediaEntry(
    val key: String,
    val source: String,
    val kind: String,
    val id: Int,
    /** The id as stored in the key. Numeric titles keep [id]; a UUID stays here when [id] is 0. */
    val token: String = id.toString(),
    val season: Int,
    val episode: Int,
    val title: String,
    val poster: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val finished: Boolean,
) {
    val resumable: Boolean get() = !finished && positionMs > 0
}

class MediaProgress(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun position(key: String): Long =
        if (prefs.getBoolean(done(key), false)) 0L else prefs.getLong(pos(key), 0L)

    fun fraction(key: String): Float? {
        val at = position(key)
        val dur = prefs.getLong(dur(key), 0L)
        if (at <= 0L || dur <= 0L) return null
        return (at.toFloat() / dur).coerceIn(0f, 1f)
    }

    /** Remember that this title was opened, without inventing a playback position. */
    fun remember(key: String, title: String, poster: String?) {
        require(MediaKeys.valid(key)) { "bad progress key" }
        val editor = prefs.edit()
        writeMeta(editor, key, title, poster)
        writeOrder(editor, key)
        editor.apply()
    }

    /**
     * Save a real playback position. Returns false when [ResumeRules] says to leave the stored
     * position alone (just started, or the duration is not known yet).
     */
    fun save(key: String, title: String, poster: String?, positionMs: Long, durationMs: Long): Boolean {
        require(MediaKeys.valid(key)) { "bad progress key" }
        val decision = ResumeRules.decide(positionMs, durationMs)
        if (decision is ResumeDecision.Ignore) return false
        val editor = prefs.edit()
        writeMeta(editor, key, title, poster)
        when (decision) {
            is ResumeDecision.Save -> {
                editor.putLong(pos(key), decision.positionMs)
                editor.putLong(dur(key), durationMs)
                editor.putBoolean(done(key), false)
            }
            ResumeDecision.Finished -> {
                editor.remove(pos(key))
                if (durationMs > 0L) editor.putLong(dur(key), durationMs)
                editor.putBoolean(done(key), true)
            }
            ResumeDecision.Ignore -> Unit
        }
        writeOrder(editor, key)
        editor.apply()
        return true
    }

    fun entries(): List<MediaEntry> {
        val keys = prefs.getString(ORDER, "").orEmpty().split('\n').filter { it.isNotBlank() }
        return keys.mapNotNull { key ->
            if (!MediaKeys.valid(key)) return@mapNotNull null
            val parts = key.split(":")
            val title = prefs.getString(title(key), null) ?: return@mapNotNull null
            val token = parts[2]
            MediaEntry(
                key = key,
                source = parts[0],
                kind = parts[1],
                id = token.toIntOrNull() ?: 0,
                token = token,
                season = parts.getOrNull(3)?.toIntOrNull() ?: 0,
                episode = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                title = title,
                poster = prefs.getString(poster(key), null)?.takeIf { it.isNotBlank() },
                positionMs = prefs.getLong(pos(key), 0L),
                durationMs = prefs.getLong(dur(key), 0L),
                updatedAt = prefs.getLong(at(key), 0L),
                finished = prefs.getBoolean(done(key), false),
            )
        }
    }

    fun continuing(source: String): List<MediaEntry> = entries().filter { it.source == source && it.resumable }

    fun recent(source: String): List<MediaEntry> = entries().filter { it.source == source && !it.resumable }

    private fun writeMeta(editor: SharedPreferences.Editor, key: String, title: String, poster: String?) {
        editor.putString(title(key), title)
        if (poster.isNullOrBlank()) editor.remove(poster(key)) else editor.putString(poster(key), poster)
        editor.putLong(at(key), clock())
    }

    private fun writeOrder(editor: SharedPreferences.Editor, key: String) {
        val rest = prefs.getString(ORDER, "").orEmpty().split('\n').filter { it.isNotBlank() && it != key }
        editor.putString(ORDER, (listOf(key) + rest).joinToString("\n"))
    }

    private fun pos(key: String) = "pos:$key"
    private fun dur(key: String) = "dur:$key"
    private fun at(key: String) = "at:$key"
    private fun title(key: String) = "title:$key"
    private fun poster(key: String) = "poster:$key"
    private fun done(key: String) = "done:$key"

    companion object {
        const val PREFS = "media_progress"
        private const val ORDER = "order"
    }
}

/** The Febbox `ui` token, kept out of the KissKH preference files. */
class CineJoyToken(private val prefs: SharedPreferences) {
    fun get(): String = prefs.getString(KEY, "").orEmpty().trim()

    fun set(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) prefs.edit().remove(KEY).apply() else prefs.edit().putString(KEY, value).apply()
    }

    companion object {
        const val PREFS = "cinejoy"
        const val KEY = "fem_token"
    }
}
