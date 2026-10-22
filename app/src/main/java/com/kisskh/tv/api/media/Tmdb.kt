package com.kisskh.tv.api.media

import com.squareup.moshi.JsonReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.net.URLEncoder
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Catalog calls shared by CineWave and CineJoy.
 *
 * Both sites call `https://api.themoviedb.org/3` with the same read key baked into their public
 * bundles (CineWave's `en()`, CineJoy's `P()`). Nothing here is proxied through either site.
 * Poster art uses `image.tmdb.org`. A missing image stays null: the sites' placehold.co fallback
 * is not a real poster, so the app shows its own plain tile instead.
 */
object TmdbPaths {
    const val API = "https://api.themoviedb.org/3"
    const val IMAGE = "https://image.tmdb.org/t/p"
    const val KEY = "8476a7ab80ad76f0936744df0430e67c"

    fun trendingAll() = "/trending/all/week"
    fun trendingMovies() = "/trending/movie/day"
    fun trendingTv() = "/trending/tv/day"
    fun onTheAir(page: Int = 1) = "/tv/on_the_air?page=$page"
    fun search(query: String) = "/search/multi?query=${encode(query)}"

    // `images` with English or textless files gives the title logo for the detail page.
    fun movie(id: Int) =
        "/movie/$id?append_to_response=release_dates,credits,recommendations,external_ids,images&include_image_language=en,null"
    fun tv(id: Int) =
        "/tv/$id?append_to_response=content_ratings,credits,recommendations,external_ids,images&include_image_language=en,null"
    fun season(id: Int, number: Int) = "/tv/$id/season/$number"

    /** The three list orders a grid can ask TMDB for. */
    enum class Order { POPULAR, TOP_RATED, NEWEST }

    /** Fewest votes a title needs to enter a Top rated list, so one 10/10 vote cannot rank first. */
    const val TOP_RATED_MIN_VOTES = 200

    /** The picture sizes the app shows. TMDB's `original` is never used on screen. */
    const val POSTER = "w342"
    const val BACKDROP = "w1280"
    const val STILL = "w300"
    const val LOGO = "w500"
    const val PROFILE = "w185"

    /**
     * CineWave's anime row, including the page the site fixes at 1.
     * [genre] (a TMDB TV genre id) narrows it further: `with_genres=16,<id>` means Animation AND that genre.
     * [year] is the first-air year. [order] defaults to the site's own popularity order. Newest stops at [today]
     * so shows that have not started do not flood the list.
     */
    fun anime(
        page: Int = 1,
        genre: String? = null,
        year: Int? = null,
        order: Order = Order.POPULAR,
        today: String = todayUtc(),
    ): String {
        val genres = "16" + (digits(genre)?.let { ",$it" } ?: "")
        val sort = when (order) {
            Order.POPULAR -> "popularity.desc"
            Order.TOP_RATED -> "vote_average.desc&vote_count.gte=$TOP_RATED_MIN_VOTES"
            Order.NEWEST -> "first_air_date.desc&first_air_date.lte=$today&vote_count.gte=5"
        }
        return "/discover/tv?with_genres=$genres&with_keywords=210024&sort_by=$sort" +
            "&include_adult=false&without_genres=10749&certification_country=US" +
            "&certification.lte=TV-MA" + (validYear(year)?.let { "&first_air_date_year=$it" } ?: "") + "&page=$page"
    }

