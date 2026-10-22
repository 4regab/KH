@file:OptIn(ExperimentalFoundationApi::class)

package com.kisskh.tv.ui.shelf

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.kisskh.tv.api.MY_LIST_ROW
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.CONTINUE_ROW
import com.kisskh.tv.api.source.ShelfRow
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.FixedPivotSpec
import com.kisskh.tv.ui.components.FocusRestore
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.KhActionButton
import com.kisskh.tv.ui.components.NoAutoScrollSpec
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.edge
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.isTv
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull

/** The share of the TV Home width the billboard picture takes, on the right. */
private const val BillboardArtShare = 0.7f

@Composable
fun ShelfHome(vm: AppViewModel, library: Library) {
    val state by remember(library) { vm.shelf.home(library) }.collectAsStateWithLifecycle()
    // Coming back from a player: the Continue row must show what was just watched.
    // The same goes for My List and Watched, which can change on a title page.
    val savedVersion by vm.saved.version.collectAsStateWithLifecycle()
    LaunchedEffect(library, savedVersion) { vm.shelf.refreshContinue(library) }
    val tv = isTv()
    val list = rememberSavedList(vm, "${library.key}:" + if (tv) "tvhome" else "home")
    val solid by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120 } }
    val barAlpha by animateFloatAsState(if (solid) 1f else 0f, tween(KhMotion.PopoverMillis, easing = KhMotion.EaseOutStrong), label = "bar")
    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            UiState.Loading -> HomeSkeleton(library.shapeHint())
            is UiState.Error -> ErrorState(
                "Couldn't load ${library.label}. ${s.message}",
                onRetry = { vm.shelf.retryHome(library) },
                autoFocus = true,
            )
            is UiState.Empty -> EmptyState(s.message ?: "Nothing to show right now.")
            is UiState.Success -> if (tv) TvHome(vm, library, s.data, list) else PhoneHome(vm, library, s.data, list)
        }
        if (!tv) ShelfTopBar(vm, library, barAlpha)
    }
}

/**
 * The title the big artwork at the top shows: the first title of the first row that is not Continue
 * or My List. A [CardShape.POSTER] Home wants a title with a real poster, a landscape one a backdrop.
 */
internal fun pickHero(rows: List<ShelfRow>, shape: CardShape = CardShape.LANDSCAPE): TitleCard? {
    val pool = rows.filter { it.title != CONTINUE_ROW && it.title != MY_LIST_ROW }.flatMap { it.items }
    val wanted = if (shape == CardShape.POSTER) pool.firstOrNull { !it.poster.isNullOrBlank() } else pool.firstOrNull { !it.backdrop.isNullOrBlank() }
    return wanted ?: pool.firstOrNull { it.artwork != null }
}

// ---------------------------------------------------------------------------------------------
// Focus keys. The same title can sit in two rows (Continue Watching and a trending row), so a key is
// the row's title plus the card's list key.

internal fun homeFocusKey(rowTitle: String, card: TitleCard): String = rowTitle + "|" + card.listKey()

/** [saved] if some row still has that card, otherwise the first card of the first row (null if there is none). */
internal fun startFocusKey(rows: List<ShelfRow>, saved: String?): String? {
    val keys = rows.flatMap { row -> row.items.map { homeFocusKey(row.title, it) } }
    return saved?.takeIf { it in keys } ?: keys.firstOrNull()
}

internal fun cardForFocusKey(rows: List<ShelfRow>, key: String?): TitleCard? {
    if (key == null) return null
    return rows.firstNotNullOfOrNull { row -> row.items.firstOrNull { homeFocusKey(row.title, it) == key } }
}

// ---------------------------------------------------------------------------------------------
// TV Home: the Netflix billboard.

/**
 * A full-bleed billboard on top that follows the card the focus is on, and rows underneath.
 *
 * - The billboard waits [KhMotion.BillboardSettleMillis] after focus stops moving, loads the next
 *   picture, and only then crossfades (250 ms). Fast D-pad scrolling cancels the wait, so it never thrashes.
 * - The focused row is pinned to a fixed spot under the billboard text (the list scrolls it there),
 *   and inside a row the focused card stays at the left pivot while the cards slide under it.
 * - OK opens the title page. The media Play key plays at once.
 */
