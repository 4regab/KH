package com.kisskh.tv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.CatalogQuery
import com.kisskh.tv.api.ContentType
import com.kisskh.tv.api.Country
import com.kisskh.tv.api.Show
import com.kisskh.tv.api.SortOrder
import com.kisskh.tv.api.Status
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.FocusKeys
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.FilterDropdown
import com.kisskh.tv.ui.components.FocusRestore
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.FocusedTitleLine
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.rememberFocusedYear
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.errorMessage
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

private const val FILTER_COUNTRY = "filter:country"
private const val FILTER_STATUS = "filter:status"
private const val FILTER_SORT = "filter:sort"
private fun tabKey(type: ContentType) = "tab:${type.name}"
private const val TABS_RETRY = "tab:retry"

/**
 * One screen for TV Series, Movies and a single country: a heading, filter chips and a paged grid.
 *
 * Series: country, status, sort. Movies: country, sort. A country: All / TV Series / Movies
 * (empty ones hidden), sort, and status on the series tab.
 * Changing a filter starts again from page 1. Coming back restores filters, scroll and focus.
 * On TV the focused title (and its year, once known) sits above the grid, because the cards are art only.
 */
@Composable
fun CatalogScreen(vm: AppViewModel, dest: Dest.Catalog) {
    val pager = remember(dest.pagerKey) { vm.pager(dest) }
    val state by pager.state.collectAsStateWithLifecycle()
    val query = dest.query
    val tv = KhTheme.formFactor == FormFactor.TV

    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = dest.scrollIndex)
    val tracker = remember { FocusTracker() }
    val restore = rememberFocusRestore(
        remember {
            dest.focusKey?.takeIf { key ->
                key.startsWith("filter:") || key.startsWith("tab:") || key.startsWith("show:")
            } ?: if (dest is Dest.CountryCatalog) FILTER_SORT else FILTER_COUNTRY
        },
    )
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, gridState.firstVisibleItemIndex) }

    var shownQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != shownQuery) {
            shownQuery = query
            gridState.scrollToItem(0)
        }
    }

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(state.items.size) {
        if (revealed) return@LaunchedEffect
        val index = state.items.indexOfFirst { FocusKeys.show(it.id) == restore.key }
        if (index >= 0) {
            gridState.revealIndex(index)
            revealed = true
        }
    }

    var focused by remember { mutableStateOf<Show?>(null) }
    val year = rememberFocusedYear(vm, if (tv) focused?.id else null)

    Column(Modifier.fillMaxSize().padding(top = KhTheme.spacing.safeVertical)) {
        CatalogHeader(vm, dest, tracker, restore)
        if (tv) {
            FocusedTitleLine(
                title = focused?.title?.trim()?.takeIf { it.isNotEmpty() },
                year = year,
                modifier = Modifier.padding(start = screenStart(), end = KhTheme.spacing.safeHorizontal),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isInitialError -> ErrorState(errorMessage(state.error!!), onRetry = { pager.retry() })
                state.isEmpty -> EmptyState("No titles in this category")
                state.items.isEmpty() -> LoadingState()
                else -> GridScreen(
                    items = state.items,
                    state = gridState,
                    restore = restore,
                    tracker = tracker,
                    onOpen = { show ->
                        vm.saveFocus(FocusKeys.show(show.id), gridState.firstVisibleItemIndex)
                        vm.push(Dest.Detail(show.id))
                    },
                    footer = when {
                        state.isLoadingMore -> GridFooter.Loading
                        state.error != null -> GridFooter.Failed { pager.retry() }
                        state.endReached -> GridFooter.End
                        else -> GridFooter.None
                    },
                    onLoadMore = { pager.loadNext() },
                    onFocusedShow = { focused = it },
                )
            }
        }
    }
}

@Composable
private fun CatalogHeader(vm: AppViewModel, dest: Dest.Catalog, tracker: FocusTracker, restore: FocusRestore) {
    val query = dest.query
    val tv = KhTheme.formFactor == FormFactor.TV
    val heading = when (dest) {
        is Dest.Series -> "TV Series"
        is Dest.Movies -> "Movies"
        is Dest.CountryCatalog -> dest.country.label
    }
    val change: ((CatalogQuery) -> CatalogQuery) -> Unit = { vm.setCatalogQuery(it) }

    Column(
        Modifier.padding(start = screenStart(), end = KhTheme.spacing.safeHorizontal),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            heading,
            style = if (tv) KhTheme.typography.heading else KhTheme.typography.headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (dest is Dest.CountryCatalog) {
                CountryTabs(vm, dest.country, query, tracker, restore, change)
            } else {
                FilterDropdown(
                    label = "Country",
                    options = listOf<Country?>(null) + Country.entries,
                    selected = query.country,
                    optionLabel = { it?.label ?: "All" },
                    onSelect = { c -> change { it.copy(country = c) } },
                    chipKey = FILTER_COUNTRY,
                    restore = restore,
                    onFocused = { tracker.key = FILTER_COUNTRY },
                )
            }
            if (query.type == ContentType.SERIES) {
                FilterDropdown(
                    label = "Status",
                    options = Status.entries,
                    selected = query.status,
                    optionLabel = { it.label },
                    onSelect = { s -> change { it.copy(status = s) } },
                    chipKey = FILTER_STATUS,
                    restore = restore,
                    onFocused = { tracker.key = FILTER_STATUS },
                )
            }
            FilterDropdown(
                label = "Sort",
                options = SortOrder.entries,
                selected = query.order,
                optionLabel = { it.label },
                onSelect = { o -> change { it.copy(order = o) } },
                chipKey = FILTER_SORT,
                restore = restore,
                onFocused = { tracker.key = FILTER_SORT },
            )
        }
    }
}

/** All / TV Series / Movies for one country. Empty ones are left out. */
@Composable
private fun CountryTabs(
    vm: AppViewModel,
    country: Country,
    query: CatalogQuery,
    tracker: FocusTracker,
    restore: FocusRestore,
    change: ((CatalogQuery) -> CatalogQuery) -> Unit,
) {
    val tabs by remember(country) { vm.countryTabs(country) }.collectAsStateWithLifecycle()
    when (val t = tabs) {
        is UiState.Success -> if (t.data.size > 1) {
            t.data.forEach { type ->
                val key = tabKey(type)
                KhChip(
                    text = type.label,
                    outlined = true,
                    marked = query.type == type,
                    onClick = { change { it.copy(type = type) } },
                    modifier = Modifier
                        .focusIfRestoring(restore, key)
                        .onFocusChanged { if (it.isFocused) tracker.key = key },
                )
            }
            Box(Modifier.width(1.dp).height(24.dp).background(KhColors.Elevated))
        }
        is UiState.Error -> {
            KhChip(
                text = "Couldn't check tabs. Retry",
                onClick = { vm.retryCountryTabs(country) },
                outlined = true,
                modifier = Modifier
                    .focusIfRestoring(restore, TABS_RETRY)
                    .onFocusChanged { if (it.isFocused) tracker.key = TABS_RETRY },
            )
        }
        else -> Unit
    }
}
