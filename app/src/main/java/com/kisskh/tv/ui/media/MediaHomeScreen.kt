package com.kisskh.tv.ui.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKeys
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.api.media.TmdbTitleDetails
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.FixedPivotSpec
import com.kisskh.tv.ui.components.FocusRestore
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.KhActionButton
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.NoAutoScrollSpec
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.labelFor
import com.kisskh.tv.ui.shell.PhoneSearchButton
import com.kisskh.tv.ui.shell.PhoneWordmark
import com.kisskh.tv.ui.tabs
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val HERO_PLAY = "hero:play"
private const val HERO_INFO = "hero:info"
private fun cardKey(row: String, title: TmdbTitle) = "m:$row:${title.mediaType}:${title.id}"

/** Width of a TV row tile. 16:9, so 224x126 dp: about four and a half tiles across the screen. */
private val TvTileWidth = 224.dp

/** Width of a phone poster tile (2:3). */
private val PhonePosterWidth = 112.dp

/**
 * Home for CineWave and CineJoy, laid out like Netflix.
 *
 * - TV: the key art of the focused title fills the top of the screen behind the rows. The billboard
 *   names it with its logo, facts, genres and synopsis. Focus starts on Play. Rows scroll so the
 *   focused one always sits just under the billboard, and the tile under the same spot on screen.
 * - Phone: a top bar over a big poster card with Play and More info, then rows of posters.
 */
@Composable
fun MediaHomeScreen(vm: AppViewModel, library: Library, dest: Dest.Home) {
    val state by remember(library) { vm.media.home(library) }.collectAsStateWithLifecycle()
    LaunchedEffect(library) { vm.media.refreshLocal(library) }
    val tv = KhTheme.formFactor == FormFactor.TV
    when (val s = state) {
        UiState.Loading -> LoadingState(message = "Loading ${library.label}")
        is UiState.Error -> ErrorState(s.message, onRetry = { vm.media.retryHome(library) }, autoFocus = true)
        is UiState.Empty -> ErrorState(s.message ?: "Nothing to show right now.", onRetry = { vm.media.retryHome(library) })
        is UiState.Success -> if (tv) TvHome(vm, library, dest, s.data) else PhoneHome(vm, library, s.data)
    }
}

/** The title the billboard opens on: the first one TMDB trends, not the viewer's own history. */
private fun featuredOf(home: MediaHome): Pair<MediaRow, TmdbTitle>? {
    val row = home.rows.firstOrNull { !it.local && it.items.isNotEmpty() } ?: home.rows.firstOrNull { it.items.isNotEmpty() }
    val title = row?.items?.firstOrNull() ?: return null
    return row to title
}

