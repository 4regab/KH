package com.kisskh.tv.ui

import com.kisskh.tv.api.CatalogQuery
import com.kisskh.tv.api.ContentType
import com.kisskh.tv.api.Country
import com.kisskh.tv.api.Status

/** The items on the left rail, top to bottom. */
enum class RootTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    SERIES("TV Series"),
    MOVIES("Movies"),
    COUNTRIES("Countries"),
}

/** Helpers for the opaque `focusKey` strings that screens store in a [Dest]. */
object FocusKeys {
    fun show(id: Int) = "show:$id"
    fun episode(id: Int) = "ep:$id"
    fun country(country: Country) = "country:${country.id}"
    const val WATCH = "watch"
}

/**
 * One screen on the back stack, plus the state needed to bring it back as the user left it.
 * Every [Restorable] has:
 *
 * - [Restorable.focusKey]: which item had focus (an opaque string the screen chooses, see [FocusKeys]).
 * - [Restorable.scrollIndex]: first visible index of its main list or grid.
 *
 * Screens write these with `AppViewModel.saveFocus` just before they navigate away.
 */
sealed interface Dest {
    /** The rail item this destination belongs to, or null for detail and player. */
    val tab: RootTab? get() = null

    sealed interface Restorable : Dest {
        val focusKey: String?
        val scrollIndex: Int
        fun withFocus(focusKey: String?, scrollIndex: Int? = null): Restorable
    }

    /** A paged, filtered list: [Series], [Movies] or [CountryCatalog]. */
    sealed interface Catalog : Restorable {
        val query: CatalogQuery

        /** Same pager for the same key, so coming back finds the loaded pages. */
        val pagerKey: String

        /**
         * Returns this destination with [query] applied. Fields this screen fixes (type, country, and
         * status where it does not apply) are corrected. Focus and scroll are reset, because the list
         * is now different.
         */
        fun withQuery(query: CatalogQuery): Catalog
    }

    data class Home(
        override val focusKey: String? = null,
        override val scrollIndex: Int = 0,
    ) : Restorable {
        override val tab get() = RootTab.HOME
        override fun withFocus(focusKey: String?, scrollIndex: Int?) =
            copy(focusKey = focusKey, scrollIndex = scrollIndex ?: this.scrollIndex)
    }

    /** The query text itself lives in `AppViewModel.search` and survives navigation. */
    data class Search(
        override val focusKey: String? = null,
        override val scrollIndex: Int = 0,
    ) : Restorable {
        override val tab get() = RootTab.SEARCH
        override fun withFocus(focusKey: String?, scrollIndex: Int?) =
            copy(focusKey = focusKey, scrollIndex = scrollIndex ?: this.scrollIndex)
    }

    /** All TV series (type 1). The user can change country, status and sort order. */
    data class Series(
        override val query: CatalogQuery = CatalogQuery(type = ContentType.SERIES),
        override val focusKey: String? = null,
        override val scrollIndex: Int = 0,
    ) : Catalog {
        override val tab get() = RootTab.SERIES
        override val pagerKey get() = "series"
        override fun withFocus(focusKey: String?, scrollIndex: Int?) =
            copy(focusKey = focusKey, scrollIndex = scrollIndex ?: this.scrollIndex)
        override fun withQuery(query: CatalogQuery) =
            copy(query = query.copy(type = ContentType.SERIES), focusKey = null, scrollIndex = 0)
    }

    /** All movies (type 2). The user can change country and sort order (status does not apply). */
    data class Movies(
        override val query: CatalogQuery = CatalogQuery(type = ContentType.MOVIE),
        override val focusKey: String? = null,
        override val scrollIndex: Int = 0,
    ) : Catalog {
        override val tab get() = RootTab.MOVIES
        override val pagerKey get() = "movies"
        override fun withFocus(focusKey: String?, scrollIndex: Int?) =
            copy(focusKey = focusKey, scrollIndex = scrollIndex ?: this.scrollIndex)
        override fun withQuery(query: CatalogQuery) =
            copy(query = query.copy(type = ContentType.MOVIE, status = Status.ALL), focusKey = null, scrollIndex = 0)
    }

    /** The grid of country tiles. */
    data class Countries(
        override val focusKey: String? = null,
        override val scrollIndex: Int = 0,
    ) : Restorable {
        override val tab get() = RootTab.COUNTRIES
        override fun withFocus(focusKey: String?, scrollIndex: Int?) =
            copy(focusKey = focusKey, scrollIndex = scrollIndex ?: this.scrollIndex)
    }

