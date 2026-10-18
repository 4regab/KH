package com.kisskh.tv.api

/*
 * Catalog filters for DramaList/List.
 *
 * ONE place for every id, label and API name. Source: API.md "Verified catalog mapping", which comes
 * from the kisskh Explore page chunk 780.f011ddf6618b7a1e.js (`chips` array), checked on 2026-10-18 by
 * sampling DramaList/Drama/{id} for each filter. Do not copy these numbers anywhere else.
 */

/** `type` filter. Hollywood is US-only and mixes series and movies. Never infer type from episode count. */
enum class ContentType(val id: Int, val label: String) {
    ALL(0, "All"),
    SERIES(1, "TV Series"),
    MOVIE(2, "Movies"),
    ANIME(3, "Anime"),
    HOLLYWOOD(4, "Hollywood");

    companion object {
        fun fromId(id: Int): ContentType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * `country` filter. Declared in the site's own order, so `Country.entries` is the order to show.
 * "All regions" is not a value: use `null` (the API takes 0). Id 9 returns a few rows but the site
 * does not offer it, so it is left out on purpose.
 *
 * [label] is what the user sees. [apiName] is the string the API puts in `Drama.country`.
 * (The site's own chips say "Chinese", "Japanese" and "Philippine" for ids 1, 3 and 8.)
 */
enum class Country(val id: Int, val label: String, val apiName: String) {
    SOUTH_KOREA(2, "South Korea", "South Korea"),
    CHINA(1, "China", "China"),
    UNITED_STATES(6, "United States", "United States"),
    THAILAND(5, "Thailand", "Thailand"),
    PHILIPPINES(8, "Philippines", "Philippines"),
    JAPAN(3, "Japan", "Japan"),
    HONG_KONG(4, "Hong Kong", "Hong Kong"),
    TAIWAN(7, "Taiwan", "Taiwan");

    companion object {
        fun fromId(id: Int): Country? = entries.firstOrNull { it.id == id }

        /** Maps `Drama.country` from the detail endpoint back to a filter. Null if unknown. */
        fun fromApiName(name: String?): Country? = entries.firstOrNull { it.apiName.equals(name?.trim(), ignoreCase = true) }
    }
}

/** `status` filter. Only meaningful for series. */
enum class Status(val id: Int, val label: String) {
    ALL(0, "All"),
    ONGOING(1, "Ongoing"),
    COMPLETED(2, "Completed"),
    UPCOMING(3, "Upcoming");

    companion object {
        fun fromId(id: Int): Status? = entries.firstOrNull { it.id == id }
    }
}

/** `order` filter. The site has no rating sort. */
enum class SortOrder(val id: Int, val label: String) {
    POPULAR(1, "Popular"),
    LAST_UPDATE(2, "Last Update"),
    RELEASE_DATE(3, "Release Date");

    companion object {
        fun fromId(id: Int): SortOrder? = entries.firstOrNull { it.id == id }
    }
}

/** One full set of filters. Two equal queries always mean the same list of results. */
data class CatalogQuery(
    val type: ContentType = ContentType.ALL,
    val country: Country? = null,
    val status: Status = Status.ALL,
    val order: SortOrder = SortOrder.POPULAR,
) {
    /** The API value for `country` (0 means all regions). */
    val countryId: Int get() = country?.id ?: 0
}

/** Where catalog pages come from. [Repository] is the real one; tests use a fake. */
interface CatalogSource {
    suspend fun list(query: CatalogQuery, page: Int, pageSize: Int): ListPage

    /** Total number of matches, from a one-row request. Use it to hide empty tabs. */
    suspend fun count(query: CatalogQuery): Int = list(query, page = 1, pageSize = 1).totalCount
}

/**
 * Tabs to show on a country page, in order: All, TV Series, Movies. Empty tabs are left out.
 * If the "All" count fails, this throws so the caller can show a retry. If only a series or movie
 * count fails, that tab is kept: a failed check is not proof that the tab is empty.
 */
suspend fun CatalogSource.availableTabs(country: Country): List<ContentType> {
    if (count(CatalogQuery(ContentType.ALL, country)) == 0) return emptyList()
    return buildList {
        add(ContentType.ALL)
        for (type in listOf(ContentType.SERIES, ContentType.MOVIE)) {
            val n = suspendCatching { count(CatalogQuery(type, country)) }.getOrNull()
            if (n == null || n > 0) add(type)
        }
    }
}
