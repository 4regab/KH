package com.kisskh.tv.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Text
import com.kisskh.tv.api.Show
import com.kisskh.tv.ui.FocusKeys
import com.kisskh.tv.ui.components.FocusRestore
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhSpinner
import com.kisskh.tv.ui.components.ShowCard
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.gridCardMinWidth
import com.kisskh.tv.ui.components.screenContentPadding
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** What to show under the last row of a [GridScreen]. */
sealed interface GridFooter {
    /** Nothing: the list is not paged (search results). */
    data object None : GridFooter

    /** The next page is loading. */
    data object Loading : GridFooter

    /** The next page failed. The items already loaded stay. */
    data class Failed(val onRetry: () -> Unit) : GridFooter

    /** Everything has been loaded. */
    data object End : GridFooter
}

/** Start loading the next page when the last visible card is this close to the end. */
private const val LOAD_MORE_AHEAD = 12

/**
 * A grid of title cards. The columns adapt to the width (about 5 on a 960 dp TV, 2 on a phone).
 * It is used by the catalog screens (paged) and by Search.
 *
 * - Cards have stable keys (the title id), so scroll and focus survive list changes.
 * - [restore] gives focus back to the card the user came from; [tracker] remembers the focused card.
 * - [onLoadMore] is called when the user nears the end. It is safe to call it often.
 * - [footer] shows the loading, retry or end-of-results line.
 * - [firstItemRequester] lets the screen move focus into the grid (Search does that).
 */
@Composable
fun GridScreen(
    items: List<Show>,
    state: LazyGridState,
    restore: FocusRestore,
    tracker: FocusTracker,
    onOpen: (Show) -> Unit,
    modifier: Modifier = Modifier,
    footer: GridFooter = GridFooter.None,
    onLoadMore: () -> Unit = {},
    showEpisodeCount: Boolean = true,
    firstItemRequester: FocusRequester? = null,
) {
    val gap = cardGap()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(gridCardMinWidth()),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = screenContentPadding(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        itemsIndexed(items, key = { _, show -> show.id }, contentType = { _, _ -> "card" }) { index, show ->
            val key = FocusKeys.show(show.id)
            ShowCard(
                show = show,
                onClick = { onOpen(show) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusIfRestoring(restore, key)
                    .then(if (index == 0 && firstItemRequester != null) Modifier.focusRequester(firstItemRequester) else Modifier),
                showEpisodeCount = showEpisodeCount,
                onFocused = { tracker.key = key },
            )
        }
        when (footer) {
            GridFooter.None -> Unit
            GridFooter.Loading -> item(key = "footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                FooterBox { KhSpinner(size = KhTheme.spacing.xl) }
            }
            is GridFooter.Failed -> item(key = "footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                FooterBox {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KhTheme.spacing.m),
                    ) {
                        Text("Couldn't load more", style = KhTheme.typography.body, color = KhColors.TextSecondary)
                        KhButton("Retry", onClick = footer.onRetry, primary = true)
                    }
                }
            }
            GridFooter.End -> item(key = "footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                FooterBox { Text("End of results", style = KhTheme.typography.label, color = KhColors.TextSecondary) }
            }
        }
    }

    // Near the end of what is loaded: ask for more. Restarts when the item count changes, so a page that
    // arrives and leaves the user still near the end triggers the next one.
    val loadMore = rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, items.size) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1 - LOAD_MORE_AHEAD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMore.value() }
    }
}

@Composable
private fun FooterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = KhTheme.spacing.m), contentAlignment = Alignment.Center) { content() }
}
