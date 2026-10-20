@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kisskh.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Show
import com.kisskh.tv.api.WatchEntry
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.HomeContent
import com.kisskh.tv.ui.HomeRow
import com.kisskh.tv.ui.Playable
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.billboardLine
import com.kisskh.tv.ui.components.ArtCard
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhCardSize
import com.kisskh.tv.ui.components.FixedPivotSpec
import com.kisskh.tv.ui.components.FocusRestore
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.KhActionButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.NoAutoScrollSpec
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.TitleCard
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.fallbackLine
import com.kisskh.tv.ui.playerDest
import com.kisskh.tv.ui.shell.PhoneSearchButton
import com.kisskh.tv.ui.shell.PhoneWordmark
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhGradients
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HERO_PLAY = "hero:play"
private const val HERO_INFO = "hero:info"
private fun cardKey(row: String, id: Int) = "home:$row:$id"

private val PhonePills = listOf(RootTab.SERIES, RootTab.MOVIES, RootTab.COUNTRIES)

/**
 * Home. On TV the top half is a billboard of whatever card has focus. On a phone it is a fixed
 * hero card, category pills and rows with titles under the art. Nothing plays or rotates by itself.
 */
@Composable
fun HomeScreen(vm: AppViewModel, dest: Dest.Home) {
    val state by vm.home.collectAsStateWithLifecycle()
    val tv = KhTheme.formFactor == FormFactor.TV
    if (tv) {
        when (val s = state) {
            UiState.Loading -> LoadingState(message = "Loading")
            is UiState.Error -> ErrorState(s.message, onRetry = { vm.retryHome() }, autoFocus = true)
            is UiState.Empty -> ErrorState(s.message ?: "Nothing to show right now.", onRetry = { vm.retryHome() }, autoFocus = true)
            is UiState.Success -> TvHome(vm, dest, s.data)
        }
    } else {
        PhoneHome(vm, state)
    }
}

private sealed interface Rail {
    val title: String
    data class Feed(val row: HomeRow) : Rail {
        override val title: String get() = row.title
    }
    data class Continue(val entries: List<WatchEntry>) : Rail {
        override val title: String get() = HomeRails.CONTINUE
    }
}

private fun railsOf(content: HomeContent, watching: List<WatchEntry>): List<Rail> = buildList {
    if (watching.isNotEmpty()) add(Rail.Continue(watching))
    content.rows.forEach { add(Rail.Feed(it)) }
}

private fun WatchEntry.asShow() = Show(id = dramaId, title = title, thumbnail = thumbnail)