    /**
     * CineWave's discover helper. Latest is the release date descending, not after today, with at
     * least five votes. Anything else is popularity. [page] is added so a grid can continue.
     * [country] is an ISO 3166-1 code (KR, JP, ...) that keeps only titles made in that country.
     * It is left out when null or not two letters, so a bad value never narrows the list to nothing.
     *
     * [genre] is a TMDB genre id (movie and TV ids differ), [year] the release year, and [order] picks
     * Popular, Top rated (at least [TOP_RATED_MIN_VOTES] votes) or Newest (same as [latest]).
     * A bad genre or year is left out the same way as a bad country.
     */
    fun discover(
        kind: String,
        latest: Boolean,
        page: Int,
        today: String,
        country: String? = null,
        genre: String? = null,
        year: Int? = null,
        order: Order = Order.POPULAR,
    ): String {
        val field = if (kind == "movie") "primary_release_date" else "first_air_date"
        val base = when {
            latest || order == Order.NEWEST ->
                "/discover/$kind?sort_by=$field.desc&$field.lte=$today&vote_count.gte=5&include_null_first_air_dates=false"
            order == Order.TOP_RATED -> "/discover/$kind?sort_by=vote_average.desc&vote_count.gte=$TOP_RATED_MIN_VOTES"
            else -> "/discover/$kind?sort_by=popularity.desc"
        }
        val origin = country?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) }
        val yearField = if (kind == "movie") "primary_release_year" else "first_air_date_year"
        return base +
            (if (origin != null) "&with_origin_country=$origin" else "") +
            (digits(genre)?.let { "&with_genres=$it" } ?: "") +
            (validYear(year)?.let { "&$yearField=$it" } ?: "") +
            "&page=$page"
    }

    private fun digits(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    private fun validYear(year: Int?): Int? = year?.takeIf { it in 1870..2100 }
    fun withKey(path: String): String {
        val join = if ('?' in path) "&" else "?"
        return "$API$path${join}api_key=$KEY"
    }

    /** A picture URL at one of the sizes above ([POSTER], [BACKDROP], [STILL], [LOGO], [PROFILE]). Null when TMDB has no file. */
    fun image(path: String?, size: String): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val file = if (path.startsWith("/")) path else "/$path"
        return "$IMAGE/$size$file"
    }

    fun todayUtc(calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun encode(query: String): String =
        URLEncoder.encode(query, "UTF-8").replace("+", "%20")
}

data class TmdbTitle(
    val id: Int,
    val mediaType: String,
    val name: String,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val vote: Double?,
    val year: String?,
    /** The full release date (release_date for movies, first_air_date for TV), yyyy-MM-dd. Null when TMDB has none. */
    val releaseDate: String? = null,
) {
    /** 16:9 art. Backdrop first, poster when that is missing. */
    val artwork: String? get() = backdrop ?: poster

    /** Full-screen art. Backdrops are already 1280 px wide, so a 1080p screen does not show a blurry upscale. */
    val hero: String? get() = backdrop ?: poster

    fun voteText(): String? = vote?.takeIf { it > 0 }?.let { java.lang.String.format(java.util.Locale.US, "%.1f", it) }
}

data class TmdbSeasonInfo(val number: Int, val name: String, val episodeCount: Int)

data class TmdbEpisode(
    val number: Int,
    val name: String,
    val overview: String,
    val still: String?,
    val runtime: Int?,
    /** yyyy-MM-dd, or null when TMDB has no date. */
    val airDate: String? = null,
) {
    /**
     * False for an episode whose air date is after [today] (yyyy-MM-dd): no server has it yet.
     * ponytail: a missing date counts as aired, so a gap in TMDB's data never blocks a real episode.
     */
    fun aired(today: String = TmdbPaths.todayUtc()): Boolean = airDate == null || airDate <= today
}

data class TmdbTitleDetails(
    val title: TmdbTitle,
    val seasons: List<TmdbSeasonInfo>,
    val recommendations: List<TmdbTitle>,
    val certification: String?,
    val genres: List<String> = emptyList(),
    /** Movie length in minutes. */
    val runtime: Int? = null,
    /** The first billed cast, at most four names. */
    val cast: List<String> = emptyList(),
    /** The English (or textless) title logo, transparent PNG. */
    val logo: String? = null,
    val tagline: String? = null,
    /** The first [CAST_LIMIT] billed cast with their role and a w185 photo. */
    val castMembers: List<TmdbCastMember> = emptyList(),
    /** A TV show's typical episode length in minutes (`episode_run_time`, else the last aired episode). */
    val episodeRuntime: Int? = null,
) {
    companion object {
        const val CAST_LIMIT = 12
    }
}

data class TmdbCastMember(val name: String, val character: String?, val photo: String?)

data class TmdbPage(val items: List<TmdbTitle>, val page: Int, val totalPages: Int)