@Composable
private fun TvHome(vm: AppViewModel, library: Library, rows: List<ShelfRow>, list: LazyListState) {
    val shape = remember(rows) { homeShape(rows) }
    // Where focus starts: the card the user left (coming back with Back), else the first card of the first row.
    val startKey = remember { startFocusKey(rows, (vm.nav.value.current as? Dest.Home)?.focusKey) }
    val restore = rememberFocusRestore(startKey)
    val tracker = remember { FocusTracker().also { it.key = startKey } }
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, list.firstVisibleItemIndex) }

    val focused = remember { mutableStateOf<TitleCard?>(null) }
    val shown = remember { mutableStateOf(cardForFocusKey(rows, startKey)) }
    val pins = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    LaunchedEffect(list) { pins.collectLatest { list.pinRow(it) } }

    val open: (TitleCard, String, Boolean) -> Unit = { card, key, autoplay ->
        vm.saveFocus(key, list.firstVisibleItemIndex)
        vm.openCard(card, library, autoplay)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            val card = focused.value
            val key = tracker.key
            val play = event.key == Key.MediaPlay || event.key == Key.MediaPlayPause
            if (card != null && key != null && play && event.type == KeyEventType.KeyDown) {
                open(card, key, true)
                true
            } else {
                false
            }
        },
    ) {
        val density = LocalDensity.current
        val geometry = tvHomeGeometry(maxHeight, shape, KhTheme.spacing.safeVertical)
        val artWidthPx = with(density) { (maxWidth * BillboardArtShare).roundToPx() }
        val artHeightPx = with(density) { geometry.billboardHeight.roundToPx() }
        val artPx = rememberUpdatedState(artWidthPx to artHeightPx)
        val context = LocalContext.current

        // Debounce: follow focus only after it has rested, and only once the picture is ready.
        LaunchedEffect(Unit) {
            snapshotFlow { focused.value }.filterNotNull().collectLatest { card ->
                if (card.listKey() == shown.value?.listKey()) return@collectLatest
                delay(KhMotion.BillboardSettleMillis)
                val url = card.artwork
                if (!url.isNullOrBlank()) {
                    val (w, h) = artPx.value
                    withTimeoutOrNull(KhMotion.BillboardPreloadMaxMillis) { context.imageLoader.execute(billboardRequest(context, url, w, h)) }
                }
                shown.value = card
            }
        }

        TvBillboard(shown, geometry, artWidthPx, artHeightPx)

        // The list scrolls rows by hand (pinRow), so focus must not scroll it as well.
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScrollSpec) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().padding(top = geometry.rowsTop),
                // Room under the last row, so it too can reach the pinned spot.
                contentPadding = PaddingValues(bottom = (maxHeight - geometry.rowsTop - tvRowFocusHeight(shape)).coerceAtLeast(0.dp)),
            ) {
                itemsIndexed(rows, key = { _, row -> "row:" + row.title }) { index, row ->
                    TvRowView(
                        vm = vm,
                        library = library,
                        row = row,
                        shape = shape,
                        restore = restore,
                        onRowFocused = { pins.tryEmit(index) },
                        onCardFocused = { card, key ->
                            tracker.key = key
                            focused.value = card
                        },
                        onOpen = { card, key -> open(card, key, false) },
                    )
                }
            }
        }
    }
}

/** Scroll so row [index] has its top at the top of the list. Animated with the on-screen movement curve. */
private suspend fun LazyListState.pinRow(index: Int) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info == null) {
        scrollToItem(index)
        return
    }
    val distance = info.offset.toFloat()
    if (distance != 0f) animateScrollBy(distance, KhMotion.move(KhMotion.RowPinMillis))
}

private fun billboardRequest(context: Context, url: String, widthPx: Int, heightPx: Int): ImageRequest =
    ImageRequest.Builder(context).data(url).size(widthPx, heightPx).build()

