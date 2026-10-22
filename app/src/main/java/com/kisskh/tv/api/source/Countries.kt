package com.kisskh.tv.api.source

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind

/** One entry of the Country filter. [code] is the ISO 3166-1 code TMDB expects. */
data class OriginCountry(val code: String, val label: String)

/**
 * The Country filter on the Movies and TV Shows grids of the sources that are not KissKH.
 * The list is KissKH's (in the site's order) plus India.
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

    /**
     * True where the grid can really filter by country: CineWave and CineJoy Movies and TV Shows
     * (TMDB `with_origin_country`). Not Reelix (its site has no such filter), not Anisuge or Anime
     * Nexus (all anime), not KissKH (it has its own screens), and not CineWave's anime grid (it is
     * chosen by a TMDB keyword, so most countries would come back almost empty).
     */
    fun supported(library: Library, kind: MediaKind): Boolean =
        (library == Library.CINEWAVE || library == Library.CINEJOY) && kind != MediaKind.ANIME
}
