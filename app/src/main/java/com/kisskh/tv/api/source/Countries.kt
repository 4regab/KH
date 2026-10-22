package com.kisskh.tv.api.source

/** One entry of the Country filter. [code] is the ISO 3166-1 code TMDB expects. */
data class OriginCountry(val code: String, val label: String)

/**
 * The Country filter list for the TMDB-backed grids (CineWave, CineJoy, Reelix Movies and TV Shows).
 * It is KissKH's list (in the site's order) plus India. Which grids offer it is decided by [SourceFilters].
 */
object OriginCountries {
    val all: List<OriginCountry> = listOf(
        OriginCountry("KR", "South Korea"),
        OriginCountry("CN", "China"),
        OriginCountry("JP", "Japan"),
        OriginCountry("TH", "Thailand"),
        OriginCountry("PH", "Philippines"),
        OriginCountry("HK", "Hong Kong"),
        OriginCountry("TW", "Taiwan"),
        OriginCountry("US", "United States"),
        OriginCountry("IN", "India"),
    )

    fun byCode(code: String?): OriginCountry? = all.firstOrNull { it.code.equals(code, ignoreCase = true) }
}