/** One TV row. The focused card sits at the left pivot, and the row tells the list when it has focus. */
@Composable
private fun TvRowView(
    vm: AppViewModel,
    library: Library,
    row: ShelfRow,
    shape: CardShape,
    restore: FocusRestore,
    onRowFocused: () -> Unit,
    onCardFocused: (TitleCard, String) -> Unit,
    onOpen: (TitleCard, String) -> Unit,
) {
    val state = rememberSavedList(vm, "${library.key}:row:${row.title}")
    val width = cardWidth(shape)
    // Two cards with the same key would crash the row, so keep the first of each.
    val cards = remember(row.items) { row.items.distinctBy(TitleCard::listKey) }
    val pivot = with(LocalDensity.current) { edge().toPx() }
    val pivotSpec = remember(pivot) { FixedPivotSpec(pivot) }

    // Focus is being sent to a card of this row: make sure that card is composed first.
    val wanted = if (restore.pending) restore.key else null
    LaunchedEffect(wanted) {
        if (wanted == null) return@LaunchedEffect
        val index = cards.indexOfFirst { homeFocusKey(row.title, it) == wanted }
        if (index >= 0) state.revealIndex(index)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = rowTop())
            .onFocusChanged { if (it.hasFocus) onRowFocused() },
    ) {
        RowTitle(row.title)
        CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = edge(), vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(cardGap()),
            ) {
                items(cards, key = TitleCard::listKey) { card ->
                    val key = homeFocusKey(row.title, card)
                    ShelfCard(
                        card,
                        shape,
                        onClick = { onOpen(card, key) },
                        modifier = Modifier.width(width),
                        mark = rememberTitleMark(vm, card),
                        onFocused = { onCardFocused(card, key) },
                        focusableModifier = Modifier.focusIfRestoring(restore, key),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowTitle(title: String) {
    Text(
        title,
        style = KhTheme.typography.rowTitle,
        color = KhColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = edge(), end = edge(), bottom = 4.dp).semantics { heading() },
    )
}

/**
 * The billboard: the picture on the right 70% with fades on its left and bottom, and the title, facts,
 * overview and a hint on the left. [shown] is read only here, so a change redraws just the billboard.
 */
@Composable
private fun TvBillboard(shown: State<TitleCard?>, geometry: TvHomeGeometry, artWidthPx: Int, artHeightPx: Int) {
    Box(Modifier.fillMaxWidth().height(geometry.billboardHeight)) {
        BillboardArtSwap(
            shown,
            artWidthPx,
            artHeightPx,
            Modifier.align(Alignment.TopEnd).fillMaxWidth(BillboardArtShare).fillMaxHeight(),
        )
        // Left fade: solid where the picture starts, clear by its middle, so text reads and the edge disappears.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(BillboardArtShare)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(0f to KhColors.Background, 0.5f to Color.Transparent)),
        )
        // Bottom fade: into the page colour well before the first row's title.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.65f to KhColors.Background.copy(alpha = 0.85f),
                        1f to KhColors.Background,
                    ),
                ),
        )
        Crossfade(
            targetState = shown.value,
            modifier = Modifier.align(Alignment.TopStart).fillMaxSize(),
            animationSpec = KhMotion.crossfade(),
            label = "billboardText",
        ) { card ->
            if (card != null) {
                Box(
                    Modifier
                        .padding(start = edge(), top = KhTheme.spacing.safeVertical)
                        .height(geometry.textHeight)
                        .fillMaxWidth(0.42f)
                        .widthIn(max = 560.dp),
                ) {
                    BillboardText(card, geometry.textHeight)
                }
            }
        }
    }
}

/**
 * The new picture fades in over the old one, which stays solid underneath, so the screen never dips
 * toward the page colour halfway through. [shown] only changes once the picture is loaded.
 */
