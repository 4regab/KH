package com.kisskh.tv.api

import android.content.Context
import android.content.SharedPreferences
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbPaths
import com.kisskh.tv.api.source.TitleCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a card or a title page says about a title that cannot be played right now. */
sealed interface TitleMark {
    /** Not out yet. [date] is the release date, yyyy-MM-dd. */
    data class ComingSoon(val date: String) : TitleMark

    /** Every server failed the last time the viewer tried it. Cleared by the next successful play. */
    object Unavailable : TitleMark
}

/** Release dates as TMDB writes them (yyyy-MM-dd). Pure Kotlin, so the JVM tests can run it. */
object ReleaseDates {
    private val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    private val shape = Regex("""(\d{4})-(\d{2})-(\d{2})""")

    private fun parts(date: String?): Triple<Int, Int, Int>? {
        val m = shape.matchEntire(date?.trim().orEmpty()) ?: return null
        val (y, mo, d) = m.destructured
        val month = mo.toInt()
        val day = d.toInt()
        return if (month in 1..12 && day in 1..31) Triple(y.toInt(), month, day) else null
    }

    /**
     * True when [date] is a real date after [today] (yyyy-MM-dd).
     * ponytail: a missing or odd date counts as released, so a gap in the data never blocks a title.
     */
    fun isUnreleased(date: String?, today: String = TmdbPaths.todayUtc()): Boolean =
        parts(date) != null && date!!.trim() > today

    /** "Dec 16", or null when [date] is not a date. */
    fun short(date: String?): String? = parts(date)?.let { (_, month, day) -> "${months[month - 1].take(3)} $day" }

    /** "December 16, 2026", or null when [date] is not a date. */
    fun long(date: String?): String? = parts(date)?.let { (year, month, day) -> "${months[month - 1]} $day, $year" }
}

/** When a "Not available" mark runs out. */
object AvailabilityRules {
    /** A mark is trusted for three days, then the title is tried again. */
    const val EXPIRY_MS = 3L * 24 * 60 * 60 * 1000

    /** True while a mark made at [at] still counts at [now]. A mark from the future (clock set back) does not. */
    fun fresh(at: Long?, now: Long): Boolean = at != null && now - at in 0 until EXPIRY_MS

    /** The mark [card] shows: Coming soon first, then Not available when [unavailable], else none. */
    fun markFor(card: TitleCard, unavailable: Boolean, today: String = TmdbPaths.todayUtc()): TitleMark? = when {
        ReleaseDates.isUnreleased(card.releaseDate, today) -> TitleMark.ComingSoon(card.releaseDate!!.trim())
        unavailable -> TitleMark.Unavailable
        else -> null
    }
}

/** The key one title is marked under: the same `<source>:<movie|tv>:<id>` as My List. */
fun availabilityKey(source: String, kind: MediaKind, id: String): String = savedKey(source, kind, id)

/**
 * Titles where every server failed, kept in their own `availability` preference file as
 * key -> time of the failure. A mark runs out after [AvailabilityRules.EXPIRY_MS] and goes as soon
 * as any playback of the title works. A title is never hidden because of a mark.
 *
 * - [version] goes up after every change, so a screen that reads it redraws.
 * - ponytail: the mark is for the whole title. For a series it comes from the one episode the viewer
 *   tried, so a later episode may still play; that play clears the mark.
 */
class Availability(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val marks = HashMap<String, Long>()
    private val _version = MutableStateFlow(0)

    /** Goes up by one after every change. */
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        val now = clock()
        val old = ArrayList<String>()
        for ((key, value) in prefs.all) {
            val at = value as? Long
            if (AvailabilityRules.fresh(at, now)) marks[key] = at!! else old += key
        }
        if (old.isNotEmpty()) prefs.edit().also { editor -> old.forEach { editor.remove(it) } }.apply()
    }

    @Synchronized fun isUnavailable(key: String): Boolean = AvailabilityRules.fresh(marks[key], clock())

    /** The mark for [card], or null. */
    @Synchronized fun markFor(card: TitleCard): TitleMark? =
        AvailabilityRules.markFor(card, isUnavailable(availabilityKey(card.source.key, card.kind, card.id)))

    /** Records that every server failed for [key] now. */
    @Synchronized fun markUnavailable(key: String) {
        val now = clock()
        marks[key] = now
        prefs.edit().putLong(key, now).apply()
        _version.value = _version.value + 1
    }

    /** Clears the mark of [key]. Does nothing when there is none. */
    @Synchronized fun clear(key: String) {
        if (marks.remove(key) == null) return
        prefs.edit().remove(key).apply()
        _version.value = _version.value + 1
    }

    companion object {
        const val PREFS = "availability"

        @Volatile private var shared: Availability? = null

        /** One store per process, so every screen sees the same marks. */
        fun create(context: Context): Availability = shared ?: synchronized(this) {
            shared ?: Availability(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).also { shared = it }
        }
    }
}
