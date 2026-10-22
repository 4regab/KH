package com.kisskh.tv.api.source

import com.kisskh.tv.api.CatalogQuery
import com.kisskh.tv.api.Country
import com.kisskh.tv.api.SortOrder
import com.kisskh.tv.api.Status
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbPaths
import java.util.Calendar
import java.util.TimeZone

/** The orders a grid can offer. A source lists only the ones it really has (see [SourceFilters]). */
enum class BrowseSort(val label: String) {
    POPULAR("Popular"),
    TOP_RATED("Top rated"),
    NEWEST("Newest"),

    /** KissKH "Last Update", Anisuge "Latest updated", Anime Nexus "Updated". */
    UPDATED("Recently updated"),
}

/**
 * One choice on a grid. Each field is null when it is not set. [country] is an ISO 3166-1 code
 * ("KR"); [genre] and [status] are the ids of [FilterCapabilities.genres] / [FilterCapabilities.statuses]
 * for that source and grid; [year] is a release year.
 *
 * Two equal values always mean the same list. Run [FilterCapabilities.normalize] before using one, so a
 * value the grid cannot filter on never splits its state.
 */
data class BrowseFilters(
    val country: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val sort: BrowseSort = BrowseSort.POPULAR,
    val status: String? = null,
) {
    /** True when anything other than the order is set. */
    val narrowed: Boolean get() = country != null || genre != null || year != null || status != null

    /** A short stable text for state maps and tests. "all" when nothing is set. */
    fun key(): String = listOfNotNull(
        country?.uppercase()?.let { "c=$it" },
        genre?.let { "g=$it" },
        year?.let { "y=$it" },
        status?.let { "st=$it" },
        sort.takeIf { it != BrowseSort.POPULAR }?.let { "s=${it.name}" },
    ).joinToString("&").ifEmpty { "all" }
}

/** One entry of a filter list: [id] goes to the source, [label] to the screen. */
data class FilterOption(val id: String, val label: String)

/**
 * What one grid can really filter on. A list is empty (or [years] null) when the source cannot do it,
 * and the screen should hide that filter. [sorts] always has at least one entry; the first is the
 * grid's default order.
 */
data class FilterCapabilities(
    val countries: List<FilterOption> = emptyList(),
    val genres: List<FilterOption> = emptyList(),
    val years: IntRange? = null,
    val sorts: List<BrowseSort> = listOf(BrowseSort.POPULAR),
    val statuses: List<FilterOption> = emptyList(),
) {
    val defaultSort: BrowseSort get() = sorts.first()

    /** True when at least one filter other than the order exists. */
    val any: Boolean get() = countries.isNotEmpty() || genres.isNotEmpty() || years != null || statuses.isNotEmpty()

    /** True when [filters] (already normalized) asks for the grid's plain list. */
    fun isDefault(filters: BrowseFilters): Boolean = !filters.narrowed && filters.sort == defaultSort

    /** Drops every value this grid cannot filter on and puts the order back to the default if it is not offered. */
    fun normalize(filters: BrowseFilters): BrowseFilters = BrowseFilters(
        country = filters.country?.trim()?.uppercase()?.takeIf { code -> countries.any { it.id == code } },
        genre = filters.genre?.takeIf { id -> genres.any { it.id == id } },
        year = filters.year?.takeIf { years != null && it in years },
        sort = filters.sort.takeIf { it in sorts } ?: defaultSort,
        status = filters.status?.takeIf { id -> statuses.any { it.id == id } },
    )
}

/**
 * The filters each grid offers. A filter is listed only when the site (or TMDB behind it) really
 * narrows the list with it; each was checked with a live request. What is missing, and why:
 *
 * - CineWave and CineJoy, Movies and TV Shows: TMDB discover. Country (`with_origin_country`),
 *   genre (`with_genres`; movie and TV ids differ), year (`primary_release_year` / `first_air_date_year`),
 *   and the orders Popular, Top rated (at least 200 votes) and Newest (not after today). No status: TMDB's
 *   `with_status` exists for TV only and the sites do not use it.
 * - CineWave and CineJoy, Anime: the same TMDB discover with CineWave's anime keyword. Genre, year and
 *   the three orders are added to that query. No country: the anime list is picked by a keyword, so
 *   most countries would come back almost empty.
 * - Reelix, Movies / TV Shows / Anime: with nothing set, the site's own list. With anything set, TMDB
 *   discover as above (Reelix ids are TMDB ids). The site itself has `?sort=` and `?genre=` but no
 *   country or year, so TMDB is the one path that gives all four. No status.
 * - KissKH: `DramaList/List` takes type, country, status and order only. Status is for TV series (on movies
 *   it barely moves the total, 1569 to 1566). `genre` and `year` are ignored by the API (the total does not change), so
 *   they are not offered. "Top rated" is missing: the site has no rating order.
 * - Anisuge: `/filter` takes genre, year, status and sort. Country is ignored by the site (all anime).
 *   Adult genres (Hentai, Erotica) are left out. The site's rating, season, type and audio boxes also
 *   work but are not part of [BrowseFilters].
 * - Anime Nexus: `api/anime/shows` takes genres, status, type and sortBy. There is no year parameter
 *   (`year`, `releaseYear` and date ranges all return the full list), no country, and no popularity
 *   order (`popular` is a fixed top 10).
 */