@Composable
private fun BillboardArtSwap(shown: State<TitleCard?>, widthPx: Int, heightPx: Int, modifier: Modifier) {
    val target = shown.value
    var base by remember { mutableStateOf(target) }
    var top by remember { mutableStateOf<TitleCard?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(target?.listKey()) {
        if (target?.listKey() == (top ?: base)?.listKey()) return@LaunchedEffect
        if (top != null) {
            base = top
            top = null
        }
        if (base == null) {
            base = target
            return@LaunchedEffect
        }
        fade.snapTo(0f)
        top = target
        fade.animateTo(1f, KhMotion.crossfade())
        base = target
        top = null
    }
    Box(modifier) {
        base?.let { BillboardArt(it.artwork, widthPx, heightPx, Modifier.fillMaxSize()) }
        top?.let { BillboardArt(it.artwork, widthPx, heightPx, Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) }
    }
}

@Composable
private fun BillboardArt(url: String?, widthPx: Int, heightPx: Int, modifier: Modifier) {
    if (url.isNullOrBlank()) {
        Box(modifier)
        return
    }
    val context = LocalContext.current
    // The same request the preload used, so the picture comes straight from Coil's memory cache.
    AsyncImage(
        model = remember(url, widthPx, heightPx) { billboardRequest(context, url, widthPx, heightPx) },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(0f, -0.35f),
        modifier = modifier,
    )
}

@Composable
private fun BillboardText(card: TitleCard, textHeight: androidx.compose.ui.unit.Dp) {
    val overviewLines = billboardOverviewLines(textHeight)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)) {
        Text(
            card.name,
            style = KhTheme.typography.display,
            color = KhColors.TextPrimary,
            maxLines = billboardTitleLines(textHeight),
            overflow = TextOverflow.Ellipsis,
        )
        val meta = heroMeta(card)
        if (meta.isNotBlank()) Text(meta, style = KhTheme.typography.meta, color = KhColors.TextSecondary, maxLines = 1)
        val overview = card.overview
        if (overviewLines > 0 && !overview.isNullOrBlank()) {
            Text(overview, style = KhTheme.typography.synopsis, color = KhColors.TextSecondary, maxLines = overviewLines, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "OK",
                style = KhTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = KhColors.TextPrimary,
                modifier = Modifier
                    .border(1.dp, KhColors.TextSecondary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Text("for details", style = KhTheme.typography.caption, color = KhColors.TextSecondary)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Phone Home: Netflix mobile.

@Composable
private fun PhoneHome(vm: AppViewModel, library: Library, rows: List<ShelfRow>, list: LazyListState) {
    // One card shape for the whole Home, so every row, the hero and the skeleton line up.
    val shape = remember(rows) { homeShape(rows) }
    val hero = remember(rows, shape) { pickHero(rows, shape) }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "hero") {
            if (hero != null) {
                PhoneHero(vm, library, hero, shape)
            } else {
                Spacer(Modifier.height(phoneHeroTop() + 16.dp))
            }
        }
        items(rows, key = { "row:" + it.title }) { row ->
            PhoneRowView(vm, library, row, shape)
        }
    }
}

/** One phone row: a title, then cards of the Home's [shape]. Every row uses the same sizes, gaps and padding. */
@Composable
private fun PhoneRowView(vm: AppViewModel, library: Library, row: ShelfRow, shape: CardShape) {
    val state = rememberSavedList(vm, "${library.key}:row:${row.title}")
    val width = cardWidth(shape)
    val cards = remember(row.items) { row.items.distinctBy(TitleCard::listKey) }
    Column(Modifier.fillMaxWidth().padding(top = rowTop())) {
        RowTitle(row.title)
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = edge(), vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(cardGap()),
        ) {
            items(cards, key = TitleCard::listKey) { card ->
                ShelfCard(card, shape, onClick = { vm.openCard(card, library) }, modifier = Modifier.width(width), mark = rememberTitleMark(vm, card))
            }
        }
    }
}

/**
 * A rounded, bordered card under the top bar, like Netflix's mobile home. It is as wide as the rows
 * (same side margin) and follows the Home's card [shape]: tall for posters, wide for stills. Tapping
 * the picture opens the title page. Play and My List sit under it at the same width.
 */
@Composable
private fun PhoneHero(vm: AppViewModel, library: Library, hero: TitleCard, shape: CardShape) {
    val portrait = shape == CardShape.POSTER
    val corner = KhTheme.shapes.tile
    val version by vm.saved.version.collectAsStateWithLifecycle()
    val inList = remember(version, hero) { vm.saved.inList(hero) }
    val listSpoken = if (inList) "Remove from My List" else "Add to My List"
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = edge(), end = edge(), top = phoneHeroTop())) {
        val height = phoneHeroHeight(maxWidth, LocalConfiguration.current.screenHeightDp.dp, shape)
        Column(Modifier.fillMaxWidth()) {
            KhFocusable(
                onClick = { vm.openCard(hero, library) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .semantics { contentDescription = "${hero.name}. Open details" },
                shape = corner,
                scaleAndLift = false,
            ) {
                Box(Modifier.fillMaxSize().border(1.dp, KhColors.HeroBorder, corner)) {
                    Artwork(
                        if (portrait) hero.poster ?: hero.artwork else hero.backdrop ?: hero.artwork,
                        hero.name,
                        Modifier.fillMaxSize(),
                        fadeMillis = 200,
                        titleOnFailure = false,
                    )
                    // A strong fade from the middle down, so the title reads on any picture.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.65f)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.4f to KhColors.Background.copy(alpha = 0.3f),
                                    1f to KhColors.Background.copy(alpha = 0.96f),
                                ),
                            ),
                    )
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            hero.name,
                            style = KhTheme.typography.heroTitle,
                            color = KhColors.TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = heroMeta(hero)
                        if (meta.isNotBlank()) {
                            Text(meta, style = KhTheme.typography.caption, color = KhColors.TextSecondary, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhActionButton(
                    "Play",
                    onClick = { vm.openCard(hero, library, autoplay = true) },
                    primary = true,
                    icon = Icons.Filled.PlayArrow,
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                )
                KhActionButton(
                    "My List",
                    onClick = { vm.saved.toggleList(hero) },
                    icon = if (inList) Icons.Filled.Check else Icons.Filled.Add,
                    height = 48.dp,
                    modifier = Modifier.weight(1f).semantics { contentDescription = listSpoken },
                )
            }
        }
    }
}

