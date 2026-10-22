package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.BrowseFilters
import com.kisskh.tv.api.source.FilterCapabilities
import com.kisskh.tv.api.source.SourceFilters
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.KhSpinner
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.edge
import com.kisskh.tv.ui.components.focusFallback
import com.kisskh.tv.ui.components.isTv
import com.kisskh.tv.ui.components.rememberAutoFocusAllowed
import com.kisskh.tv.ui.shell.PhoneSearchButton
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

@Composable
fun ShelfBrowse(vm: AppViewModel, library: Library, kind: MediaKind) {
    val title = when (kind) {
        MediaKind.MOVIE -> "Movies"
        MediaKind.TV -> if (library == Library.KISSKH) "TV Series" else "TV Shows"
        MediaKind.ANIME -> "Anime"
    }
    val tv = isTv()
    // What this grid can filter on (see SourceFilters), and what the viewer has chosen so far.
    val caps = remember(library, kind) { SourceFilters.capabilities(library, kind) }
    val chosen by vm.shelf.filters.collectAsStateWithLifecycle()
    val filters = remember(chosen, library, kind) { vm.shelf.filtersFor(library, kind) }
    val firstChip = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        if (tv) {
            Text(
                "$title · ${library.label}",
                style = KhTheme.typography.heading,
                color = KhColors.TextPrimary,
                modifier = Modifier.padding(start = edge(), top = 24.dp, bottom = 8.dp).semantics { heading() },
            )
        } else {
            BrowseBar(vm, library, title)
        }
        // The bar stays put while the grid below it is swapped, so it keeps focus and its place.
        ShelfFilterBar(caps, filters, onChange = { vm.shelf.setFilters(library, kind, it) }, firstChip = firstChip)
        // One grid per filter set: its pages, scroll spot and focus start fresh when a filter changes.
        // Up with no card above (the top row) goes to the bar even where Compose's search finds no chip.
        key(library, kind, filters.key()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (tv) Modifier.focusFallback(Key.DirectionUp, FocusDirection.Up) { runCatching { firstChip.requestFocus() } } else Modifier),
            ) {
                BrowseGrid(vm, library, kind, title, caps, filters, firstChip)
            }
        }
    }
}

@Composable
private fun BrowseGrid(
    vm: AppViewModel,
    library: Library,
    kind: MediaKind,
    title: String,
    caps: FilterCapabilities,
    filters: BrowseFilters,
    firstChip: FocusRequester,
) {
    val state by remember(library, kind, filters) { vm.shelf.browse(library, kind, filters) }.collectAsStateWithLifecycle()
    val grid = rememberSavedGrid(vm, "${library.key}:browse:$kind:${filters.key()}")
    when {
        state.loading -> GridSkeleton(library.shapeHint())
        state.error != null && state.items.isEmpty() -> ErrorState(
            "Couldn't load $title. ${state.error}",
            onRetry = { vm.shelf.retryBrowse(library, kind, filters) },
            // With filters set the viewer is working in the bar above: do not pull focus away from it.
            autoFocus = !filters.narrowed,
        )
        state.items.isEmpty() -> NoResults(
            emptyFilterMessage(library, kind, caps, filters),
            onClear = if (filters.narrowed) ({ vm.shelf.setFilters(library, kind, filters.cleared()) }) else null,
            firstChip = firstChip,
        )
        else -> {
            // Decided once from the first page, so later pages never change the card shape under the viewer.
            val shape = remember { shapeFor(state.items) }
            val last by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
            LaunchedEffect(last, state.items.size, state.hasMore) {
                if (state.hasMore && last >= state.items.size - 9) vm.shelf.loadMore(library, kind, filters)
            }
            CardGrid(
                vm = vm,
                items = state.items,
                shape = shape,
                state = grid,
                onOpen = { vm.openCard(it, library) },
                footer = {
                    when {
                        state.loadingMore -> KhSpinner(size = 28.dp)
                        state.error != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Couldn't load more.", style = KhTheme.typography.caption, color = KhColors.TextSecondary)
                            KhChip("Retry", onClick = { vm.shelf.loadMore(library, kind, filters) }, outlined = true)
                        }
                        !state.hasMore && state.items.size > 12 -> Text("That's everything.", style = KhTheme.typography.caption, color = KhColors.NavMuted)
                    }
                },
            )
        }
    }
}

