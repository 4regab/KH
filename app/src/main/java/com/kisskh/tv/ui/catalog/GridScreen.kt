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
import com.kisskh.tv.ui.components.ArtCard
import com.kisskh.tv.ui.components.FocusRestore
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhSpinner
import com.kisskh.tv.ui.components.TitleCard
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.gridCardMinWidth
import com.kisskh.tv.ui.components.screenContentPadding
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** What to show under the last row of a [GridScreen]. */
sealed interface GridFooter {
    data object None : GridFooter
    data object Loading : GridFooter
    data class Failed(val onRetry: () -> Unit) : GridFooter
    data object End : GridFooter
}

/** Start loading the next page when the last visible card is this close to the end. */
private const val LOAD_MORE_AHEAD = 12

/**
 * A grid of title cards. TV cards are art only (the screen names the focused one). Phone cards
 * carry the title underneath. Columns adapt to the width.
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
    onFocusedShow: (Show) -> Unit = {},
    firstItemRequester: FocusRequester? = null,
) {
    val gap = cardGap()
    val artOnly = KhTheme.formFactor == FormFactor.TV
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
            val cardMod = Modifier
                .fillMaxWidth()
                .focusIfRestoring(restore, key)
                .then(if (index == 0 && firstItemRequester != null) Modifier.focusRequester(firstItemRequester) else Modifier)
            val focused = {
                tracker.key = key
                onFocusedShow(show)
            }
            if (artOnly) {
                ArtCard(show = show, onClick = { onOpen(show) }, modifier = cardMod, onFocused = focused)
            } else {
                TitleCard(show = show, onClick = { onOpen(show) }, modifier = cardMod, onFocused = focused)
            }
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