object SourceFilters {
    /** The year list stops here at the top; the earliest year differs per source. */
    private fun thisYear(): Int = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)

    fun capabilities(library: Library, kind: MediaKind, thisYear: Int = thisYear()): FilterCapabilities = when (library) {
        Library.CINEWAVE, Library.CINEJOY, Library.REELIX -> tmdbCapabilities(kind, thisYear)
        Library.KISSKH -> kissCapabilities(kind)
        Library.ANISUGE -> FilterCapabilities(
            genres = ANISUGE_GENRES,
            years = 1980..thisYear,
            sorts = listOf(BrowseSort.POPULAR, BrowseSort.TOP_RATED, BrowseSort.NEWEST, BrowseSort.UPDATED),
            statuses = ANISUGE_STATUSES,
        )
        Library.NEXUS -> FilterCapabilities(
            genres = NEXUS_GENRES,
            sorts = listOf(BrowseSort.NEWEST, BrowseSort.UPDATED),
            statuses = NEXUS_STATUSES,
        )
    }

    fun normalize(library: Library, kind: MediaKind, filters: BrowseFilters, thisYear: Int = thisYear()): BrowseFilters =
        capabilities(library, kind, thisYear).normalize(filters)

    private fun tmdbCapabilities(kind: MediaKind, thisYear: Int) = FilterCapabilities(
        countries = if (kind == MediaKind.ANIME) emptyList() else OriginCountries.all.map { FilterOption(it.code, it.label) },
        genres = when (kind) {
            MediaKind.MOVIE -> TMDB_MOVIE_GENRES
            MediaKind.TV -> TMDB_TV_GENRES
            MediaKind.ANIME -> TMDB_ANIME_GENRES
        },
        years = 1950..thisYear,
        sorts = listOf(BrowseSort.POPULAR, BrowseSort.TOP_RATED, BrowseSort.NEWEST),
    )

    private fun kissCapabilities(kind: MediaKind) = FilterCapabilities(
        countries = KISS_COUNTRIES.map { FilterOption(it.second, it.first.label) },
        sorts = listOf(BrowseSort.POPULAR, BrowseSort.UPDATED, BrowseSort.NEWEST),
        statuses = if (kind == MediaKind.TV) KISS_STATUSES else emptyList(),
    )

    /** The KissKH list query for a grid: the same call the Countries tab makes. */
    fun kissQuery(kind: MediaKind, filters: BrowseFilters): CatalogQuery {
        val f = normalize(Library.KISSKH, kind, filters)
        return CatalogQuery(
            type = kind.toContent(),
            country = KISS_COUNTRIES.firstOrNull { it.second == f.country }?.first,
            status = Status.entries.firstOrNull { it.name.equals(f.status, ignoreCase = true) } ?: Status.ALL,
            order = when (f.sort) {
                BrowseSort.UPDATED -> SortOrder.LAST_UPDATE
                BrowseSort.NEWEST -> SortOrder.RELEASE_DATE
                else -> SortOrder.POPULAR
            },
        )
    }

    /** The TMDB order behind a [BrowseSort]. [BrowseSort.UPDATED] has no TMDB twin and falls back to popular. */
    fun tmdbOrder(sort: BrowseSort): TmdbPaths.Order = when (sort) {
        BrowseSort.TOP_RATED -> TmdbPaths.Order.TOP_RATED
        BrowseSort.NEWEST -> TmdbPaths.Order.NEWEST
        else -> TmdbPaths.Order.POPULAR
    }

    /** KissKH countries in the site's order, with the ISO code the shared [OriginCountries] list uses. */
    private val KISS_COUNTRIES: List<Pair<Country, String>> = listOf(
        Country.SOUTH_KOREA to "KR",
        Country.CHINA to "CN",
        Country.UNITED_STATES to "US",
        Country.THAILAND to "TH",
        Country.PHILIPPINES to "PH",
        Country.JAPAN to "JP",
        Country.HONG_KONG to "HK",
        Country.TAIWAN to "TW",
    )

    private val KISS_STATUSES = listOf(
        FilterOption(Status.ONGOING.name, Status.ONGOING.label),
        FilterOption(Status.COMPLETED.name, Status.COMPLETED.label),
        FilterOption(Status.UPCOMING.name, Status.UPCOMING.label),
    )

    // TMDB's official genre lists (GET /genre/movie/list and /genre/tv/list). The ids differ between the two.
    private val TMDB_MOVIE_GENRES = listOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction", 53 to "Thriller", 10752 to "War", 37 to "Western",
    ).map { FilterOption(it.first.toString(), it.second) }

    private val TMDB_TV_GENRES = listOf(
        10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 10762 to "Kids", 9648 to "Mystery", 10764 to "Reality",
        10765 to "Sci-Fi & Fantasy", 10768 to "War & Politics", 37 to "Western",
    ).map { FilterOption(it.first.toString(), it.second) }

    // The anime query already says Animation (16), so only the TV genres that fit anime are offered.
    private val TMDB_ANIME_GENRES = listOf(
        10759 to "Action & Adventure", 35 to "Comedy", 80 to "Crime", 18 to "Drama", 10751 to "Family",
        10762 to "Kids", 9648 to "Mystery", 10765 to "Sci-Fi & Fantasy", 10768 to "War & Politics",
    ).map { FilterOption(it.first.toString(), it.second) }

    // Anisuge's /filter genre ids (from the site's own filter form). Adult genres are left out on purpose.
    private val ANISUGE_GENRES = listOf(
        1 to "Action", 2 to "Adventure", 8 to "Comedy", 119 to "Demons", 62 to "Drama", 214 to "Ecchi",
        3 to "Fantasy", 180 to "Game", 215 to "Harem", 70 to "Historical", 222 to "Horror", 74 to "Isekai",
        46 to "Kids", 203 to "Magic", 114 to "Martial Arts", 123 to "Mecha", 125 to "Military", 242 to "Music",
        57 to "Mystery", 162 to "Parody", 73 to "Psychological", 28 to "Romance", 14 to "School", 12 to "Sci-Fi",
        50 to "Seinen", 252 to "Shoujo", 15 to "Shounen", 35 to "Slice of Life", 124 to "Space", 29 to "Sports",
        16 to "Super Power", 9 to "Supernatural", 54 to "Thriller", 58 to "Vampire",
    ).map { FilterOption(it.first.toString(), it.second) }

    private val ANISUGE_STATUSES = listOf(
        FilterOption("currently-airing", "Airing"),
        FilterOption("finished-airing", "Finished"),
    )

    // Anime Nexus genre ids are database UUIDs, read from api/anime/shows?includes[]=genres.
    // ponytail: a fixed list, so a genre the site adds later is missing until this list is updated.
    private val NEXUS_GENRES = listOf(
        "998fb863-e6c5-4527-a87a-94c8c91d9ee1" to "Action",
        "998fb863-f39d-4d94-83cc-35e37a0494ea" to "Adventure",
        "99914531-d119-4cbd-a190-214b5fdbc54f" to "Avant Garde",
        "999142e6-9684-4289-9394-e6f0897d102c" to "Award Winning",
        "998fb864-0207-4389-819d-bc5bec9aeb14" to "Comedy",
        "998fb864-173b-4509-ac1b-0d5ecec6814c" to "Drama",
        "998fb864-1e26-4a65-9fed-b788a455f2d5" to "Ecchi",
        "998fb864-2391-44b7-8d22-8b0ab840dede" to "Fantasy",
        "99914530-f739-417b-9ecf-9876e537cf87" to "Gourmet",
        "998fb864-455a-445c-9695-85e41db3df77" to "Horror",
        "998fb864-7e3c-48ef-8218-8920e4d85e3f" to "Mystery",
        "998fb864-9317-41b8-9dd8-a889bb9d2783" to "Romance",
        "998fb864-a67f-4b30-9d6c-3fd6c0118483" to "Sci-Fi",
        "998fb864-c7b4-4551-9f8d-2ab70bc97020" to "Slice of Life",
        "998fb864-d1fb-422f-b24c-d8b7d47eb7f7" to "Sports",
        "998fb864-dec0-43c3-a026-ceb79d31d2f9" to "Supernatural",
        "9991452d-a96d-4ae7-92a6-9ee3854de473" to "Suspense",
    ).map { FilterOption(it.first, it.second) }

    private val NEXUS_STATUSES = listOf(
        FilterOption("Currently Airing", "Airing"),
        FilterOption("Finished Airing", "Finished"),
    )
}
