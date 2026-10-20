package com.kisskh.tv.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.KhSpinner
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.gridCardMinWidth
import com.kisskh.tv.ui.components.screenContentPadding
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow

@Composable
fun MediaBrowseScreen(vm: AppViewModel, dest: Dest.MediaBrowse) {
    val state by remember(dest.kind, dest.latest) { vm.media.browse(dest.kind, dest.latest) }.collectAsStateWithLifecycle()
    val tv = KhTheme.formFactor == FormFactor.TV
    var focused by remember(dest.browseKey) { mutableStateOf<TmdbTitle?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = screenStart(), end = 24.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when (dest.kind) {
                    MediaKind.MOVIE -> "Movies"
                    MediaKind.TV -> "TV Shows"
                    MediaKind.ANIME -> "Anime"
                },
                style = KhTheme.typography.heading,
                color = KhColors.TextPrimary,
            )
            if (dest.kind != MediaKind.ANIME) {
                KhChip("Popular", selected = !dest.latest, onClick = { vm.setMediaSort(false) })
                KhChip("Latest", selected = dest.latest, onClick = { vm.setMediaSort(true) })
            }
        }
        if (tv) {
            Text(
                focused?.name ?: " ",
                style = KhTheme.typography.title,
                color = KhColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = screenStart(), end = 24.dp, bottom = 4.dp),
            )
        }
        val error = state.error
        when {
            state.loading && state.items.isEmpty() -> LoadingState(message = "Loading")
            error != null && state.items.isEmpty() -> ErrorState(
                error,
                onRetry = { vm.media.retryBrowse(dest.kind, dest.latest) },
                autoFocus = true,
            )
            state.items.isEmpty() -> EmptyState("Nothing to show.")
            else -> MediaGrid(
                items = state.items,
                names = !tv,
                loadingMore = state.loadingMore,
                moreError = state.error,
                end = state.page >= state.totalPages,
                onOpen = { vm.openTitle(it, dest.kind) },
                onFocused = { focused = it },
                onLoadMore = { vm.media.loadMore(dest.kind, dest.latest) },
                onRetryMore = { vm.media.retryMore(dest.kind, dest.latest) },
            )
        }
    }
}

@Composable
private fun MediaGrid(
    items: List<TmdbTitle>,
    names: Boolean,
    loadingMore: Boolean,
    moreError: String?,
    end: Boolean,
    onOpen: (TmdbTitle) -> Unit,
    onFocused: (TmdbTitle) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
) {
    val gap = cardGap()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(gridCardMinWidth()),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenContentPadding(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        itemsIndexed(items, key = { _, item -> "${item.mediaType}:${item.id}" }) { _, item ->
            MediaCard(
                title = item,
                showName = names,
                onClick = { onOpen(item) },
                onFocused = { onFocused(item) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                when {
                    loadingMore -> KhSpinner(size = KhTheme.spacing.xl)
                    moreError != null -> Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Couldn't load more", style = KhTheme.typography.body, color = KhColors.TextSecondary)
                        KhButton("Retry", onClick = onRetryMore, primary = true)
                    }
                    end -> Text("End of results", style = KhTheme.typography.label, color = KhColors.TextSecondary)
                }
            }
        }
    }
    val load = rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState, items.size, end, moreError) {
        if (end || moreError != null) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1 - 12
        }.distinctUntilChanged().filter { it }.collect { load.value() }
    }
}