internal fun kindLabel(kind: MediaKind) = when (kind) {
    MediaKind.MOVIE -> "Movie"
    MediaKind.TV -> "Series"
    MediaKind.ANIME -> "Anime"
}

/** "2024 · Series · ★ 8.1" */
private fun heroMeta(card: TitleCard): String =
    listOfNotNull(card.year, kindLabel(card.kind), card.meta?.takeIf { it.isNotBlank() }?.let(::ratingText)).joinToString(" · ")

/** "8.957" -> "★ 9.0". Anything that is not a number is shown as it is. */
internal fun ratingText(raw: String): String {
    val value = raw.trim().toDoubleOrNull() ?: return raw
    if (value <= 0.0) return raw
    return "★ " + String.format(java.util.Locale.US, "%.1f", value)
}

/**
 * Grey blocks in the shape of the finished Home: on TV the billboard (picture and text lines) and the
 * first rows under it; on a phone the hero card, its two buttons and the rows. Sizes, gaps and
 * margins come from the same helpers the real Home uses. Static: no shimmer.
 */
@Composable
private fun HomeSkeleton(shape: CardShape) {
    val tv = isTv()
    val corner = KhTheme.shapes.tile
    if (tv) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val geometry = tvHomeGeometry(maxHeight, shape, KhTheme.spacing.safeVertical)
            Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(BillboardArtShare).height(geometry.billboardHeight).background(KhColors.Surface))
            Column(
                Modifier
                    .padding(start = edge(), top = KhTheme.spacing.safeVertical)
                    .height(geometry.textHeight)
                    .fillMaxWidth(0.4f),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(40.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(16.dp))
                SkeletonBlock(Modifier.fillMaxWidth().height(14.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(14.dp))
            }
            SkeletonRows(shape, Modifier.padding(top = geometry.rowsTop), count = 2)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = edge(), end = edge(), top = phoneHeroTop())) {
                val height = phoneHeroHeight(maxWidth, LocalConfiguration.current.screenHeightDp.dp, shape)
                Column {
                    Box(Modifier.fillMaxWidth().height(height).background(KhColors.Surface, corner))
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBlock(Modifier.weight(1f).height(48.dp))
                        SkeletonBlock(Modifier.weight(1f).height(48.dp))
                    }
                }
            }
            SkeletonRows(shape, Modifier, count = 2)
        }
    }
}

@Composable
private fun SkeletonRows(shape: CardShape, modifier: Modifier, count: Int) {
    val tv = isTv()
    Column(modifier) {
        repeat(count) {
            Column(Modifier.fillMaxWidth().padding(top = rowTop())) {
                SkeletonBlock(Modifier.padding(start = edge(), bottom = 4.dp).width(140.dp).height(20.dp))
                Row(
                    Modifier.padding(start = edge(), top = if (tv) 12.dp else 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(cardGap()),
                ) {
                    repeat(if (tv) 6 else 4) {
                        Column(Modifier.width(cardWidth(shape))) {
                            SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(shape.ratio()))
                            if (shape == CardShape.LANDSCAPE) SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(12.dp))
                        }
                    }
                }
            }
        }
    }
}