/**
 * A grid with no titles. [message] says which filters found nothing; with [onClear] a "Clear filters"
 * button lets the viewer start again. Without filters set it is just the message.
 */
@Composable
private fun NoResults(message: String, onClear: (() -> Unit)?, firstChip: FocusRequester) {
    if (onClear == null) {
        EmptyState(message)
        return
    }
    val autoFocus = rememberAutoFocusAllowed()
    Box(Modifier.fillMaxSize().padding(KhTheme.spacing.l), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KhTheme.spacing.l)) {
            Text(message, style = KhTheme.typography.title, color = KhColors.TextSecondary, textAlign = TextAlign.Center)
            KhButton(
                "Clear filters",
                onClick = {
                    onClear()
                    // This button leaves with the empty list, so focus goes to the bar rather than getting lost.
                    if (autoFocus()) runCatching { firstChip.requestFocus() }
                },
                primary = true,
            )
        }
    }
}

/** Phone header on the Movies / TV / Anime tabs: the tab name and the source switch. */
@Composable
private fun BrowseBar(vm: AppViewModel, library: Library, title: String) {
    Row(
        Modifier.fillMaxWidth().height(TopBarHeight).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = KhTheme.typography.headline, color = KhColors.TextPrimary, modifier = Modifier.weight(1f).semantics { heading() })
        SourceButton(library, onClick = { vm.sourcePicker.value = true })
        PhoneSearchButton(onClick = { vm.selectTab(RootTab.SEARCH) }, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * A grid of cards of one [shape]. Cards are the same ones the Home rows use: same width (as the
 * minimum column), same gap, same caption rule. [badge] gives a card's top-left tag, if any. [vm] is read for the
 * Coming soon and Not available marks. [detail] gives a second caption line under the title (a year and kind);
 * it shows only where the shape has a caption.
 */
@Composable
internal fun CardGrid(
    vm: AppViewModel,
    items: List<TitleCard>,
    shape: CardShape,
    state: LazyGridState,
    onOpen: (TitleCard) -> Unit,
    caption: Boolean = shape == CardShape.LANDSCAPE,
    badge: (TitleCard) -> String? = ::continueBadge,
    detail: ((TitleCard) -> String?)? = null,
    footer: @Composable () -> Unit = {},
) {
    val gap = cardGap()
    // Two cards with the same key would crash the grid, so keep the first of each.
    val cards = remember(items) { items.distinctBy(TitleCard::listKey) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardWidth(shape)),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = edge(), end = edge(), top = if (isTv()) 12.dp else 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(if (caption) 14.dp else gap),
    ) {
        items(cards, key = TitleCard::listKey) { card ->
            val mark = rememberTitleMark(vm, card)
            if (caption && detail != null) {
                // The title and its detail line are drawn here, so the card itself shows no caption.
                Column {
                    ShelfCard(card, shape, onClick = { onOpen(card) }, caption = false, badge = badge(card), mark = mark)
                    CardCaption(card.name, detail(card))
                }
            } else {
                ShelfCard(card, shape, onClick = { onOpen(card) }, caption = caption, badge = badge(card), mark = mark)
            }
        }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { footer() }
        }
    }
}

/** Title and a quieter second line under a landscape card. Never under 12 sp on a phone. */
@Composable
private fun CardCaption(name: String, detail: String?) {
    val style = if (isTv()) KhTheme.typography.cardTitle else KhTheme.typography.cardTitlePhone
    Text(
        name,
        style = style,
        color = KhColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
    )
    if (!detail.isNullOrBlank()) {
        Text(
            detail,
            style = style,
            color = KhColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/** Grey cards in the grid's [shape] while a page loads. */
@Composable
internal fun GridSkeleton(shape: CardShape) {
    val gap = cardGap()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardWidth(shape)),
        contentPadding = PaddingValues(start = edge(), end = edge(), top = if (isTv()) 12.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        userScrollEnabled = false,
        modifier = Modifier.semantics { contentDescription = "Loading" },
    ) {
        items(12) {
            Column {
                SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(shape.ratio()))
                if (shape == CardShape.LANDSCAPE) SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(12.dp))
            }
        }
    }
}
