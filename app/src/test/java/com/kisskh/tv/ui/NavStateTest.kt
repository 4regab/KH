package com.kisskh.tv.ui

import com.kisskh.tv.api.CatalogQuery
import com.kisskh.tv.api.ContentType
import com.kisskh.tv.api.Country
import com.kisskh.tv.api.SortOrder
import com.kisskh.tv.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStateTest {
    private val start = NavState()

    @Test
    fun startsAtHomeAndBackExits() {
        assertEquals(Dest.Home(), start.current)
        assertEquals(RootTab.HOME, start.activeTab)
        assertNull(start.pop())
    }

    @Test
    fun backFromATopLevelScreenGoesHome() {
        val s = start.selectTab(RootTab.MOVIES)
        assertEquals(listOf(Dest.Home(), Dest.Movies()), s.stack)
        assertEquals(RootTab.MOVIES, s.activeTab)
        assertEquals(listOf<Dest>(Dest.Home()), s.pop()!!.stack)
    }

    @Test
    fun backFromDetailRestoresTheCardAndScrollOnTheScreenBelow() {
        val s = start.selectTab(RootTab.SERIES)
            .updateCurrent { (it as Dest.Series).withFocus(FocusKeys.show(42), scrollIndex = 17) }
            .push(Dest.Detail(42))
        assertEquals(RootTab.SERIES, s.activeTab) // the rail keeps showing TV Series
        val back = s.pop()!!.current as Dest.Series
        assertEquals(FocusKeys.show(42), back.focusKey)
        assertEquals(17, back.scrollIndex)
    }

    @Test
    fun withFocusKeepsTheOldScrollWhenNoneIsGiven() {
        val d = Dest.Home(focusKey = "a", scrollIndex = 3).withFocus("b")
        assertEquals(Dest.Home(focusKey = "b", scrollIndex = 3), d)
    }

    @Test
    fun backFromPlayerRestoresTheEpisodeOnDetail() {
        val player = Dest.Player(episodeId = 9, dramaId = 5, title = "T", label = "Episode 9")
        val s = start.push(Dest.Detail(5))
            .updateCurrent { (it as Dest.Detail).withFocus(FocusKeys.episode(9)) }
            .push(player)
        assertEquals(player, s.current)
        val detail = s.pop()!!.current as Dest.Detail
        assertEquals(9, detail.focusedEpisodeId)
    }

    @Test
    fun focusedEpisodeIdOnlyReadsEpisodeKeys() {
        assertNull(Dest.Detail(1).focusedEpisodeId)
        assertNull(Dest.Detail(1, focusKey = FocusKeys.WATCH).focusedEpisodeId)
        assertNull(Dest.Detail(1, focusKey = "ep:abc").focusedEpisodeId)
        assertEquals(12, Dest.Detail(1, focusKey = "ep:12").focusedEpisodeId)
    }

    @Test
    fun leavingAndReturningToATabKeepsItsFiltersFocusAndScroll() {
        val filtered = CatalogQuery(ContentType.SERIES, Country.JAPAN, Status.COMPLETED, SortOrder.RELEASE_DATE)
        val s = start.selectTab(RootTab.SERIES)
            .updateCurrent { Dest.Series(filtered, focusKey = FocusKeys.show(8), scrollIndex = 40) }
            .selectTab(RootTab.COUNTRIES)
            .selectTab(RootTab.SERIES)
        assertEquals(Dest.Series(filtered, FocusKeys.show(8), 40), s.current)
        assertEquals(2, s.stack.size)
    }

    @Test
    fun selectingTheActiveTabAgainReturnsToItsTopScreen() {
        val s = start.selectTab(RootTab.SERIES)
            .push(Dest.CountryCatalog(Country.CHINA))
            .push(Dest.Detail(1))
            .selectTab(RootTab.SERIES)
        assertEquals(2, s.stack.size)
        assertTrue(s.current is Dest.Series)
    }

    @Test
    fun selectingHomeFromATabKeepsHomeStateAndParksTheTab() {
        val s = start.updateCurrent { (it as Dest.Home).withFocus("show:1", 2) }
            .selectTab(RootTab.MOVIES)
            .selectTab(RootTab.HOME)
        assertEquals(listOf<Dest>(Dest.Home("show:1", 2)), s.stack)
        assertEquals(setOf(RootTab.MOVIES), s.parked.keys)
    }

    @Test
    fun catalogKeysCoverTheStackAndParkedTabs() {
        val s = start.selectTab(RootTab.SERIES).push(Dest.CountryCatalog(Country.SOUTH_KOREA))
        assertEquals(setOf("series", "country:2"), s.catalogKeys())

        val moved = s.selectTab(RootTab.COUNTRIES) // drops the country screen, parks Series
        assertEquals(setOf("series"), moved.catalogKeys())
    }

    // ---- fixed fields of each catalog screen

    @Test
    fun seriesAlwaysKeepsTypeSeries() {
        val d = Dest.Series().withQuery(CatalogQuery(ContentType.MOVIE, Country.CHINA, Status.ONGOING, SortOrder.LAST_UPDATE))
        assertEquals(CatalogQuery(ContentType.SERIES, Country.CHINA, Status.ONGOING, SortOrder.LAST_UPDATE), d.query)
    }

    @Test
    fun moviesAlwaysKeepTypeMovieAndIgnoreStatus() {
        val d = Dest.Movies().withQuery(CatalogQuery(ContentType.SERIES, Country.JAPAN, Status.ONGOING))
        assertEquals(CatalogQuery(ContentType.MOVIE, Country.JAPAN, Status.ALL), d.query)
    }

    @Test
    fun countryCatalogKeepsItsCountryAndOnlySeriesUsesStatus() {
        val kr = Dest.CountryCatalog(Country.SOUTH_KOREA)
        assertEquals(CatalogQuery(ContentType.ALL, Country.SOUTH_KOREA), kr.query)

        val series = kr.withQuery(kr.query.copy(type = ContentType.SERIES, status = Status.COMPLETED, country = Country.JAPAN))
        assertEquals(CatalogQuery(ContentType.SERIES, Country.SOUTH_KOREA, Status.COMPLETED), series.query)

        val movies = series.withQuery(series.query.copy(type = ContentType.MOVIE)) // tab switch drops the series-only filter
        assertEquals(CatalogQuery(ContentType.MOVIE, Country.SOUTH_KOREA, Status.ALL), movies.query)
    }

    @Test
    fun changingTheQueryResetsFocusAndScroll() {
        val d = Dest.Series(focusKey = "show:3", scrollIndex = 25).withQuery(CatalogQuery(order = SortOrder.RELEASE_DATE))
        assertNull(d.focusKey)
        assertEquals(0, d.scrollIndex)
        assertEquals(SortOrder.RELEASE_DATE, d.query.order)
    }

    @Test
    fun pagerKeys() {
        assertEquals("series", Dest.Series().pagerKey)
        assertEquals("movies", Dest.Movies().pagerKey)
        assertEquals("country:6", Dest.CountryCatalog(Country.UNITED_STATES).pagerKey)
    }
}
