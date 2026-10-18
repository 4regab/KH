package com.kisskh.tv.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Show
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.HomeContent
import com.kisskh.tv.ui.HomeRow
import com.kisskh.tv.ui.Playable
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.FocusRestore
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.HeroBackdrop
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.ShowCard
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.heroHeight
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.components.rowCardWidth
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.metaParts
import com.kisskh.tv.ui.playerDest
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.launch

// Focus keys. A card key holds the rail title too, because one title can sit in several rails.
private const val HERO_WATCH = "hero:watch"
private const val HERO_INFO = "hero:info"
private val BrowseTabs = listOf(RootTab.SERIES, RootTab.MOVIES, RootTab.COUNTRIES)
private fun browseKey(tab: RootTab) = "browse:${tab.name}"
private fun cardKey(rowTitle: String, showId: Int) = "home:$rowTitle:$showId"

/**
 * Home: a big featured title, shortcuts to the catalog screens, then rails of titles.
 * Nothing plays by itself and nothing rotates.
 */
@Composable
fun HomeScreen(vm: AppViewModel, dest: Dest.Home) {
    val state by vm.home.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> LoadingState(message = "Loading")
        is UiState.Error -> ErrorState(s.message, onRetry = { vm.retryHome() }, autoFocus = true)
        is UiState.Empty -> ErrorState(s.message ?: "Nothing to show right now.", onRetry = { vm.retryHome() }, autoFocus = true)
        is UiState.Success -> HomeContentView(vm, dest, s.data)
    }
}

@Composable
private fun HomeContentView(vm: AppViewModel, dest: Dest.Home, content: HomeContent) {
    val featured = content.featured
    val hasFailed = content.failedRows.isNotEmpty()
    // Lazy items before the rails: hero (if any), the shortcut row, the "could not load" line (if any).
    val railsStart = (if (featured != null) 1 else 0) + 1 + (if (hasFailed) 1 else 0)

    val canWatch = featured != null && vm.playable(featured.id).value is UiState.Success
    val restore = rememberFocusRestore(
        remember {
            dest.focusKey?.takeIf { isKnownKey(it, content, canWatch) }
                ?: if (featured != null) HERO_INFO else browseKey(BrowseTabs.first())
        },
    )
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = dest.scrollIndex)
    val tracker = remember { FocusTracker() }
    val scope = rememberCoroutineScope()

    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, listState.firstVisibleItemIndex) }

    // Back from a detail page: make sure the rail with the remembered card is on screen.
    LaunchedEffect(Unit) {
        val rowIndex = content.rows.indexOfFirst { r -> r.items.any { cardKey(r.title, it.id) == restore.key } }
        if (rowIndex >= 0) listState.revealIndex(railsStart + rowIndex)
    }

    val openDetail: (Show, String) -> Unit = { show, key ->
        vm.saveFocus(key, listState.firstVisibleItemIndex)
        vm.push(Dest.Detail(show.id))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = KhTheme.spacing.safeVertical),
    ) {
        if (featured != null) {
            item(key = "hero", contentType = "hero") {
                FeaturedHero(
                    vm = vm,
                    show = featured,
                    restore = restore,
                    tracker = tracker,
                    onFocusTop = { scope.launch { listState.animateScrollToItem(0) } },
                    onInfo = { openDetail(featured, HERO_INFO) },
                    onWatch = { drama, playable ->
                        vm.saveFocus(HERO_WATCH, listState.firstVisibleItemIndex)
                        vm.play(drama.playerDest(playable.episode))
                    },
                )
            }
        }
        item(key = "browse", contentType = "browse") {
            BrowseRow(
                restore = restore,
                tracker = tracker,
                onOpen = { tab ->
                    vm.saveFocus(browseKey(tab), listState.firstVisibleItemIndex)
                    vm.selectTab(tab)
                },
            )
        }
        if (hasFailed) {
            item(key = "failed", contentType = "failed") {
                Row(
                    Modifier.padding(start = screenStart(), top = KhTheme.spacing.s, bottom = KhTheme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KhTheme.spacing.m),
                ) {
                    Text(
                        "Couldn't load: ${content.failedRows.joinToString()}",
                        style = KhTheme.typography.label,
                        color = KhColors.TextSecondary,
                    )
                    KhChip("Retry", onClick = { vm.retryHome() })
                }
            }
        }
        items(content.rows, key = { it.title }, contentType = { "rail" }) { row ->
            ShowRail(row, restore, tracker, openDetail)
        }
    }
}

private fun isKnownKey(key: String, content: HomeContent, canWatch: Boolean): Boolean = when {
    key == HERO_INFO -> content.featured != null
    key == HERO_WATCH -> content.featured != null && canWatch
    key.startsWith("browse:") -> true
    else -> content.rows.any { r -> r.items.any { cardKey(r.title, it.id) == key } }
}

