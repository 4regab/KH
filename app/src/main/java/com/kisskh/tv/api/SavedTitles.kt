package com.kisskh.tv.api

import android.content.Context
import android.content.SharedPreferences
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.TitleCard
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.Buffer

/** Title of the Home row that holds the viewer's saved titles. It sits right after Continue Watching. */
const val MY_LIST_ROW = "My List"

/**
 * The key one title is saved under: `<source>:<movie|tv>:<id>`, for example `cw:movie:550`.
 * Series and anime both use `tv` so the same show saved from Browse and from Search is one entry.
 * KissKH always uses `tv`: its ids are unique across kinds, and its Continue cards do not know
 * whether a drama is a film.
 */
fun savedKey(source: String, kind: MediaKind, id: String): String {
    val segment = if (kind == MediaKind.MOVIE && source != Library.KISSKH.key) "movie" else "tv"
    return "$source:$segment:$id"
}

fun savedKey(card: TitleCard): String = savedKey(card.source.key, card.kind, card.id)

/** Enough of a title to draw its card again later. [at] is when it was added or marked. */
data class SavedTitle(
    val source: String,
    val kind: MediaKind,
    val id: String,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val year: String?,
    val slug: String,
    val at: Long,
) {
    val key: String get() = savedKey(source, kind, id)

    /** The card for this title, or null when the source is not one this app knows any more. */
    fun toCard(sourceLabelAsMeta: Boolean = false): TitleCard? {
        val library = Library.entries.firstOrNull { it.key == source } ?: return null
        return TitleCard(
            source = library,
            id = id,
            kind = kind,
            name = name,
            poster = poster,
            backdrop = backdrop,
            year = year,
            overview = null,
            slug = slug,
            meta = if (sourceLabelAsMeta) library.label else null,
        )
    }
}

/** Drops the titles the viewer marked as watched (Continue Watching must not offer them). */
fun List<TitleCard>.withoutWatched(watched: Set<String>): List<TitleCard> =
    if (watched.isEmpty()) this else filterNot { savedKey(it) in watched }

