package com.kisskh.tv.api.media

/** Where a screen's titles come from. Each value is one adapter behind the shared shelves. */
enum class Library(val label: String, val key: String) {
    KISSKH("KissKH", "kh"),
    CINEWAVE("CineWave", "cw"),
    CINEJOY("CineJoy", "cj"),
    REELIX("Reelix", "rx"),
    ANISUGE("Anisuge", "as"),
    NEXUS("Anime Nexus", "nx"),
}

/** Movie, series, or CineWave's anime list (anime is still a TMDB TV record). */
enum class MediaKind {
    MOVIE,
    TV,
    ANIME,
    ;

    /** TMDB path segment. Anime uses the TV endpoints. */
    fun api(): String = if (this == MOVIE) "movie" else "tv"

    val isMovie: Boolean get() = this == MOVIE
}