    /**
     * One country. The tab is `query.type`: ALL, SERIES or MOVIE (see `CatalogSource.availableTabs`).
     * Status only applies to the series tab. Opening the same country twice shares one pager.
     */
    data class CountryCatalog(
        val country: Country,
        override val query: CatalogQuery = CatalogQuery(country = country),
        override val focusKey: String? = null,
        override val scrollIndex: Int = 0,
    ) : Catalog {
        override val pagerKey get() = "country:${country.id}"
        override fun withFocus(focusKey: String?, scrollIndex: Int?) =
            copy(focusKey = focusKey, scrollIndex = scrollIndex ?: this.scrollIndex)
        override fun withQuery(query: CatalogQuery) = copy(
            query = query.copy(
                country = country,
                status = if (query.type == ContentType.SERIES) query.status else Status.ALL,
            ),
            focusKey = null,
            scrollIndex = 0,
        )
    }

    /** A title page. When the user picks an episode, its key is `FocusKeys.episode(id)`. */
    data class Detail(
        val dramaId: Int,
        override val focusKey: String? = null,
        override val scrollIndex: Int = 0,
    ) : Restorable {
        /** The episode that had focus, if the saved key is an episode key. */
        val focusedEpisodeId: Int? get() = focusKey?.takeIf { it.startsWith("ep:") }?.removePrefix("ep:")?.toIntOrNull()
        override fun withFocus(focusKey: String?, scrollIndex: Int?) =
            copy(focusKey = focusKey, scrollIndex = scrollIndex ?: this.scrollIndex)
    }

    /**
     * Full-screen playback. [title] is the title's name, [label] the part ("Episode 5", "Part 2",
     * "Movie"). [episodeNumber] and [thumbnail] only feed watch history.
     */
    data class Player(
        val episodeId: Int,
        val dramaId: Int,
        val title: String,
        val label: String,
        val episodeNumber: Float = 0f,
        val thumbnail: String? = null,
    ) : Dest
}

/**
 * The back stack. Immutable: every function returns the new state.
 *
 * - The stack always starts with [Dest.Home]. Picking another rail item gives `[Home, thatTab]`,
 *   so Back from any top-level screen goes to Home, and Back from Home exits.
 * - [parked] remembers the last state of each top-level screen the user left through the rail, so
 *   picking it again brings back its filters, scroll and focus.
 */
data class NavState(
    val stack: List<Dest> = listOf(Dest.Home()),
    val parked: Map<RootTab, Dest> = emptyMap(),
) {
    val current: Dest get() = stack.last()

    /** The rail item to show as selected. */
    val activeTab: RootTab get() = stack.getOrNull(1)?.tab ?: RootTab.HOME

    /** Open a screen on top of the current one. */
    fun push(dest: Dest): NavState = copy(stack = stack + dest)

    /** Go back one screen, or null when already at Home (the app should exit). */
    fun pop(): NavState? = if (stack.size > 1) copy(stack = stack.dropLast(1)) else null

    /** Change the top screen's state (focus, scroll, filters). */
    fun updateCurrent(transform: (Dest) -> Dest): NavState =
        copy(stack = stack.dropLast(1) + transform(stack.last()))

    /** Rail selection. Picking the active item again returns to its top screen. */
    fun selectTab(tab: RootTab): NavState {
        if (tab == activeTab) return copy(stack = stack.take(if (tab == RootTab.HOME) 1 else 2))
        val home = stack.first()
        val leaving = stack.getOrNull(1)?.takeIf { it.tab != null } // the top-level screen being left
        val nextParked = if (leaving != null) parked + (leaving.tab!! to leaving) else parked
        return if (tab == RootTab.HOME) NavState(listOf(home), nextParked)
        else NavState(listOf(home, nextParked[tab] ?: defaultFor(tab)), nextParked - tab)
    }

    /** Every catalog destination still reachable (on the stack or parked): their pagers must stay. */
    fun catalogKeys(): Set<String> =
        (stack + parked.values).filterIsInstance<Dest.Catalog>().map { it.pagerKey }.toSet()

    private fun defaultFor(tab: RootTab): Dest = when (tab) {
        RootTab.HOME -> Dest.Home()
        RootTab.SEARCH -> Dest.Search()
        RootTab.SERIES -> Dest.Series()
        RootTab.MOVIES -> Dest.Movies()
        RootTab.COUNTRIES -> Dest.Countries()
    }
}
