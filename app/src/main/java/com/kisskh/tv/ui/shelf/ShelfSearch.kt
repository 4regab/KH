package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.api.MY_LIST_ROW
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.CONTINUE_ROW
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.edge
import com.kisskh.tv.ui.components.isTv
import com.kisskh.tv.ui.media.KhField
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

@Composable
fun ShelfSearch(vm: AppViewModel, library: Library) {
    val query by vm.shelf.query.collectAsStateWithLifecycle()
    val all by vm.shelf.allSources.collectAsStateWithLifecycle()
    val results by vm.shelf.results.collectAsStateWithLifecycle()
    val grid = rememberSavedGrid(vm, "search")
    val trendingGrid = rememberSavedGrid(vm, "search:trending")
    LaunchedEffect(library) {
        if (query.isNotBlank() && !all) vm.shelf.setQuery(library, query)
    }
    val field = remember { FocusRequester() }
    // A new search on a phone opens the keyboard at once. Coming back to results does not.
    LaunchedEffect(Unit) { if (query.isBlank()) runCatching { field.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = edge(), end = edge(), top = if (isTv()) 24.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KhField(
                    value = query,
                    onValueChange = { vm.shelf.setQuery(library, it) },
                    hint = if (all) "Search every source" else "Search ${library.label}",
                    leading = R.drawable.ic_nav_search,
                    modifier = Modifier.weight(1f).focusRequester(field),
                )
                if (query.isNotEmpty()) {
                    KhFocusable(
                        onClick = { vm.shelf.setQuery(library, "") },
                        modifier = Modifier.padding(start = 4.dp).size(48.dp).semantics { contentDescription = "Clear search" },
                        shape = CircleShape,
                        scaleAndLift = false,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = KhColors.TextSecondary, modifier = Modifier.align(Alignment.Center).size(22.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KhChip(library.label, selected = !all, onClick = { vm.shelf.setQuery(library, query, all = false) })
                KhChip("All sources", selected = all, onClick = { vm.shelf.setQuery(library, query, all = true) })
            }
        }
        if (query.isBlank()) {
            Trending(vm, library, all, trendingGrid)
            return@Column
        }
        when (val s = results) {
            UiState.Loading -> GridSkeleton(if (all) CardShape.LANDSCAPE else library.shapeHint())
            is UiState.Empty -> EmptyState(s.message ?: "No titles match.")
            is UiState.Error -> ErrorState("Search failed. ${s.message}", onRetry = { vm.shelf.setQuery(library, query, all = all) })
            is UiState.Success -> {
                // Every source at once mixes posters and stills, so those results stay landscape. Each one
                // carries its source name as the tag. A single source uses its own Home shape.
                val shape = if (all) CardShape.LANDSCAPE else shapeFor(s.data)
                CardGrid(
                    vm = vm,
                    items = s.data,
                    shape = shape,
                    state = grid,
                    onOpen = { vm.openCard(it, it.source) },
                    badge = if (all) ({ card -> card.meta }) else ::continueBadge,
                    detail = ::searchDetail,
                )
            }
        }    }
}

/** "2019 · Movie": the second caption line of a search result. Null when the card has neither part. */
internal fun searchDetail(card: TitleCard): String? {
    val kind = when (card.kind) {
        MediaKind.MOVIE -> "Movie"
        MediaKind.TV -> "TV"
        MediaKind.ANIME -> "Anime"
    }
    return listOfNotNull(card.year?.trim()?.takeIf { it.isNotEmpty() }, kind).joinToString(" · ")
}

/**
 * What the screen shows before anything is typed: the first Home row of this source that is not the
 * viewer's own (Continue Watching, My List), as a grid. Same cards as Home, same marks.
 */
@Composable
private fun Trending(vm: AppViewModel, library: Library, all: Boolean, grid: LazyGridState) {
    val home by remember(library) { vm.shelf.home(library) }.collectAsStateWithLifecycle()
    val state = home
    val row = (state as? UiState.Success)?.data?.firstOrNull { it.title != CONTINUE_ROW && it.title != MY_LIST_ROW }
    val title = if (all) "Popular right now" else "Trending on ${library.label}"
    when {
        state is UiState.Loading -> Column(Modifier.fillMaxSize()) {
            TrendingHeading(title)
            GridSkeleton(library.shapeHint())
        }
        row != null && row.items.isNotEmpty() -> Column(Modifier.fillMaxSize()) {
            TrendingHeading(title)
            CardGrid(
                vm = vm,
                items = row.items,
                shape = shapeFor(row.items),
                state = grid,
                onOpen = { vm.openCard(it, library) },
            )
        }
        else -> EmptyState(if (all) "Find a title on every source at once." else "Find movies, shows and anime on ${library.label}.")
    }
}

@Composable
private fun TrendingHeading(text: String) {
    Text(
        text,
        style = KhTheme.typography.rowTitle,
        color = KhColors.TextPrimary,
        modifier = Modifier.padding(start = edge(), top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}