/** Details for the billboard (logo, genres, length). Null until loaded; a failure just leaves the basics. */
@Composable
private fun rememberDetails(vm: AppViewModel, kind: MediaKind?, id: Int?): TmdbTitleDetails? {
    val flow = remember(kind, id) {
        if (kind == null || id == null) MutableStateFlow<UiState<TmdbTitleDetails>>(UiState.Loading) else vm.media.detail(kind, id)
    }
    val state by flow.collectAsStateWithLifecycle()
    return (state as? UiState.Success)?.data
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvHome(vm: AppViewModel, library: Library, dest: Dest.Home, home: MediaHome) {
    val featured = remember(home) { featuredOf(home) }
    val featuredKind = featured?.let { kindFor(it.first.title, it.second) }
    // The tile focus comes back to, with its kind: a movie and a series can share a TMDB id.
    val restored = remember {
        home.rows.firstNotNullOfOrNull { row ->
            row.items.firstOrNull { cardKey(row.title, it) == dest.focusKey }?.let { it to kindFor(row.title, it) }
        }
    }
    var browsingRows by remember { mutableStateOf(restored != null) }
    var focused by remember { mutableStateOf(restored?.first ?: featured?.second) }
    var focusedKind by remember { mutableStateOf(restored?.second ?: featuredKind) }
    var billboard by remember { mutableStateOf(focused) }
    var billboardKind by remember { mutableStateOf(focusedKind) }
    LaunchedEffect(focused) {
        val target = focused
        if (target == billboard) return@LaunchedEffect
        // Back on the featured title: change at once. Moving across tiles waits out the D-pad.
        if (target != featured?.second) delay(KhMotion.BillboardSettleMillis)
        billboard = target
        billboardKind = focusedKind
    }
    val details = rememberDetails(vm, billboardKind, billboard?.id)?.takeIf { it.title.id == billboard?.id }

    val knownKeys = remember(home) {
        buildSet {
            home.rows.forEach { row -> row.items.forEach { add(cardKey(row.title, it)) } }
            if (featured != null) {
                add(HERO_PLAY)
                add(HERO_INFO)
            }
        }
    }
    val firstCard = home.rows.firstOrNull()?.let { row -> row.items.firstOrNull()?.let { cardKey(row.title, it) } }
    val restore = rememberFocusRestore(
        remember { dest.focusKey?.takeIf { it in knownKeys } ?: if (featured != null) HERO_PLAY else firstCard },
    )
    val tracker = remember { FocusTracker() }
    val rows = home.rows
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = dest.scrollIndex.coerceIn(0, rows.lastIndex.coerceAtLeast(0)))
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, listState.firstVisibleItemIndex) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val index = rows.indexOfFirst { row -> row.items.any { cardKey(row.title, it) == restore.key } }
        if (index >= 0) listState.revealIndex(index)
    }
    fun snapTo(index: Int) {
        if (listState.firstVisibleItemIndex == index && listState.firstVisibleItemScrollOffset == 0) return
        scope.launch { listState.animateScrollToItem(index) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        HeroArt(
            url = (details?.title ?: billboard)?.hero,
            modifier = Modifier.fillMaxWidth().height(screenHeight * 0.78f),
        )
        Column(Modifier.fillMaxSize()) {
            Billboard(
                title = billboard,
                details = details,
                library = library,
                showActions = !browsingRows && featured != null,
                playLabel = featured?.let { playLabel(vm.savedEntry(library, it.second), featuredKind?.isMovie != false) } ?: "Play",
                restore = restore,
                tracker = tracker,
                onHeroFocus = {
                    browsingRows = false
                    focusedKind = featuredKind
                    focused = featured?.second
                },
                onPlay = {
                    val (_, title) = featured ?: return@Billboard
                    vm.saveFocus(HERO_PLAY, 0)
                    vm.playOrResume(library, featuredKind ?: MediaKind.MOVIE, title)
                },
                onInfo = {
                    val (row, title) = featured ?: return@Billboard
                    vm.saveFocus(HERO_INFO, 0)
                    vm.openTitle(title, kindFor(row.title, title))
                },
                modifier = Modifier.fillMaxWidth().height(screenHeight * 0.56f),
            )
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScrollSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = screenHeight * 0.40f),
                ) {
                    items(rows.size, key = { rows[it].title }) { index ->
                        val row = rows[index]
                        TvRow(
                            vm = vm,
                            library = library,
                            row = row,
                            restore = restore,
                            onFocused = { title ->
                                browsingRows = true
                                focusedKind = kindFor(row.title, title)
                                focused = title
                                tracker.key = cardKey(row.title, title)
                                snapTo(index)
                            },
                            onOpen = { title, entryIndex ->
                                vm.saveFocus(cardKey(row.title, title), listState.firstVisibleItemIndex)
                                val entry = row.resumes?.getOrNull(entryIndex)
                                if (entry != null && entry.resumable) vm.resumeEntry(entry) else vm.openTitle(title, kindFor(row.title, title))
                            },
                        )
                    }
                    if (library == Library.CINEJOY) {
                        // Optional: CineJoy plays without it. Last, so it never sits between the viewer and the rows.
                        item(key = "token") {
                            KhChip(
                                "Febbox token (optional)",
                                onClick = { vm.push(Dest.Token()) },
                                outlined = true,
                                modifier = Modifier
                                    .padding(start = screenStart(), top = 8.dp, bottom = 16.dp)
                                    .onFocusChanged { if (it.isFocused) snapTo(rows.size) },
                            )
                        }
                    }
                    if (home.failed.isNotEmpty()) {
                        item(key = "failed") {
                            Row(
                                Modifier.padding(start = screenStart(), top = 8.dp, bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "Couldn't load ${home.failed.joinToString(", ")}.",
                                    style = KhTheme.typography.body,
                                    color = KhColors.TextSecondary,
                                )
                                KhButton("Retry", onClick = { vm.media.retryHome(library) }, primary = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Billboard(
    title: TmdbTitle?,
    details: TmdbTitleDetails?,
    library: Library,
    showActions: Boolean,
    playLabel: String,
    restore: FocusRestore,
    tracker: FocusTracker,
    onHeroFocus: () -> Unit,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionsAlpha by animateFloatAsState(
        targetValue = if (showActions) 1f else 0f,
        animationSpec = tween(KhMotion.FocusMillis, easing = KhMotion.EaseOutStrong),
        label = "heroActions",
    )
    // Saved titles (Continue, Recent) only keep a name and a poster; the details fill in the rest.
    val full = details?.title ?: title
    Box(modifier) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = screenStart(), bottom = 12.dp, end = 24.dp)
                .widthIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TitleLogo(
                logo = details?.logo,
                name = title?.name ?: library.label,
                height = 84.dp,
                style = heroTitleStyle(),
            )
            MetaLine(
                year = full?.year,
                certification = details?.certification,
                length = details?.let { lengthOf(it) },
                vote = full?.voteText(),
                modifier = Modifier.padding(top = 2.dp),
            )
            val overview = full?.overview?.trim().orEmpty()
            if (overview.isNotEmpty()) {
                Text(
                    overview,
                    style = KhTheme.typography.synopsis,
                    color = KhColors.TextPrimary.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GenreLine(details?.genres.orEmpty())
            if (title != null) {
                Row(
                    Modifier.graphicsLayer { alpha = actionsAlpha }.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KhActionButton(
                        text = playLabel,
                        onClick = onPlay,
                        primary = true,
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier
                            .widthIn(min = 140.dp).width(IntrinsicSize.Max)
                            .focusIfRestoring(restore, HERO_PLAY)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    tracker.key = HERO_PLAY
                                    onHeroFocus()
                                }
                            },
                    )
                    KhActionButton(
                        text = "More info",
                        onClick = onInfo,
                        icon = Icons.Filled.Info,
                        modifier = Modifier
                            .widthIn(min = 140.dp).width(IntrinsicSize.Max)
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

/** "2h 19m" for a movie, "3 Seasons" for a series. */
fun lengthOf(details: TmdbTitleDetails): String? {
    if (details.title.mediaType == "movie") return runtimeText(details.runtime)
    val seasons = details.seasons.count { it.number >= 1 }
    return when (seasons) {
        0 -> null
        1 -> "${details.seasons.first { it.number >= 1 }.episodeCount} Episodes"
        else -> "$seasons Seasons"
    }
}

private fun progressOf(vm: AppViewModel, library: Library, row: MediaRow, index: Int, title: TmdbTitle): Float? {
    val entry = row.resumes?.getOrNull(index)
    return when {
        entry != null && entry.durationMs > 0 && entry.positionMs > 0 -> entry.positionMs.toFloat() / entry.durationMs
        title.mediaType == "movie" -> vm.media.progress.fraction(MediaKeys.movie(library.key, title.id))
        else -> vm.media.progress.continuing(library.key)
            .firstOrNull { it.id == title.id && it.kind == "tv" && it.durationMs > 0 }
            ?.let { it.positionMs.toFloat() / it.durationMs }
    }
}

/** Netflix calls it "Continue Watching"; the stored row name stays "Continue". */
private fun rowLabel(row: MediaRow, library: Library): String = when (row.title) {
    "Continue" -> "Continue Watching"
    "Recent" -> "Watched Recently"
    "Trending" -> "Trending on ${library.label}"
    else -> row.title.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvRow(
    vm: AppViewModel,
    library: Library,
    row: MediaRow,
    restore: FocusRestore,
    onFocused: (TmdbTitle) -> Unit,
    onOpen: (TmdbTitle, Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            rowLabel(row, library),
            style = KhTheme.typography.rowTitle,
            color = KhColors.TextPrimary,
            modifier = Modifier.padding(start = screenStart(), bottom = 6.dp).semantics { heading() },
        )
        val initial = remember { row.items.indexOfFirst { cardKey(row.title, it) == restore.key }.coerceAtLeast(0) }
        val rowState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val start = screenStart()
            val startPx = with(LocalDensity.current) { start.toPx() }
            val endPad = (maxWidth - TvTileWidth - start).coerceAtLeast(0.dp)
            val spec = remember(startPx) { FixedPivotSpec(startPx) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(start = start, end = endPad, top = 4.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(row.items.size, key = { "${row.items[it].mediaType}:${row.items[it].id}" }) { index ->
                        val title = row.items[index]
                        MediaCard(
                            title = title,
                            progress = progressOf(vm, library, row, index, title),
                            onClick = { onOpen(title, index) },
                            onFocused = { onFocused(title) },
                            modifier = Modifier.width(TvTileWidth).focusIfRestoring(restore, cardKey(row.title, title)),
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ phone

@Composable
private fun PhoneHome(vm: AppViewModel, library: Library, home: MediaHome) {
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12 } }
    val barAlpha by animateFloatAsState(
        targetValue = if (scrolled) 0.94f else 0f,
        animationSpec = tween(150, easing = KhMotion.EaseOutStrong),
        label = "phoneBar",
    )
    Box(Modifier.fillMaxSize()) {
        PhoneRows(vm, library, home, listState)
        PhoneTopBar(
            vm = vm,
            library = library,
            alpha = barAlpha,
        )
    }
}

@Composable
private fun PhoneTopBar(vm: AppViewModel, library: Library, alpha: Float) {
    val pills = library.tabs().filter { it != RootTab.HOME && it != RootTab.SEARCH }
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
            Text(
                library.label,
                style = KhTheme.typography.label,
                color = KhColors.TextSecondary,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            PhoneSearchButton(onClick = { vm.selectTab(RootTab.SEARCH) })
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pills.forEach { tab ->
                KhChip(text = tab.labelFor(library), onClick = { vm.selectTab(tab) }, outlined = true)
            }
        }
    }
}

@Composable
private fun PhoneRows(vm: AppViewModel, library: Library, home: MediaHome, listState: LazyListState) {
    val featured = remember(home) { featuredOf(home) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "hero") {
            if (featured != null) PhoneHero(vm, library, featured.first, featured.second) else Spacer(Modifier.height(120.dp))
        }
        if (library == Library.CINEJOY) {
            item(key = "token") {
                KhChip(
                    "Febbox token (optional)",
                    onClick = { vm.push(Dest.Token()) },
                    outlined = true,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                )
            }
        }
        items(home.rows, key = { it.title }) { row ->
            Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text(
                    rowLabel(row, library),
                    style = KhTheme.typography.rowTitle,
                    color = KhColors.TextPrimary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() },
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(row.items.size, key = { "${row.items[it].mediaType}:${row.items[it].id}" }) { index ->
                        val title = row.items[index]
                        val entry = row.resumes?.getOrNull(index)
                        MediaCard(
                            title = title,
                            poster = true,
                            progress = progressOf(vm, library, row, index, title),
                            onClick = {
                                if (entry != null && entry.resumable) vm.resumeEntry(entry) else vm.openTitle(title, kindFor(row.title, title))
                            },
                            modifier = Modifier.width(PhonePosterWidth),
                        )
                    }
                }
            }
        }
        if (home.failed.isNotEmpty()) {
            item(key = "failed") {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Couldn't load ${home.failed.joinToString(", ")}.", style = KhTheme.typography.caption, color = KhColors.TextSecondary)
                    KhChip("Retry", onClick = { vm.media.retryHome(library) }, outlined = true)
                }
            }
        }
    }
}

@Composable
private fun PhoneHero(vm: AppViewModel, library: Library, row: MediaRow, title: TmdbTitle) {
    val kind = kindFor(row.title, title)
    val details = rememberDetails(vm, kind, title.id)
    val shape = RoundedCornerShape(12.dp)
    val saved = vm.savedEntry(library, title)
    Column(
        Modifier.fillMaxWidth().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.84f)
                .aspectRatio(4f / 5f)
                .clip(shape)
                .border(1.dp, KhColors.HeroBorder, shape)
                .clickable { vm.openTitle(title, kind) },
        ) {
            Artwork(title.poster ?: title.artwork, title.name, Modifier.fillMaxSize(), fadeMillis = 180, titleOnFailure = false)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.75f),
                            1f to Color.Black.copy(alpha = 0.96f),
                        ),
                    ),
            )
            Column(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The poster's own title sits under the buttons, so the logo goes above them.
                TitleLogo(
                    logo = details?.logo,
                    name = title.name,
                    height = 64.dp,
                    style = KhTheme.typography.headline,
                    maxWidth = 260.dp,
                    align = Alignment.BottomCenter,
                )
                GenreLine(details?.genres.orEmpty())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KhActionButton(
                        text = playLabel(saved, kind.isMovie),
                        onClick = { vm.playOrResume(library, kind, title) },
                        primary = true,
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.weight(1f),
                    )
                    KhActionButton(
                        text = "More info",
                        onClick = { vm.openTitle(title, kind) },
                        icon = Icons.Filled.Info,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
