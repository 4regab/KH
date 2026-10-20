package com.kisskh.tv.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

private const val KEY_FIRST = "key:space"
private fun resultKey(title: TmdbTitle) = "r:${title.mediaType}:${title.id}"

private fun TmdbTitle.kind(): MediaKind = if (mediaType == "tv") MediaKind.TV else MediaKind.MOVIE

/** What to show before anything is typed: the source's own trending titles. */
@Composable
private fun rememberSuggestions(vm: AppViewModel, library: Library): List<TmdbTitle> {
    val home by remember(library) { vm.media.home(library) }.collectAsStateWithLifecycle()
    val rows = (home as? UiState.Success)?.data?.rows.orEmpty()
    return remember(rows) { rows.firstOrNull { !it.local }?.items.orEmpty().take(18) }
}

/**
 * Search for CineWave and CineJoy, laid out like Netflix.
 *
 * - TV: an on-screen keyboard on the left types straight into the query (no system keyboard over
 *   the page); results fill a three-wide grid on the right as you type. Before anything is typed,
 *   the grid shows what is trending on this source.
 * - Phone: a search field at the top; results as a poster grid, and "Top searches" before that.
 */
@Composable
fun MediaSearchScreen(vm: AppViewModel, library: Library, dest: Dest.Search) {
    if (KhTheme.formFactor == FormFactor.TV) TvSearch(vm, library, dest) else PhoneSearch(vm, library)
}

@Composable
private fun TvSearch(vm: AppViewModel, library: Library, dest: Dest.Search) {
    val query by vm.media.query.collectAsStateWithLifecycle()
    val results by vm.media.results.collectAsStateWithLifecycle()
    val suggestions = rememberSuggestions(vm, library)
    val restore = rememberFocusRestore(remember { dest.focusKey ?: KEY_FIRST })
    val tracker = remember { FocusTracker() }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = dest.scrollIndex)
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, gridState.firstVisibleItemIndex) }

    Row(Modifier.fillMaxSize().padding(start = screenStart(), top = KhTheme.spacing.safeVertical)) {
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            // The typed text, with a caret, where the eye expects a search field.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(KhColors.Surface, KhTheme.shapes.button)
                    .padding(horizontal = 14.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    androidx.compose.ui.res.painterResource(SearchIcon),
                    contentDescription = null,
                    tint = KhColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    if (query.isEmpty()) "Search ${library.label}" else query + "|",
                    color = if (query.isEmpty()) KhColors.TextSecondary else KhColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = if (query.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            TvKeyboard(
                onType = { c ->
                    tracker.key = KEY_FIRST
                    vm.media.setQuery((query + c).trimStart())
                },
                onDelete = { vm.media.setQuery(query.dropLast(1)) },
                onClear = { vm.media.setQuery("") },
                modifier = Modifier.padding(top = 16.dp),
                firstKey = Modifier
                    .focusIfRestoring(restore, KEY_FIRST)
                    .onFocusChanged { if (it.isFocused) tracker.key = KEY_FIRST },
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp)) {
            val showing: List<TmdbTitle>? = when (val s = results) {
                is UiState.Success -> s.data
                is UiState.Empty -> if (query.isBlank()) suggestions else null
                else -> null
            }
            when {
                showing != null && showing.isNotEmpty() -> ResultGrid(
                    heading = if (query.isBlank()) "Trending on ${library.label}" else "Results for \u201C${query.trim()}\u201D",
                    items = showing,
                    gridState = gridState,
                    onOpen = { title ->
                        vm.saveFocus(resultKey(title), gridState.firstVisibleItemIndex)
                        vm.openTitle(title, title.kind())
                    },
                    itemModifier = { title ->
                        Modifier
                            .focusIfRestoring(restore, resultKey(title))
                            .onFocusChanged { if (it.isFocused) tracker.key = resultKey(title) }
                    },
                )
                results is UiState.Loading -> LoadingState(message = "Searching")
                results is UiState.Error -> ErrorState(
                    (results as UiState.Error).message,
                    onRetry = { vm.media.setQuery(query, immediate = true) },
                )
                query.isNotBlank() -> EmptyState(
                    (results as? UiState.Empty)?.message ?: "No titles match \u201C${query.trim()}\u201D. Try fewer letters.",
                )
                else -> EmptyState("Type a movie or show with the keyboard.")
            }
        }
    }
    LaunchedEffect(Unit) {
        val items = (results as? UiState.Success)?.data ?: suggestions
        val index = items.indexOfFirst { resultKey(it) == restore.key }
        if (index >= 0) gridState.revealIndex(index + 1)
    }
}

@Composable
private fun ResultGrid(
    heading: String,
    items: List<TmdbTitle>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpen: (TmdbTitle) -> Unit,
    itemModifier: @Composable (TmdbTitle) -> Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 0.dp,
            end = KhTheme.spacing.safeHorizontal,
            bottom = KhTheme.spacing.safeVertical + KhTheme.spacing.focusBleed,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                heading,
                style = KhTheme.typography.rowTitle,
                color = KhColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp).semantics { heading() },
            )
        }
        items(items, key = { "${it.mediaType}:${it.id}" }) { item ->
            MediaCard(
                title = item,
                onClick = { onOpen(item) },
                modifier = Modifier.fillMaxWidth().then(itemModifier(item)),
            )
        }
    }
}

// ------------------------------------------------------------------ phone

@Composable
private fun PhoneSearch(vm: AppViewModel, library: Library) {
    val query by vm.media.query.collectAsStateWithLifecycle()
    val results by vm.media.results.collectAsStateWithLifecycle()
    val suggestions = rememberSuggestions(vm, library)
    Column(Modifier.fillMaxSize()) {
        KhField(
            value = query,
            onValueChange = { vm.media.setQuery(it) },
            hint = "Search shows and movies",
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            imeAction = ImeAction.Search,
            onAction = { vm.media.setQuery(query, immediate = true) },
            leading = SearchIcon,
        )
        when (val s = results) {
            UiState.Loading -> LoadingState(message = "Searching")
            is UiState.Error -> ErrorState(s.message, onRetry = { vm.media.setQuery(query, immediate = true) })
            is UiState.Empty -> if (query.isBlank() && suggestions.isNotEmpty()) {
                TopSearches(suggestions, onOpen = { vm.openTitle(it, it.kind()) })
            } else {
                EmptyState(if (query.isBlank()) "Find a movie or show." else s.message ?: "No titles match.")
            }
            is UiState.Success -> LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Movies & TV",
                        style = KhTheme.typography.rowTitle,
                        modifier = Modifier.padding(vertical = 4.dp).semantics { heading() },
                    )
                }
                items(s.data, key = { "${it.mediaType}:${it.id}" }) { item ->
                    MediaCard(
                        title = item,
                        poster = true,
                        onClick = { vm.openTitle(item, item.kind()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Netflix's "Top Searches": a still, the name, and a play glyph, one per line. */
@Composable
private fun TopSearches(items: List<TmdbTitle>, onOpen: (TmdbTitle) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Top Searches",
                style = KhTheme.typography.rowTitle,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
            )
        }
        items(items, key = { "${it.mediaType}:${it.id}" }) { item ->
            KhFocusable(
                onClick = { onOpen(item) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                shape = KhTheme.shapes.art,
                scaleAndLift = false,
            ) {
                Row(
                    Modifier.fillMaxWidth().background(KhColors.Surface).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(item.artwork, item.name, Modifier.width(140.dp).aspectRatio(16f / 9f))
                    Text(
                        item.name,
                        style = KhTheme.typography.label,
                        color = KhColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = KhColors.TextPrimary, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