/** Saved text <-> lists. Pure Kotlin, so the JVM tests can run it. A damaged text reads as empty. */
internal object SavedCodec {
    fun encodeTitles(items: List<SavedTitle>): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { w ->
            w.beginArray()
            for (t in items) {
                w.beginObject()
                w.name("s").value(t.source)
                w.name("k").value(t.kind.name)
                w.name("i").value(t.id)
                w.name("n").value(t.name)
                w.name("p").value(t.poster)
                w.name("b").value(t.backdrop)
                w.name("y").value(t.year)
                w.name("g").value(t.slug)
                w.name("t").value(t.at)
                w.endObject()
            }
            w.endArray()
        }
        return buffer.readUtf8()
    }

    fun decodeTitles(text: String?): List<SavedTitle> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            JsonReader.of(Buffer().writeUtf8(text)).use { r ->
                val out = ArrayList<SavedTitle>()
                r.beginArray()
                while (r.hasNext()) {
                    var source: String? = null
                    var kind: MediaKind? = null
                    var id: String? = null
                    var name: String? = null
                    var poster: String? = null
                    var backdrop: String? = null
                    var year: String? = null
                    var slug = ""
                    var at = 0L
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "s" -> source = r.string()
                            "k" -> kind = r.string()?.let { n -> MediaKind.entries.firstOrNull { it.name == n } }
                            "i" -> id = r.string()
                            "n" -> name = r.string()
                            "p" -> poster = r.string()
                            "b" -> backdrop = r.string()
                            "y" -> year = r.string()
                            "g" -> slug = r.string().orEmpty()
                            "t" -> at = if (r.peek() == JsonReader.Token.NUMBER) r.nextLong() else { r.skipValue(); 0L }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    if (!source.isNullOrBlank() && kind != null && !id.isNullOrBlank() && !name.isNullOrBlank()) {
                        out += SavedTitle(source, kind, id, name, poster, backdrop, year, slug, at)
                    }
                }
                r.endArray()
                out
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeEpisodes(marks: Set<String>): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { w ->
            w.beginArray()
            for (mark in marks) w.value(mark)
            w.endArray()
        }
        return buffer.readUtf8()
    }

    fun decodeEpisodes(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        return try {
            JsonReader.of(Buffer().writeUtf8(text)).use { r ->
                val out = LinkedHashSet<String>()
                r.beginArray()
                while (r.hasNext()) r.string()?.let { out += it }
                r.endArray()
                out
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun JsonReader.string(): String? = when (peek()) {
        JsonReader.Token.STRING -> nextString()
        JsonReader.Token.NUMBER -> nextString()
        else -> {
            skipValue()
            null
        }
    }
}

/**
 * My List (save for later) and Watched marks, kept in their own `saved` preference file.
 *
 * - My List and Watched hold one entry per title, newest first.
 * - A title can also be marked watched one episode at a time.
 * - [version] goes up after every change, so a screen that reads it redraws.
 *
 * Reads come from memory; every change is written at once.
 * ponytail: the whole list is rewritten on each change, which is fine for hundreds of titles.
 */
class SavedTitles(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var list: List<SavedTitle> = SavedCodec.decodeTitles(prefs.getString(LIST, null))
    private var watched: List<SavedTitle> = SavedCodec.decodeTitles(prefs.getString(WATCHED, null))
    private var episodes: Set<String> = SavedCodec.decodeEpisodes(prefs.getString(EPISODES, null))
    private val _version = MutableStateFlow(0)

    /** Goes up by one after every change. */
    val version: StateFlow<Int> = _version.asStateFlow()

    // ---------------------------------------------------------------- My List

    @Synchronized fun inList(card: TitleCard): Boolean = list.any { it.key == savedKey(card) }

    /** Newest first. */
    @Synchronized fun myList(): List<SavedTitle> = list

    @Synchronized fun addToList(card: TitleCard) {
        if (inList(card)) return
        list = listOf(snapshot(card)) + list
        changed()
    }

    @Synchronized fun removeFromList(card: TitleCard) {
        val key = savedKey(card)
        if (list.none { it.key == key }) return
        list = list.filterNot { it.key == key }
        changed()
    }

    /** Returns true when the title is in My List afterwards. */
    @Synchronized fun toggleList(card: TitleCard): Boolean {
        if (inList(card)) removeFromList(card) else addToList(card)
        return inList(card)
    }

    // ---------------------------------------------------------------- Watched

    @Synchronized fun isWatched(card: TitleCard): Boolean = watched.any { it.key == savedKey(card) }

    /** Newest first. */
    @Synchronized fun watchedList(): List<SavedTitle> = watched

    /** Keys of every title marked watched, for [withoutWatched]. */
    @Synchronized fun watchedKeys(): Set<String> = watched.mapTo(HashSet()) { it.key }

    /** Marks the whole title. Episode marks stay as they are. */
    @Synchronized fun markWatched(card: TitleCard) {
        if (isWatched(card)) return
        watched = listOf(snapshot(card)) + watched
        changed()
    }

    /** Clears the title mark and every episode mark of this title. */
    @Synchronized fun unmarkWatched(card: TitleCard) {
        val key = savedKey(card)
        val rest = episodes.filterNot { it.startsWith("$key|") }.toSet()
        if (watched.none { it.key == key } && rest.size == episodes.size) return
        watched = watched.filterNot { it.key == key }
        episodes = rest
        changed()
    }

    /** Returns true when the title is marked watched afterwards. */
    @Synchronized fun toggleWatched(card: TitleCard): Boolean {
        if (isWatched(card)) unmarkWatched(card) else markWatched(card)
        return isWatched(card)
    }

    /** True when the episode is marked, or the whole title is. */
    @Synchronized fun episodeWatched(card: TitleCard, season: Int, number: Int): Boolean =
        isWatched(card) || episodeMark(card, season, number) in episodes

    /**
     * Marks or clears one episode.
     *
     * Clearing an episode of a title that is marked watched as a whole removes that title mark. To
     * keep the other episodes watched, pass the episodes on screen as [siblings] (season, number):
     * they get their own marks first.
     * ponytail: episodes of seasons that are not on screen lose their watched look in that case.
     */
    @Synchronized fun setEpisodeWatched(
        card: TitleCard,
        season: Int,
        number: Int,
        value: Boolean,
        siblings: List<Pair<Int, Int>> = emptyList(),
    ) {
        val mark = episodeMark(card, season, number)
        val key = savedKey(card)
        if (value) {
            if (mark in episodes) return
            episodes = episodes + mark
        } else {
            val wholeTitle = watched.any { it.key == key }
            if (!wholeTitle && mark !in episodes) return
            if (wholeTitle) {
                watched = watched.filterNot { it.key == key }
                episodes = episodes + siblings.map { (s, n) -> episodeMark(card, s, n) }
            }
            episodes = episodes - mark
        }
        changed()
    }

    // ---------------------------------------------------------------- internals

    private fun episodeMark(card: TitleCard, season: Int, number: Int) = "${savedKey(card)}|$season|$number"

    private fun snapshot(card: TitleCard) = SavedTitle(
        source = card.source.key,
        kind = card.kind,
        id = card.id,
        name = card.name,
        poster = card.poster,
        backdrop = card.backdrop,
        year = card.year,
        slug = card.slug,
        at = clock(),
    )

    private fun changed() {
        prefs.edit()
            .putString(LIST, SavedCodec.encodeTitles(list))
            .putString(WATCHED, SavedCodec.encodeTitles(watched))
            .putString(EPISODES, SavedCodec.encodeEpisodes(episodes))
            .apply()
        _version.value = _version.value + 1
    }

    companion object {
        const val PREFS = "saved"
        private const val LIST = "list"
        private const val WATCHED = "watched"
        private const val EPISODES = "episodes"

        @Volatile private var shared: SavedTitles? = null

        /** The store of the running app, once [create] has run. Lets the shelves read it without a context. */
        val current: SavedTitles? get() = shared

        /** One store per process, so every screen sees the same marks. */
        fun create(context: Context): SavedTitles = shared ?: synchronized(this) {
            shared ?: SavedTitles(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).also { shared = it }
        }
    }
}