@Composable
private fun FeaturedHero(
    vm: AppViewModel,
    show: Show,
    restore: FocusRestore,
    tracker: FocusTracker,
    onFocusTop: () -> Unit,
    onInfo: () -> Unit,
    onWatch: (Drama, Playable) -> Unit,
) {
    val detail by remember(show.id) { vm.detail(show.id) }.collectAsStateWithLifecycle()
    val playable by remember(show.id) { vm.playable(show.id) }.collectAsStateWithLifecycle()
    val drama = (detail as? UiState.Success)?.data
    val ready = (playable as? UiState.Success)?.data

    BoxWithConstraints(Modifier.fillMaxWidth().height(heroHeight())) {
        val narrow = maxWidth < 600.dp
        HeroBackdrop(url = drama?.thumbnail ?: show.thumbnail, title = show.title, modifier = Modifier.fillMaxSize())
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (narrow) 1f else 0.5f)
                .padding(
                    start = screenStart(),
                    end = if (narrow) KhTheme.spacing.safeHorizontal else KhTheme.spacing.l,
                    bottom = KhTheme.spacing.m,
                ),
            verticalArrangement = Arrangement.spacedBy(KhTheme.spacing.s),
        ) {
            Text(
                show.title?.trim().orEmpty(),
                style = if (narrow) KhTheme.typography.headline else KhTheme.typography.display,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (drama != null) {
                val meta = drama.metaParts(fullDate = false)
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString("  •  "),
                        style = KhTheme.typography.label,
                        color = KhColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val synopsis = drama.description?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                if (synopsis.isNotEmpty()) {
                    Text(
                        synopsis,
                        style = KhTheme.typography.body,
                        color = KhColors.TextPrimary.copy(alpha = 0.9f),
                        maxLines = if (narrow) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                Modifier.padding(top = KhTheme.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(KhTheme.spacing.s),
            ) {
                // Watch only appears once an episode that can really be played has been found.
                if (ready != null && drama != null) {
                    KhButton(
                        "Watch",
                        onClick = { onWatch(drama, ready) },
                        primary = true,
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier
                            .focusIfRestoring(restore, HERO_WATCH)
                            .onFocusChanged { if (it.isFocused) { tracker.key = HERO_WATCH; onFocusTop() } },
                    )
                }
                KhButton(
                    "More info",
                    onClick = onInfo,
                    icon = Icons.Filled.Info,
                    modifier = Modifier
                        .focusIfRestoring(restore, HERO_INFO)
                        .onFocusChanged { if (it.isFocused) { tracker.key = HERO_INFO; onFocusTop() } },
                )
            }
        }
    }
}

/** Shortcuts to the catalog screens, so they are easy to find without going to the rail. */
@Composable
private fun BrowseRow(restore: FocusRestore, tracker: FocusTracker, onOpen: (RootTab) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = screenStart(), end = KhTheme.spacing.safeHorizontal, top = KhTheme.spacing.s, bottom = KhTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KhTheme.spacing.s),
    ) {
        Text("Browse", style = KhTheme.typography.label, color = KhColors.TextSecondary, modifier = Modifier.padding(end = KhTheme.spacing.xs))
        BrowseTabs.forEach { tab ->
            val key = browseKey(tab)
            KhChip(
                text = tab.label,
                onClick = { onOpen(tab) },
                modifier = Modifier
                    .focusIfRestoring(restore, key)
                    .onFocusChanged { if (it.isFocused) tracker.key = key },
            )
        }
    }
}

@Composable
private fun ShowRail(
    row: HomeRow,
    restore: FocusRestore,
    tracker: FocusTracker,
    onOpen: (Show, String) -> Unit,
) {
    // If this rail holds the card to restore, start the rail scrolled to it.
    val initial = remember { row.items.indexOfFirst { cardKey(row.title, it.id) == restore.key }.coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val cardWidth = rowCardWidth()
    Column {
        Text(
            row.title,
            style = KhTheme.typography.title,
            modifier = Modifier.padding(start = screenStart(), top = KhTheme.spacing.s),
        )
        LazyRow(
            state = state,
            contentPadding = PaddingValues(
                start = screenStart(),
                end = KhTheme.spacing.safeHorizontal,
                top = KhTheme.spacing.focusBleed,
                bottom = KhTheme.spacing.focusBleed,
            ),
            horizontalArrangement = Arrangement.spacedBy(cardGap()),
        ) {
            items(row.items, key = { it.id }, contentType = { "card" }) { show ->
                val key = cardKey(row.title, show.id)
                ShowCard(
                    show = show,
                    onClick = { onOpen(show, key) },
                    modifier = Modifier.width(cardWidth).focusIfRestoring(restore, key),
                    onFocused = { tracker.key = key },
                )
            }
        }
    }
}