object TmdbJson {
    fun page(json: String, fallbackType: String?): TmdbPage =
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            var page = 1
            var total = 1
            var items = emptyList<TmdbTitle>()
            reader.obj {
                when (reader.nextName()) {
                    "page" -> page = reader.intOrNull() ?: page
                    "total_pages" -> total = reader.intOrNull() ?: total
                    "results" -> items = reader.titles(fallbackType)
                    else -> reader.skipValue()
                }
            }
            TmdbPage(items, page, total.coerceAtLeast(1))
        }

    fun details(json: String, fallbackType: String): TmdbTitleDetails =
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            var titleName: String? = null
            var showName: String? = null
            var id: Int? = null
            var overview = ""
            var poster: String? = null
            var backdrop: String? = null
            var vote: Double? = null
            var release: String? = null
            var air: String? = null
            var media: String? = null
            var seasons = emptyList<TmdbSeasonInfo>()
            var recommendations = emptyList<TmdbTitle>()
            var certification: String? = null
            var genres = emptyList<String>()
            var runtime: Int? = null
            var cast = emptyList<TmdbCastMember>()
            var logo: String? = null
            var tagline: String? = null
            var episodeRuntime: Int? = null
            var lastEpisodeRuntime: Int? = null
            reader.obj {
                when (val name = reader.nextName()) {
                    "id" -> id = reader.intOrNull()
                    "title" -> titleName = reader.stringOrNull()
                    "name" -> showName = reader.stringOrNull()
                    "overview" -> overview = reader.stringOrNull().orEmpty()
                    "poster_path" -> poster = reader.stringOrNull()
                    "backdrop_path" -> backdrop = reader.stringOrNull()
                    "vote_average" -> vote = reader.doubleOrNull()
                    "release_date" -> release = reader.stringOrNull()
                    "first_air_date" -> air = reader.stringOrNull()
                    "media_type" -> media = reader.stringOrNull()
                    "seasons" -> seasons = reader.seasons()
                    "recommendations" -> recommendations = reader.nestedResults(null)
                    "release_dates" -> certification = reader.usReleaseCertification() ?: certification
                    "content_ratings" -> certification = reader.usContentRating() ?: certification
                    "genres" -> genres = reader.names("name")
                    "runtime" -> runtime = reader.intOrNull()?.takeIf { it > 0 }
                    "credits" -> cast = reader.castMembers()
                    "images" -> logo = reader.firstLogo()
                    "tagline" -> tagline = reader.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                    "episode_run_time" -> episodeRuntime = reader.firstInt()
                    "last_episode_to_air" -> lastEpisodeRuntime = reader.runtimeOf()
                    else -> {
                        if (name == "status_message") throw IllegalStateException(reader.nextString())
                        reader.skipValue()
                    }
                }
            }
            val type = media ?: fallbackType
            val tid = id ?: throw IllegalStateException("TMDB title has no id.")
            val date = if (type == "tv") air ?: release else release ?: air
            val shown = (if (type == "tv") showName ?: titleName else titleName ?: showName)
                ?.trim().orEmpty().ifEmpty { "Untitled" }
            TmdbTitleDetails(
                title = TmdbTitle(
                    id = tid,
                    mediaType = type,
                    name = shown,
                    overview = overview,
                    poster = TmdbPaths.image(poster, TmdbPaths.POSTER),
                    backdrop = TmdbPaths.image(backdrop, TmdbPaths.BACKDROP),
                    vote = vote,
                    year = yearOf(date),
                    releaseDate = fullDate(date),
                ),
                seasons = seasons,
                recommendations = recommendations,
                certification = certification?.takeIf { it.isNotBlank() },
                genres = genres,
                runtime = runtime,
                cast = cast.take(4).map { it.name },
                logo = TmdbPaths.image(logo, TmdbPaths.LOGO),
                tagline = tagline,
                castMembers = cast,
                episodeRuntime = episodeRuntime ?: lastEpisodeRuntime,
            )
        }

    /** The first number of an int array such as `episode_run_time`, or null when it is empty. */
    private fun JsonReader.firstInt(): Int? {
        if (peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            return null
        }
        var first: Int? = null
        beginArray()
        while (hasNext()) {
            val n = intOrNull()
            if (first == null) first = n?.takeIf { it > 0 }
        }
        endArray()
        return first
    }

    /** `runtime` of an episode object, for example `last_episode_to_air`. */
    private fun JsonReader.runtimeOf(): Int? {
        if (peek() != JsonReader.Token.BEGIN_OBJECT) {
            skipValue()
            return null
        }
        var runtime: Int? = null
        obj { if (nextName() == "runtime") runtime = intOrNull()?.takeIf { it > 0 } else skipValue() }
        return runtime
    }

    /** The [field] string of each object in an array, for example the genre names. */
    private fun JsonReader.names(field: String): List<String> {
        if (peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            return emptyList()
        }
        val out = ArrayList<String>()
        beginArray()
        while (hasNext()) {
            if (peek() != JsonReader.Token.BEGIN_OBJECT) {
                skipValue()
                continue
            }
            var value: String? = null
            obj { if (nextName() == field) value = stringOrNull() else skipValue() }
            value?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        }
        endArray()
        return out
    }

    /** The first [TmdbTitleDetails.CAST_LIMIT] billed cast of a `credits` object. TMDB lists them in billing order. */
    private fun JsonReader.castMembers(): List<TmdbCastMember> {
        if (peek() != JsonReader.Token.BEGIN_OBJECT) {
            skipValue()
            return emptyList()
        }
        val out = ArrayList<TmdbCastMember>()
        obj {
            if (nextName() == "cast" && peek() == JsonReader.Token.BEGIN_ARRAY) {
                beginArray()
                while (hasNext()) {
                    if (peek() != JsonReader.Token.BEGIN_OBJECT) {
                        skipValue()
                        continue
                    }
                    var name: String? = null
                    var character: String? = null
                    var photo: String? = null
                    obj {
                        when (nextName()) {
                            "name" -> name = stringOrNull()?.trim()
                            "character" -> character = stringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                            "profile_path" -> photo = stringOrNull()
                            else -> skipValue()
                        }
                    }
                    val shown = name?.takeIf { it.isNotEmpty() }
                    if (shown != null && out.size < TmdbTitleDetails.CAST_LIMIT) {
                        out += TmdbCastMember(shown, character, TmdbPaths.image(photo, TmdbPaths.PROFILE))
                    }
                }
                endArray()
            } else {
                skipValue()
            }
        }
        return out
    }

    /** TMDB sorts logos by vote, so the first English or textless one is the best. */
    private fun JsonReader.firstLogo(): String? {
        if (peek() != JsonReader.Token.BEGIN_OBJECT) {
            skipValue()
            return null
        }
        var english: String? = null
        var textless: String? = null
        obj {
            if (nextName() == "logos" && peek() == JsonReader.Token.BEGIN_ARRAY) {
                beginArray()
                while (hasNext()) {
                    if (peek() != JsonReader.Token.BEGIN_OBJECT) {
                        skipValue()
                        continue
                    }
                    var path: String? = null
                    var lang: String? = null
                    obj {
                        when (nextName()) {
                            "file_path" -> path = stringOrNull()
                            "iso_639_1" -> lang = stringOrNull()
                            else -> skipValue()
                        }
                    }
                    if (path != null && lang == "en" && english == null) english = path
                    if (path != null && lang == null && textless == null) textless = path
                }
                endArray()
            } else {
                skipValue()
            }
        }
        return english ?: textless
    }

    fun episodes(json: String): List<TmdbEpisode> =
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            var episodes = emptyList<TmdbEpisode>()
            reader.obj {
                when (reader.nextName()) {
                    "episodes" -> episodes = reader.episodeArray()
                    else -> reader.skipValue()
                }
            }
            episodes.sortedBy { it.number }
        }

    private fun JsonReader.titles(fallbackType: String?): List<TmdbTitle> {
        if (peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            return emptyList()
        }
        val out = ArrayList<TmdbTitle>()
        beginArray()
        while (hasNext()) {
            if (peek() != JsonReader.Token.BEGIN_OBJECT) {
                skipValue()
                continue
            }
            readTitle(fallbackType)?.let { out += it }
        }
        endArray()
        return out
    }

    private fun JsonReader.readTitle(fallbackType: String?): TmdbTitle? {
        var id: Int? = null
        var title: String? = null
        var name: String? = null
        var overview = ""
        var poster: String? = null
        var backdrop: String? = null
        var vote: Double? = null
        var release: String? = null
        var air: String? = null
        var media: String? = null
        obj {
            when (nextName()) {
                "id" -> id = intOrNull()
                "title" -> title = stringOrNull()
                "name" -> name = stringOrNull()
                "overview" -> overview = stringOrNull().orEmpty()
                "poster_path" -> poster = stringOrNull()
                "backdrop_path" -> backdrop = stringOrNull()
                "vote_average" -> vote = doubleOrNull()
                "release_date" -> release = stringOrNull()
                "first_air_date" -> air = stringOrNull()
                "media_type" -> media = stringOrNull()
                else -> skipValue()
            }
        }
        val type = media ?: fallbackType
        val tid = id ?: return null
        if (type != "movie" && type != "tv") return null
        val date = if (type == "tv") air ?: release else release ?: air
        val shown = (if (type == "tv") name ?: title else title ?: name)?.trim().orEmpty().ifEmpty { "Untitled" }
        return TmdbTitle(
            id = tid,
            mediaType = type,
            name = shown,
            overview = overview,
            poster = TmdbPaths.image(poster, TmdbPaths.POSTER),
            backdrop = TmdbPaths.image(backdrop, TmdbPaths.BACKDROP),
            vote = vote,
            year = yearOf(date),
            releaseDate = fullDate(date),
        )
    }

    private fun JsonReader.seasons(): List<TmdbSeasonInfo> {
        if (peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            return emptyList()
        }
        val out = ArrayList<TmdbSeasonInfo>()
        beginArray()
        while (hasNext()) {
            if (peek() != JsonReader.Token.BEGIN_OBJECT) {
                skipValue()
                continue
            }
            var number: Int? = null
            var name = ""
            var count = 0
            obj {
                when (nextName()) {
                    "season_number" -> number = intOrNull()
                    "name" -> name = stringOrNull().orEmpty()
                    "episode_count" -> count = intOrNull() ?: 0
                    else -> skipValue()
                }
            }
            val n = number
            if (n != null && count > 0) {
                val label = name.ifBlank { if (n == 0) "Specials" else "Season $n" }
                out += TmdbSeasonInfo(n, label, count)
            }
        }
        endArray()
        return out.sortedBy { it.number }
    }

    private fun JsonReader.episodeArray(): List<TmdbEpisode> {
        if (peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            return emptyList()
        }
        val out = ArrayList<TmdbEpisode>()
        beginArray()
        while (hasNext()) {
            if (peek() != JsonReader.Token.BEGIN_OBJECT) {
                skipValue()
                continue
            }
            var number: Int? = null
            var name = ""
            var overview = ""
            var still: String? = null
            var runtime: Int? = null
            var airDate: String? = null
            obj {
                when (nextName()) {
                    "air_date" -> airDate = stringOrNull()?.takeIf { it.length == 10 }
                    "episode_number" -> number = intOrNull()
                    "name" -> name = stringOrNull().orEmpty()
                    "overview" -> overview = stringOrNull().orEmpty()
                    "still_path" -> still = stringOrNull()
                    "runtime" -> runtime = intOrNull()
                    else -> skipValue()
                }
            }
            val n = number ?: continue
            out += TmdbEpisode(
                number = n,
                name = name.ifBlank { "Episode $n" },
                overview = overview,
                still = TmdbPaths.image(still, TmdbPaths.STILL),
                runtime = runtime?.takeIf { it > 0 },
                airDate = airDate,
            )
        }
        endArray()
        return out
    }

    /** First certification on the US release, which is the entry CineWave reads. */
    private fun JsonReader.usReleaseCertification(): String? {
        if (peek() != JsonReader.Token.BEGIN_OBJECT) {
            skipValue()
            return null
        }
        var cert: String? = null
        obj {
            if (nextName() == "results") {
                beginArray()
                while (hasNext()) {
                    var iso: String? = null
                    var first: String? = null
                    obj {
                        when (nextName()) {
                            "iso_3166_1" -> iso = stringOrNull()
                            "release_dates" -> first = firstCertification()
                            else -> skipValue()
                        }
                    }
                    if (iso == "US" && cert == null) cert = first
                }
                endArray()
            } else {
                skipValue()
            }
        }
        return cert
    }

    private fun JsonReader.firstCertification(): String? {
        var first: String? = null
        beginArray()
        while (hasNext()) {
            var cert: String? = null
            obj {
                when (nextName()) {
                    "certification" -> cert = stringOrNull()
                    else -> skipValue()
                }
            }
            if (first == null) first = cert
        }
        endArray()
        return first
    }

    private fun JsonReader.usContentRating(): String? {
        if (peek() != JsonReader.Token.BEGIN_OBJECT) {
            skipValue()
            return null
        }
        var rating: String? = null
        obj {
            if (nextName() == "results") {
                beginArray()
                while (hasNext()) {
                    var iso: String? = null
                    var value: String? = null
                    obj {
                        when (nextName()) {
                            "iso_3166_1" -> iso = stringOrNull()
                            "rating" -> value = stringOrNull()
                            else -> skipValue()
                        }
                    }
                    if (iso == "US" && rating == null) rating = value
                }
                endArray()
            } else {
                skipValue()
            }
        }
        return rating
    }

    private fun JsonReader.nestedResults(fallbackType: String?): List<TmdbTitle> {
        if (peek() != JsonReader.Token.BEGIN_OBJECT) {
            skipValue()
            return emptyList()
        }
        var items = emptyList<TmdbTitle>()
        obj {
            when (nextName()) {
                "results" -> items = titles(fallbackType)
                else -> skipValue()
            }
        }
        return items
    }

    private fun fullDate(date: String?): String? = date?.trim()?.takeIf { it.length == 10 }

    private fun yearOf(date: String?): String? =
        date?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }

    private inline fun JsonReader.obj(body: JsonReader.() -> Unit) {
        beginObject()
        while (hasNext()) body()
        endObject()
    }

    private fun JsonReader.stringOrNull(): String? = when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any?>()
            null
        }
        JsonReader.Token.STRING -> nextString()
        else -> {
            skipValue()
            null
        }
    }

    private fun JsonReader.intOrNull(): Int? = when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any?>()
            null
        }
        JsonReader.Token.NUMBER -> nextDouble().toInt()
        else -> {
            skipValue()
            null
        }
    }

    private fun JsonReader.doubleOrNull(): Double? = when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any?>()
            null
        }
        JsonReader.Token.NUMBER -> nextDouble()
        else -> {
            skipValue()
            null
        }
    }
}

