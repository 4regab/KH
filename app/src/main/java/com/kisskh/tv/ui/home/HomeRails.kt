package com.kisskh.tv.ui.home

/**
 * Home row titles, in the order the screen shows them.
 * Continue Watching is first, and only when the viewer actually has something to resume.
 * The other eight come from real feeds. Together that is at most nine rows.
 */
object HomeRails {
    const val CONTINUE = "Continue Watching"
    const val LATEST = "Latest"
    const val POPULAR = "Popular"
    const val TOP_RATING = "Top Rating"
    const val ANIME = "Anime"
    const val UPCOMING = "Upcoming"
    const val KOREAN_SERIES = "Korean Series"
    const val CHINESE_SERIES = "Chinese Series"
    const val MOVIES = "Movies"

    /** API-backed rows, in screen order. */
    val feeds: List<String> = listOf(
        LATEST,
        POPULAR,
        TOP_RATING,
        ANIME,
        UPCOMING,
        KOREAN_SERIES,
        CHINESE_SERIES,
        MOVIES,
    )

    fun titles(continueWatching: Boolean): List<String> = buildList {
        if (continueWatching) add(CONTINUE)
        addAll(feeds)
    }
}