@Composable
private fun TvHome(vm: AppViewModel, dest: Dest.Home, content: HomeContent) {
    val watching = remember { vm.repo.history.recent() }
    val rails = remember(content, watching) { railsOf(content, watching) }
    val featured = content.featured ?: content.rows.firstOrNull()?.items?.firstOrNull()
    val restored = remember {
        rails.firstNotNullOfOrNull { rail ->
            when (rail) {
                is Rail.Continue -> rail.entries.firstOrNull { cardKey(rail.title, it.dramaId) == dest.focusKey }?.asShow()
                is Rail.Feed -> rail.row.items.firstOrNull { cardKey(rail.title, it.id) == dest.focusKey }
            }
        }
    }
    var browsingRows by remember { mutableStateOf(restored != null) }
    var focusedShow by remember { mutableStateOf(restored ?: featured) }
    var billboard by remember { mutableStateOf(focusedShow) }
    LaunchedEffect(focusedShow?.id) {
        val target = focusedShow
        if (target?.id == billboard?.id) return@LaunchedEffect
        // Back to the featured title: update at once. Moving across cards waits out the D-pad.
        if (target?.id != featured?.id) delay(KhMotion.BillboardSettleMillis)
        billboard = target
    }

    val featuredPlayable by remember(featured?.id) {
        val id = featured?.id
        if (id == null) MutableStateFlow(UiState.Loading as UiState<Playable>) else vm.playable(id)
    }.collectAsStateWithLifecycle()
    val knownWatch = featured != null && featuredPlayable is UiState.Success
    val restore = rememberFocusRestore(
        remember {
            dest.focusKey?.takeIf { isKnownKey(it, rails, featured, knownWatch) }
                ?: if (featured != null) HERO_INFO else rails.firstOrNull()?.let { firstKey(it) }
        },
    )
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = dest.scrollIndex.coerceAtMost(rails.lastIndex.coerceAtLeast(0)))
    val tracker = remember { FocusTracker() }
    val scope = rememberCoroutineScope()
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, listState.firstVisibleItemIndex) }

    LaunchedEffect(Unit) {
        val index = rails.indexOfFirst { rail ->
            when (rail) {
                is Rail.Continue -> rail.entries.any { cardKey(rail.title, it.dramaId) == restore.key }
                is Rail.Feed -> rail.row.items.any { cardKey(rail.title, it.id) == restore.key }
            }
        }
        if (index >= 0) listState.revealIndex(index)
    }

    fun snapTo(index: Int) {
        if (listState.firstVisibleItemIndex == index && listState.firstVisibleItemScrollOffset == 0) return
        scope.launch { listState.animateScrollToItem(index) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        Column(Modifier.fillMaxSize()) {
            if (billboard != null || featured != null) {
                TvBillboard(
                    vm = vm,
                    show = billboard ?: featured!!,
                    featured = featured,
                    showActions = !browsingRows && featured != null,
                    restore = restore,
                    tracker = tracker,
                    onHeroFocus = {
                        browsingRows = false
                        focusedShow = featured
                    },
                    onPlay = { drama, playable ->
                        vm.saveFocus(HERO_PLAY, 0)
                        vm.play(drama.playerDest(playable.episode), playable.url)
                    },
                    onInfo = { show ->
                        vm.saveFocus(HERO_INFO, 0)
                        vm.push(Dest.Detail(show.id))
                    },
                    modifier = Modifier.fillMaxWidth().height(screenHeight * 0.52f),
                )
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScrollSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = screenHeight * 0.40f),
                ) {
                    items(rails.size, key = { rails[it].title }) { index ->
                        val rail = rails[index]
                        TvRail(
                            rail = rail,
                            restore = restore,
                            onCardFocused = { show ->
                                browsingRows = true
                                focusedShow = show
                                tracker.key = cardKey(rail.title, show.id)
                                snapTo(index)
                            },
                            onOpenShow = { show ->
                                val key = cardKey(rail.title, show.id)
                                vm.saveFocus(key, listState.firstVisibleItemIndex)
                                vm.push(Dest.Detail(show.id))
                            },
                            onResume = { entry ->
                                val key = cardKey(rail.title, entry.dramaId)
                                vm.saveFocus(key, listState.firstVisibleItemIndex)
                                vm.play(
                                    Dest.Player(
                                        episodeId = entry.episodeId,
                                        dramaId = entry.dramaId,
                                        title = entry.title.orEmpty(),
                                        label = "Episode ${com.kisskh.tv.ui.components.formatEpisodeNumber(entry.episodeNumber)}",
                                        episodeNumber = entry.episodeNumber,
                                        thumbnail = entry.thumbnail,
                                    ),
                                )
                            },
                        )
                    }
                    if (content.failedRows.isNotEmpty()) {
                        item(key = "failed") {
                            Row(
                                Modifier.padding(start = screenStart(), top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                }
            }
        }
    }
}

private fun firstKey(rail: Rail): String = when (rail) {
    is Rail.Continue -> rail.entries.firstOrNull()?.let { cardKey(rail.title, it.dramaId) } ?: HERO_INFO
    is Rail.Feed -> rail.row.items.firstOrNull()?.let { cardKey(rail.title, it.id) } ?: HERO_INFO
}

private fun isKnownKey(key: String, rails: List<Rail>, featured: Show?, canWatch: Boolean): Boolean = when {
    key == HERO_INFO -> featured != null
    key == HERO_PLAY -> featured != null && canWatch
    else -> rails.any { rail ->
        when (rail) {
            is Rail.Continue -> rail.entries.any { cardKey(rail.title, it.dramaId) == key }
            is Rail.Feed -> rail.row.items.any { cardKey(rail.title, it.id) == key }
        }
    }
}

@Composable
private fun TvBillboard(
    vm: AppViewModel,
    show: Show,
    featured: Show?,
    showActions: Boolean,
    restore: FocusRestore,
    tracker: FocusTracker,
    onHeroFocus: () -> Unit,
    onPlay: (Drama, Playable) -> Unit,
    onInfo: (Show) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drama by remember(show.id) { mutableStateOf<Drama?>(null) }
    LaunchedEffect(show.id) {
        vm.detail(show.id).collect { state ->
            drama = (state as? UiState.Success)?.data
        }
    }
    var playable by remember(featured?.id) { mutableStateOf<UiState<Playable>?>(null) }
    LaunchedEffect(featured?.id) {
        val id = featured?.id
        if (id == null) {
            playable = null
        } else {
            vm.playable(id).collect { playable = it }
        }
    }
    val ready = (playable as? UiState.Success)?.data
    val actionsAlpha by animateFloatAsState(
        targetValue = if (showActions) 1f else 0f,
        animationSpec = tween(KhMotion.FocusMillis, easing = KhMotion.EaseOutStrong),
        label = "heroActions",
    )
    Box(modifier) {
        Crossfade(
            targetState = show.id to show.thumbnail,
            animationSpec = KhMotion.crossfade(),
            label = "billboardArt",
        ) { (_, url) ->
            Box(Modifier.fillMaxSize().background(KhColors.Background)) {
                Artwork(
                    url = url,
                    title = show.title,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.70f),
                    fitPortrait = true,
                    fadeMillis = 0,
                    titleOnFailure = false,
                )
                Box(Modifier.fillMaxSize().background(KhGradients.LeftFade))
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.50f)
                        .background(KhGradients.BottomFade),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = screenStart(), bottom = 16.dp, end = 24.dp)
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                show.title?.trim().orEmpty().ifEmpty { "Untitled" },
                fontSize = 46.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = drama?.billboardLine()?.takeIf { it.isNotEmpty() } ?: show.fallbackLine()
            if (!meta.isNullOrEmpty()) {
                Text(
                    meta,
                    style = KhTheme.typography.meta,
                    color = KhColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val synopsis = drama?.description?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            if (synopsis.isNotEmpty()) {
                Text(
                    synopsis,
                    style = KhTheme.typography.synopsis,
                    color = KhColors.TextPrimary.copy(alpha = 0.92f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (featured != null) {
                Row(
                    Modifier.graphicsLayer { alpha = actionsAlpha }.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val playDrama = drama?.takeIf { it.id == featured.id }
                    if (ready != null && playDrama != null) {
                        KhActionButton(
                            text = "Play",
                            onClick = { onPlay(playDrama, ready) },
                            primary = true,
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier
                                .focusIfRestoring(restore, HERO_PLAY)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        tracker.key = HERO_PLAY
                                        onHeroFocus()
                                    }
                                },
                        )
                    }
                    KhActionButton(
                        text = "More info",
                        onClick = { onInfo(featured) },
                        modifier = Modifier
                            .focusIfRestoring(restore, HERO_INFO)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    tracker.key = HERO_INFO
                                    onHeroFocus()
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvRail(
    rail: Rail,
    restore: FocusRestore,
    onCardFocused: (Show) -> Unit,
    onOpenShow: (Show) -> Unit,
    onResume: (WatchEntry) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            rail.title,
            style = KhTheme.typography.rowTitle,
            modifier = Modifier.padding(start = screenStart(), bottom = 8.dp),
        )
        when (rail) {
            is Rail.Feed -> PivotCards(
                shows = rail.row.items,
                restore = restore,
                keyOf = { cardKey(rail.title, it.id) },
                onFocused = onCardFocused,
                onClick = onOpenShow,
            )
            is Rail.Continue -> PivotCards(
                shows = rail.entries.map { it.asShow() },
                restore = restore,
                keyOf = { cardKey(rail.title, it.id) },
                progressOf = { show -> rail.entries.firstOrNull { it.dramaId == show.id }?.progress },
                onFocused = onCardFocused,
                onClick = { show ->
                    val entry = rail.entries.first { it.dramaId == show.id }
                    onResume(entry)
                },
            )
        }
    }
}

@Composable
private fun PivotCards(
    shows: List<Show>,
    restore: FocusRestore,
    keyOf: (Show) -> String,
    onFocused: (Show) -> Unit,
    onClick: (Show) -> Unit,
    progressOf: (Show) -> Float? = { null },
) {
    val initial = remember { shows.indexOfFirst { keyOf(it) == restore.key }.coerceAtLeast(0) }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val start = screenStart()
        val startPx = with(LocalDensity.current) { start.toPx() }
        val endPad = (maxWidth - KhCardSize.TvWidth - start).coerceAtLeast(0.dp)
        val spec = remember(startPx) { FixedPivotSpec(startPx) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(start = start, end = endPad, top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shows, key = { it.id }) { show ->
                    val key = keyOf(show)
                    ArtCard(
                        show = show,
                        onClick = { onClick(show) },
                        progress = progressOf(show),
                        modifier = Modifier.width(KhCardSize.TvWidth).focusIfRestoring(restore, key),
                        onFocused = { onFocused(show) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneHome(vm: AppViewModel, state: UiState<HomeContent>) {
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }
    val barAlpha by animateFloatAsState(
        targetValue = if (scrolled) 0.9f else 0f,
        animationSpec = tween(150, easing = KhMotion.EaseOutStrong),
        label = "phoneBar",
    )
    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            UiState.Loading -> LoadingState(message = "Loading")
            is UiState.Error -> ErrorState(s.message, onRetry = { vm.retryHome() })
            is UiState.Empty -> ErrorState(s.message ?: "Nothing to show right now.", onRetry = { vm.retryHome() })
            is UiState.Success -> PhoneRows(vm, s.data, listState)
        }
        PhoneTopBar(
            alpha = barAlpha,
            onSearch = { vm.selectTab(RootTab.SEARCH) },
            onOpen = { vm.selectTab(it) },
        )
    }
}

@Composable
private fun PhoneTopBar(alpha: Float, onSearch: () -> Unit, onOpen: (RootTab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(KhColors.Background.copy(alpha = alpha))
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhoneWordmark()
            Spacer(Modifier.weight(1f))
            PhoneSearchButton(onClick = onSearch)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhonePills.forEach { tab ->
                KhChip(text = tab.label, onClick = { onOpen(tab) }, outlined = true)
            }
        }
    }
}

@Composable
private fun PhoneRows(
    vm: AppViewModel,
    content: HomeContent,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val watching = remember { vm.repo.history.recent() }
    val featured = content.featured ?: content.rows.firstOrNull()?.items?.firstOrNull()
    val rails = remember(content, watching) { railsOf(content, watching) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "hero") {
            if (featured != null) PhoneHero(vm, featured) else Spacer(Modifier.height(96.dp))
        }
        if (content.failedRows.isNotEmpty()) {
            item(key = "failed") {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Couldn't load: ${content.failedRows.joinToString()}", style = KhTheme.typography.caption, color = KhColors.TextSecondary)
                    KhChip("Retry", onClick = { vm.retryHome() }, outlined = true)
                }
            }
        }
        items(rails, key = { it.title }) { rail ->
            PhoneRail(vm, rail)
        }
    }
}

@Composable
private fun PhoneHero(vm: AppViewModel, show: Show) {
    var drama by remember(show.id) { mutableStateOf<Drama?>(null) }
    LaunchedEffect(show.id) {
        vm.detail(show.id).collect { state ->
            drama = (state as? UiState.Success)?.data
        }
    }
    var playable by remember(show.id) { mutableStateOf<UiState<Playable>?>(null) }
    LaunchedEffect(show.id) {
        vm.playable(show.id).collect { playable = it }
    }
    val ready = (playable as? UiState.Success)?.data
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val cardWidth = maxWidth * 0.80f
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .width(cardWidth)
                    .aspectRatio(4f / 5f)
                    .clip(shape)
                    .border(1.dp, KhColors.HeroBorder, shape),
            ) {
                Artwork(
                    url = drama?.thumbnail ?: show.thumbnail,
                    title = show.title,
                    modifier = Modifier.fillMaxSize(),
                    fadeMillis = 180,
                    titleOnFailure = false,
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(88.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.42f)
                        .background(KhGradients.BottomFade),
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        show.title?.trim().orEmpty().ifEmpty { "Untitled" },
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = drama?.billboardLine()?.takeIf { it.isNotEmpty() } ?: show.fallbackLine()
                    if (!meta.isNullOrEmpty()) {
                        Text(
                            meta,
                            style = KhTheme.typography.caption,
                            color = KhColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth(0.80f)
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val playDrama = drama
            if (ready != null && playDrama != null) {
                KhActionButton(
                    text = "Play",
                    onClick = { vm.play(playDrama.playerDest(ready.episode), ready.url) },
                    primary = true,
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier.weight(1f),
                )
            }
            KhActionButton(
                text = "More info",
                onClick = { vm.push(Dest.Detail(show.id)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PhoneRail(vm: AppViewModel, rail: Rail) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            rail.title,
            style = KhTheme.typography.rowTitle,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (rail) {
                is Rail.Feed -> items(rail.row.items, key = { it.id }) { show ->
                    TitleCard(
                        show = show,
                        onClick = { vm.push(Dest.Detail(show.id)) },
                        modifier = Modifier.width(KhCardSize.PhoneWidth),
                    )
                }
                is Rail.Continue -> items(rail.entries, key = { it.dramaId }) { entry ->
                    TitleCard(
                        show = entry.asShow(),
                        onClick = {
                            vm.play(
                                Dest.Player(
                                    episodeId = entry.episodeId,
                                    dramaId = entry.dramaId,
                                    title = entry.title.orEmpty(),
                                    label = "Episode ${com.kisskh.tv.ui.components.formatEpisodeNumber(entry.episodeNumber)}",
                                    episodeNumber = entry.episodeNumber,
                                    thumbnail = entry.thumbnail,
                                ),
                            )
                        },
                        progress = entry.progress,
                        modifier = Modifier.width(KhCardSize.PhoneWidth),
                    )
                }
            }
        }
    }
}