class TmdbClient(private val http: OkHttpClient = defaultHttp()) {
    fun trendingAll() = page(TmdbPaths.trendingAll(), null)
    fun trendingMovies() = page(TmdbPaths.trendingMovies(), "movie")
    fun trendingTv() = page(TmdbPaths.trendingTv(), "tv")
    fun onTheAir(page: Int = 1) = page(TmdbPaths.onTheAir(page), "tv")
    fun search(query: String) = page(TmdbPaths.search(query), null).items
    fun anime(page: Int, genre: String? = null, year: Int? = null, order: TmdbPaths.Order = TmdbPaths.Order.POPULAR) =
        page(TmdbPaths.anime(page, genre, year, order), "tv")
    fun discover(
        kind: String,
        latest: Boolean,
        page: Int,
        today: String,
        country: String? = null,
        genre: String? = null,
        year: Int? = null,
        order: TmdbPaths.Order = TmdbPaths.Order.POPULAR,
    ) = page(TmdbPaths.discover(kind, latest, page, today, country, genre, year, order), kind)

    fun movie(id: Int) = TmdbJson.details(get(TmdbPaths.movie(id)), "movie")
    fun tv(id: Int) = TmdbJson.details(get(TmdbPaths.tv(id)), "tv")
    fun season(id: Int, number: Int) = TmdbJson.episodes(get(TmdbPaths.season(id, number)))

    private fun page(path: String, fallback: String?) = TmdbJson.page(get(path), fallback)

    private fun get(path: String): String {
        val request = Request.Builder().url(TmdbPaths.withKey(path)).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("TMDB returned ${response.code}.")
            }
            return body
        }
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
