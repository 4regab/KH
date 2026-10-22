package com.kisskh.tv.ui.shelf

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.api.MY_LIST_ROW
import com.kisskh.tv.api.ReleaseDates
import com.kisskh.tv.api.TitleMark
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.CONTINUE_ROW
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.OriginCountries
import com.kisskh.tv.api.source.OriginCountry
import com.kisskh.tv.api.source.ShelfRow
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.api.source.TitleDetail
import com.kisskh.tv.api.source.progressKey
import com.kisskh.tv.download.DownloadActionButton
import com.kisskh.tv.download.DownloadButton
import com.kisskh.tv.download.TitleDownloads
import com.kisskh.tv.download.rememberTitleDownloads
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.FilterDropdown
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.HeroBackdrop
import com.kisskh.tv.ui.components.KhActionButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.KhSpinner
import com.kisskh.tv.ui.components.rememberAutoFocusAllowed
import com.kisskh.tv.ui.media.KhField
import com.kisskh.tv.ui.shell.PhoneSearchButton
import com.kisskh.tv.ui.shell.PhoneWordmark
import com.kisskh.tv.ui.shell.SourceAvatar
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhGradients
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/*
 * The screens every source shares: Home, the Movies / TV / Anime grids, Search and the title page.
 * Phones get a top bar with the source switch; on TV the rail does that job.
 */

private val TopBarHeight = 56.dp

/** Left and right margin of every shelf screen. */
@Composable
private fun edge(): Dp = if (KhTheme.formFactor == FormFactor.TV) 24.dp else 16.dp

@Composable
private fun isTv() = KhTheme.formFactor == FormFactor.TV

// ---------------------------------------------------------------- top bar

/**
 * Phone top bar: the KH mark (or a screen [title]), the source switch and Search. It sits over the
 * Home artwork and turns solid once the page scrolls ([solid] 0..1).
 */
@Composable
fun ShelfTopBar(vm: AppViewModel, library: Library, solid: Float, title: String? = null, showSearch: Boolean = true) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        KhColors.Background.copy(alpha = 0.55f + 0.4f * solid),
                        KhColors.Background.copy(alpha = 0.95f * solid),
                    ),
                ),
            )
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        Row(
            Modifier.fillMaxWidth().height(TopBarHeight).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (title == null) {
                PhoneWordmark()
            } else {
                Text(
                    title,
                    style = KhTheme.typography.headline,
                    color = KhColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.weight(1f))
            SourceButton(library, onClick = { vm.sourcePicker.value = true })
            if (showSearch) PhoneSearchButton(onClick = { vm.selectTab(RootTab.SEARCH) }, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** The current source, as a pill. Opens "Choose a source". */
@Composable
fun SourceButton(library: Library, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = "Source: ${library.label}. Change source" },
        shape = KhTheme.shapes.chip,
        scaleAndLift = false,
    ) { focused ->
        Row(
            Modifier
                .align(Alignment.Center)
                .padding(vertical = 6.dp)
                .background(if (focused) KhColors.Elevated else KhColors.Surface.copy(alpha = 0.85f), KhTheme.shapes.chip)
                .border(1.dp, KhColors.ChipOutline, KhTheme.shapes.chip)
                .padding(start = 6.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceAvatar(library, size = 22.dp)
            Text(
                library.label,
                style = KhTheme.typography.caption,
                color = KhColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, end = 2.dp),
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = KhColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------------------------------------------------------------- scroll memory

@Composable
private fun rememberSavedList(vm: AppViewModel, key: String): LazyListState {
    val saved = vm.shelf.scrolls[key]
    val state = rememberLazyListState(saved?.first ?: 0, saved?.second ?: 0)
    DisposableEffect(key) {
        onDispose { vm.shelf.scrolls[key] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    }
    return state
}

@Composable
private fun rememberSavedGrid(vm: AppViewModel, key: String): LazyGridState {
    val saved = vm.shelf.scrolls[key]
    val state = rememberLazyGridState(saved?.first ?: 0, saved?.second ?: 0)
    DisposableEffect(key) {
        onDispose { vm.shelf.scrolls[key] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    }
    return state
}

// ---------------------------------------------------------------- home

@Composable
fun ShelfHome(vm: AppViewModel, library: Library) {
    val state by remember(library) { vm.shelf.home(library) }.collectAsStateWithLifecycle()
    // Coming back from a player: the Continue row must show what was just watched.
    // The same goes for My List and Watched, which can change on a title page.
    val savedVersion by vm.saved.version.collectAsStateWithLifecycle()
    LaunchedEffect(library, savedVersion) { vm.shelf.refreshContinue(library) }
    val list = rememberSavedList(vm, "${library.key}:home")
    val tv = isTv()
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
            is UiState.Success -> HomeRows(vm, library, s.data, list)
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

/** Space above a row's title: the same for every row. TV rows add their own focus room below. */
@Composable
private fun rowTop(): Dp = if (isTv()) 12.dp else 20.dp

/** Top of the hero on a phone: under the status bar and the top bar. */
@Composable
private fun phoneHeroTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight + 8.dp

/** Height of the TV billboard card for a card [width]. */
private fun tvHeroHeight(width: Dp): Dp = (width * 0.40f).coerceIn(280.dp, 440.dp)

private val HeroCorner = RoundedCornerShape(12.dp)

@Composable
private fun HomeRows(vm: AppViewModel, library: Library, rows: List<ShelfRow>, list: LazyListState) {
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
                if (isTv()) TvHero(vm, library, hero) else PhoneHero(vm, library, hero, shape)
            } else {
                Spacer(Modifier.height(TopBarHeight + 24.dp))
            }
        }
        items(rows, key = { "row:" + it.title }) { row ->
            ShelfRowView(vm, library, row, shape)
        }
    }
}

/** One row: a title, then cards of the Home's [shape]. Every row uses the same sizes, gaps and padding. */
@Composable
private fun ShelfRowView(vm: AppViewModel, library: Library, row: ShelfRow, shape: CardShape) {
    val state = rememberSavedList(vm, "${library.key}:row:${row.title}")
    val width = cardWidth(shape)
    Column(Modifier.fillMaxWidth().padding(top = rowTop())) {
        Text(
            row.title,
            style = KhTheme.typography.rowTitle,
            color = KhColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = edge(), end = edge(), bottom = 4.dp).semantics { heading() },
        )
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = edge(), vertical = if (isTv()) 12.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(cardGap()),
        ) {
            itemsIndexed(row.items, key = { i, card -> "$i:${card.source.key}:${card.id}:${card.resumeEpisode}" }) { _, card ->
                ShelfCard(card, shape, onClick = { vm.openCard(card, library) }, modifier = Modifier.width(width), mark = rememberTitleMark(vm, card))
            }
        }
    }
}

/**
 * Phone: a rounded, bordered card under the top bar, like Netflix's mobile home. It is as wide as the
 * rows (same side margin) and follows the Home's card [shape]: tall for posters, wide for stills.
 * The Play and More info buttons sit under it at the same width.
 */
@Composable
private fun PhoneHero(vm: AppViewModel, library: Library, hero: TitleCard, shape: CardShape) {
    val portrait = shape == CardShape.POSTER
    Column(Modifier.fillMaxWidth().padding(start = edge(), end = edge(), top = phoneHeroTop())) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (portrait) 4f / 5f else 16f / 10f)
                .clip(HeroCorner)
                .border(1.dp, KhColors.HeroBorder, HeroCorner),
        ) {
            Artwork(
                if (portrait) hero.poster ?: hero.artwork else hero.backdrop ?: hero.artwork,
                hero.name,
                Modifier.fillMaxSize(),
                fadeMillis = 200,
                titleOnFailure = false,
            )
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f).background(KhGradients.BottomFade))
            Column(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    hero.name,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
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
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KhActionButton("Play", onClick = { vm.openCard(hero, library, autoplay = true) }, primary = true, icon = Icons.Filled.PlayArrow, height = 48.dp, modifier = Modifier.weight(1f))
            KhActionButton("More info", onClick = { vm.openCard(hero, library) }, icon = Icons.Filled.Info, height = 48.dp, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * TV and wide screens: Netflix's billboard, as a rounded, bordered card with the same side margin as
 * the rows. Artwork on the right, the title and actions on the left.
 */
@Composable
private fun TvHero(vm: AppViewModel, library: Library, hero: TitleCard) {
    val play = remember { FocusRequester() }
    val allowed = rememberAutoFocusAllowed()
    LaunchedEffect(hero.id) { if (allowed()) runCatching { play.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = edge(), end = edge(), top = 12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(tvHeroHeight(maxWidth))
                .clip(HeroCorner)
                .border(1.dp, KhColors.HeroBorder, HeroCorner),
        ) {
            HeroBackdrop(hero.backdrop ?: hero.artwork, hero.name, Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 32.dp, end = 32.dp, bottom = 20.dp).widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(hero.name, style = KhTheme.typography.display, color = KhColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = heroMeta(hero)
                if (meta.isNotBlank()) Text(meta, style = KhTheme.typography.meta, color = KhColors.TextSecondary)
                hero.overview?.let {
                    Text(it, style = KhTheme.typography.synopsis, color = KhColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KhActionButton(
                        "Play",
                        onClick = { vm.openCard(hero, library, autoplay = true) },
                        primary = true,
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.width(150.dp).focusRequester(play),
                    )
                    KhActionButton("More info", onClick = { vm.openCard(hero, library) }, icon = Icons.Filled.Info, modifier = Modifier.width(170.dp))
                }
            }
        }
    }
}

private fun kindLabel(kind: MediaKind) = when (kind) {
    MediaKind.MOVIE -> "Movie"
    MediaKind.TV -> "Series"
    MediaKind.ANIME -> "Anime"
}

private fun heroMeta(card: TitleCard): String =
    listOfNotNull(card.year, kindLabel(card.kind), card.meta?.takeIf { it.isNotBlank() }?.let(::ratingText)).joinToString("  •  ")

/** "8.957" -> "★ 9.0". Anything that is not a number is shown as it is. */
internal fun ratingText(raw: String): String {
    val value = raw.trim().toDoubleOrNull() ?: return raw
    if (value <= 0.0) return raw
    return "★ " + String.format(java.util.Locale.US, "%.1f", value)
}

/**
 * Grey blocks in the shape of the finished Home: the hero card, then rows of cards of [shape].
 * Sizes, gaps and margins come from the same helpers the real Home uses.
 */
@Composable
private fun HomeSkeleton(shape: CardShape) {
    val tv = isTv()
    Column(Modifier.fillMaxSize()) {
        if (tv) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = edge(), end = edge(), top = 12.dp)) {
                Box(Modifier.fillMaxWidth().height(tvHeroHeight(maxWidth)).background(KhColors.Surface, HeroCorner))
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(start = edge(), end = edge(), top = phoneHeroTop())) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (shape == CardShape.POSTER) 4f / 5f else 16f / 10f)
                        .background(KhColors.Surface, HeroCorner),
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.weight(1f).height(48.dp))
                    SkeletonBlock(Modifier.weight(1f).height(48.dp))
                }
            }
        }
        repeat(2) {
            Column(Modifier.fillMaxWidth().padding(top = rowTop())) {
                SkeletonBlock(Modifier.padding(start = edge(), bottom = 4.dp).width(140.dp).height(20.dp))
                Row(
                    Modifier.padding(start = edge(), top = if (tv) 12.dp else 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(cardGap()),
                ) {
                    repeat(4) {
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

// ---------------------------------------------------------------- browse

@Composable
fun ShelfBrowse(vm: AppViewModel, library: Library, kind: MediaKind) {
    val title = when (kind) {
        MediaKind.MOVIE -> "Movies"
        MediaKind.TV -> if (library == Library.KISSKH) "TV Series" else "TV Shows"
        MediaKind.ANIME -> "Anime"
    }
    val tv = isTv()
    // Country filter: only where the source can really filter (see OriginCountries.supported).
    val filterable = OriginCountries.supported(library, kind)
    val chosen by vm.shelf.countries.collectAsStateWithLifecycle()
    val country = if (filterable) OriginCountries.byCode(chosen["${library.key}:${kind.name}"]) else null
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
        if (filterable) {
            CountryFilter(country, onPick = { vm.shelf.setCountry(library, kind, it?.code) })
        }
        // One grid per country: its pages, scroll spot and focus start fresh when the country changes.
        key(library, kind, country?.code) { BrowseGrid(vm, library, kind, title, country) }
    }
}

/** "Country: All" chip with the KissKH country list. Sits under the header and stays usable when a list is empty. */
@Composable
private fun CountryFilter(selected: OriginCountry?, onPick: (OriginCountry?) -> Unit) {
    val options = remember { listOf<OriginCountry?>(null) + OriginCountries.all }
    Row(Modifier.fillMaxWidth().padding(start = edge(), end = edge(), top = 2.dp)) {
        FilterDropdown(
            label = "Country",
            options = options,
            selected = selected,
            optionLabel = { it?.label ?: "All" },
            onSelect = onPick,
        )
    }
}

@Composable
private fun BrowseGrid(vm: AppViewModel, library: Library, kind: MediaKind, title: String, country: OriginCountry?) {
    val code = country?.code
    val state by remember(library, kind, code) { vm.shelf.browse(library, kind, code) }.collectAsStateWithLifecycle()
    val grid = rememberSavedGrid(vm, "${library.key}:browse:$kind:${code ?: "all"}")
    when {
        state.loading -> GridSkeleton(library.shapeHint())
        state.error != null && state.items.isEmpty() -> ErrorState(
            "Couldn't load $title. ${state.error}",
            onRetry = { vm.shelf.retryBrowse(library, kind, code) },
            autoFocus = true,
        )
        state.items.isEmpty() -> EmptyState(
            if (country != null) "No $title from ${country.label} on ${library.label}." else "${library.label} has no $title right now.",
        )
        else -> {
            // Decided once from the first page, so later pages never change the card shape under the viewer.
            val shape = remember { shapeFor(state.items) }
            val last by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
            LaunchedEffect(last, state.items.size, state.hasMore) {
                if (state.hasMore && last >= state.items.size - 9) vm.shelf.loadMore(library, kind, code)
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
                            KhChip("Retry", onClick = { vm.shelf.loadMore(library, kind, code) }, outlined = true)
                        }
                        !state.hasMore && state.items.size > 12 -> Text("That's everything.", style = KhTheme.typography.caption, color = KhColors.NavMuted)
                    }
                },
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
 * Coming soon and Not available marks.
 */
@Composable
private fun CardGrid(
    vm: AppViewModel,
    items: List<TitleCard>,
    shape: CardShape,
    state: LazyGridState,
    onOpen: (TitleCard) -> Unit,
    caption: Boolean = shape == CardShape.LANDSCAPE,
    badge: (TitleCard) -> String? = ::continueBadge,
    footer: @Composable () -> Unit = {},
) {
    val gap = cardGap()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardWidth(shape)),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = edge(), end = edge(), top = if (isTv()) 12.dp else 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(if (caption) 14.dp else gap),
    ) {
        itemsIndexed(items, key = { i, card -> "$i:${card.source.key}:${card.id}" }) { _, card ->
            ShelfCard(card, shape, onClick = { onOpen(card) }, caption = caption, badge = badge(card), mark = rememberTitleMark(vm, card))
        }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { footer() }
        }
    }
}

/** Grey cards in the grid's [shape] while a page loads. */
@Composable
private fun GridSkeleton(shape: CardShape) {
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

// ---------------------------------------------------------------- search

@Composable
fun ShelfSearch(vm: AppViewModel, library: Library) {
    val query by vm.shelf.query.collectAsStateWithLifecycle()
    val all by vm.shelf.allSources.collectAsStateWithLifecycle()
    val results by vm.shelf.results.collectAsStateWithLifecycle()
    val grid = rememberSavedGrid(vm, "search")
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
        when (val s = results) {
            UiState.Loading -> GridSkeleton(if (all) CardShape.LANDSCAPE else library.shapeHint())
            is UiState.Empty -> EmptyState(
                if (query.isBlank()) {
                    if (all) "Find a title on every source at once." else "Find movies, shows and anime on ${library.label}."
                } else {
                    s.message ?: "No titles match."
                },
            )
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
                )
            }
        }
    }
}

// ---------------------------------------------------------------- title page

@Composable
fun ShelfDetail(vm: AppViewModel, library: Library, dest: Dest.TitlePage) {
    val state by remember(library, dest.id, dest.kind) {
        vm.shelf.detail(library, dest.id, dest.kind, dest.slug, dest.name, dest.poster)
    }.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> DetailLoading(vm, dest)
        is UiState.Error -> Box(Modifier.fillMaxSize()) {
            ErrorState(
                "Couldn't open this title. ${s.message}",
                onRetry = { vm.shelf.retryDetail(library, dest.id, dest.kind, dest.slug, dest.name, dest.poster) },
                autoFocus = true,
            )
            BackCircle(vm, Modifier.align(Alignment.TopStart))
        }
        is UiState.Empty -> Box(Modifier.fillMaxSize()) {
            EmptyState(s.message ?: "This title is not available.")
            BackCircle(vm, Modifier.align(Alignment.TopStart))
        }
        is UiState.Success -> DetailBody(vm, library, dest, s.data)
    }
}

/** While the title loads: its artwork and name from the card that was tapped, and a spinner. */
@Composable
private fun DetailLoading(vm: AppViewModel, dest: Dest.TitlePage) {
    Box(Modifier.fillMaxSize()) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).heightIn(max = 420.dp)) {
                Artwork(dest.poster, dest.name, Modifier.fillMaxSize(), titleOnFailure = false)
                Box(Modifier.fillMaxSize().background(KhGradients.BottomFade))
            }
            if (dest.name.isNotBlank()) {
                Text(dest.name, style = KhTheme.typography.headline, color = KhColors.TextPrimary, modifier = Modifier.padding(horizontal = edge(), vertical = 8.dp))
            }
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) { KhSpinner() }
        }
        BackCircle(vm, Modifier.align(Alignment.TopStart))
    }
}

/** The round Back button over the title artwork (phones have no rail to leave by). */
@Composable
private fun BackCircle(vm: AppViewModel, modifier: Modifier = Modifier) {
    if (isTv()) return
    KhFocusable(
        onClick = { vm.onBack() },
        modifier = modifier
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 4.dp, start = 8.dp)
            .size(48.dp)
            .semantics { contentDescription = "Back" },
        shape = CircleShape,
        scaleAndLift = false,
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_player_back), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

/** What the big button on a title page says and plays. [dimmed] draws it grey: it only explains why nothing plays. */
private data class MainAction(val label: String, val dimmed: Boolean = false, val play: () -> Unit)

@Composable
private fun DetailBody(vm: AppViewModel, library: Library, dest: Dest.TitlePage, detail: TitleDetail) {
    val card = detail.card
    val movie = card.kind == MediaKind.MOVIE
    val seasons = detail.seasons.filter { movie || it.number > 0 }.ifEmpty { detail.seasons }
    // My List and Watched marks. Reading the version redraws this page after every change.
    val store = vm.saved
    val savedVersion by store.version.collectAsStateWithLifecycle()
    val inList = remember(savedVersion, card) { store.inList(card) }
    val titleWatched = remember(savedVersion, card) { store.isWatched(card) }
    // Coming soon (by release date) or Not available (every server failed last time). The title stays open either way.
    val availabilityVersion by vm.availability.version.collectAsStateWithLifecycle()
    val mark = remember(availabilityVersion, card) { vm.availability.markFor(card) }
    val comingSoon = mark as? TitleMark.ComingSoon
    val context = LocalContext.current
    val notOutYet = { Toast.makeText(context, "Not out yet. Comes out ${ReleaseDates.long(comingSoon?.date)}.", Toast.LENGTH_SHORT).show() }
    // Where the viewer left off on this title, newest first. Opens on that season.
    val last = remember(library, card.id) { vm.lastWatched(library, card) }
    // A title marked watched plays from the start: never "Resume", no "Continue" episode.
    val resumeFrom = if (titleWatched) null else last
    var seasonNumber by remember(card.id) {
        mutableIntStateOf(last?.season?.takeIf { s -> seasons.any { it.number == s } } ?: seasons.firstOrNull()?.number ?: 1)
    }
    val selected = seasons.firstOrNull { it.number == seasonNumber } ?: seasons.firstOrNull()
    val inline = selected?.episodes.orEmpty()
    val remote by remember(library, card.id, seasonNumber, inline.isEmpty() && !movie) {
        if (inline.isEmpty() && !movie) vm.shelf.season(library, card.id, seasonNumber)
        else kotlinx.coroutines.flow.MutableStateFlow(UiState.Success(inline))
    }.collectAsStateWithLifecycle()
    val episodes = (remote as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(episodes) { if (episodes.isNotEmpty()) vm.shelf.rememberEpisodes(library, card.id, seasonNumber, episodes) }

    // Download buttons: one per episode row, and for a one-part movie one beside My List.
    val titleDownloads = rememberTitleDownloads(vm, library, card)
    val movieEpisode = when {
        !movie || episodes.size > 1 -> null
        episodes.isNotEmpty() -> episodes.first()
        // Same stand-in the Play button uses when a movie lists no episode. KissKH needs a real episode id.
        library != Library.KISSKH -> EpisodeRef(card.id, 0, 1, "Movie", card.artwork)
        else -> null
    }
    val movieDownload: (@Composable (Modifier, Boolean) -> Unit)? = if (movieEpisode == null || comingSoon != null) null else { size, stacked ->
        DownloadActionButton(titleDownloads, movieEpisode, episodes, card.name, size, stacked)
    }
    val action = remember(card, episodes, resumeFrom, seasonNumber, comingSoon) {
        if (comingSoon != null) MainAction("Coming soon", dimmed = true) { notOutYet() }
        else mainAction(vm, library, card, movie, seasonNumber, episodes, resumeFrom)
    }
    // Play on Home opened this page: start the main action once the episodes are known.
    LaunchedEffect(dest.autoplay, remote is UiState.Loading) {
        if (dest.autoplay && remote !is UiState.Loading) {
            vm.consumeAutoplay()
            action.play()
        }
    }
    val list = rememberSavedList(vm, "${library.key}:title:${card.id}")
    val playFocus = remember { FocusRequester() }
    val allowed = rememberAutoFocusAllowed()
    LaunchedEffect(card.id) { if (allowed()) runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "header") {
                DetailHeader(
                    detail, library, movie, seasons.size, action, playFocus,
                    mark = mark,
                    inList = inList,
                    watched = titleWatched,
                    onToggleList = { store.toggleList(card) },
                    onToggleWatched = { store.toggleWatched(card) },
                    download = movieDownload,
                )
            }
            // Series, and movies that come in several parts (some KissKH films).
            if (!movie || episodes.size > 1) {
                item(key = "episodes-title") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = edge(), end = edge(), top = 24.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (movie) "Parts" else "Episodes", style = KhTheme.typography.rowTitle, color = KhColors.TextPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                        if (episodes.isNotEmpty()) Text("${episodes.size}", style = KhTheme.typography.caption, color = KhColors.TextSecondary)
                    }
                }
                if (!movie && seasons.size > 1) {
                    item(key = "seasons") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = edge(), vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(seasons, key = { it.number }) { season ->
                                KhChip(season.name, selected = season.number == seasonNumber, onClick = { seasonNumber = season.number })
                            }
                        }
                    }
                }
                when (val eps = remote) {
                    UiState.Loading -> item(key = "eps-loading") {
                        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { KhSpinner(size = 32.dp) }
                    }
                    is UiState.Error -> item(key = "eps-error") {
                        ErrorState(
                            "Couldn't load the episodes. ${eps.message}",
                            onRetry = { vm.shelf.retrySeason(library, card.id, seasonNumber) },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                        )
                    }
                    is UiState.Empty -> item(key = "eps-empty") {
                        EmptyState(eps.message ?: "No episodes in this season yet.", modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                    is UiState.Success -> itemsIndexed(eps.data, key = { i, ep -> "ep:$i:${ep.id}:${ep.number}" }) { _, episode ->
                        val current = resumeFrom != null && resumeFrom.season.coerceAtLeast(1) == episode.season.coerceAtLeast(1) && resumeFrom.episode == episode.number
                        val watched = remember(savedVersion, card, episode.season, episode.number) {
                            store.episodeWatched(card, episode.season, episode.number)
                        }
                        EpisodeRow(
                            episode = episode,
                            progress = vm.episodeProgress(library, card, episode),
                            current = current,
                            watched = watched,
                            onClick = { if (comingSoon != null) notOutYet() else vm.playTitle(library, card, episode, eps.data) },
                            showDownload = comingSoon == null,
                            downloads = titleDownloads,
                            episodes = eps.data,
                            onToggleWatched = {
                                store.setEpisodeWatched(card, episode.season, episode.number, !watched, eps.data.map { it.season to it.number })
                            },
                        )
                    }
                }
            }
        }
        titleDownloads.Host()
        BackCircle(vm, Modifier.align(Alignment.TopStart))
    }
}

/** The line under a title's facts that says why it may not play: the release date, or that every server failed. */
@Composable
private fun MarkNote(mark: TitleMark?, modifier: Modifier = Modifier) {
    val text = when (mark) {
        is TitleMark.ComingSoon -> "Coming ${ReleaseDates.long(mark.date) ?: mark.date}"
        TitleMark.Unavailable -> "Not available right now \u2014 every server failed. Try again later."
        null -> return
    }
    Text(text, style = KhTheme.typography.caption, color = KhColors.TextPrimary, modifier = modifier)
}

@Composable
private fun DetailHeader(
    detail: TitleDetail,
    library: Library,
    movie: Boolean,
    seasonCount: Int,
    action: MainAction,
    playFocus: FocusRequester,
    mark: TitleMark?,
    inList: Boolean,
    watched: Boolean,
    onToggleList: () -> Unit,
    onToggleWatched: () -> Unit,
    download: (@Composable (Modifier, Boolean) -> Unit)?,
) {
    val card = detail.card
    val meta = listOfNotNull(
        card.year,
        detail.certification?.takeIf { it.isNotBlank() },
        kindLabel(card.kind),
        if (!movie && seasonCount > 1) "$seasonCount seasons" else null,
        card.meta?.takeIf { it.isNotBlank() }?.let(::ratingText),
        library.label,
    ).joinToString("  •  ")
    var expanded by remember(card.id) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Box(Modifier.fillMaxWidth().height((maxWidth * 0.42f).coerceIn(300.dp, 480.dp))) {
                HeroBackdrop(card.backdrop ?: card.artwork, card.name, Modifier.fillMaxSize())
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = edge() + 8.dp, bottom = 12.dp).widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(card.name, style = KhTheme.typography.display, color = KhColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(meta, style = KhTheme.typography.meta, color = KhColors.TextSecondary)
                    MarkNote(mark)
                    if (detail.description.isNotBlank()) {
                        Text(detail.description, style = KhTheme.typography.synopsis, color = KhColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    KhActionButton(
                        action.label,
                        onClick = action.play,
                        primary = !action.dimmed,
                        icon = if (action.dimmed) null else Icons.Filled.PlayArrow,
                        modifier = Modifier.padding(top = 6.dp).widthIn(min = 200.dp).alpha(if (action.dimmed) 0.7f else 1f).focusRequester(playFocus),
                    )
                    SavedActions(inList, watched, onToggleList, onToggleWatched, buttonWidth = 150.dp, download = download)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                    Artwork(card.backdrop ?: card.artwork, card.name, Modifier.fillMaxSize(), fadeMillis = 200, titleOnFailure = false)
                    Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.45f).background(KhGradients.BottomFade))
                }
                Column(Modifier.padding(horizontal = edge()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        card.name,
                        fontSize = 26.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold,
                        color = KhColors.TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(meta, style = KhTheme.typography.caption, color = KhColors.TextSecondary)
                    MarkNote(mark)
                    KhActionButton(
                        action.label,
                        onClick = action.play,
                        primary = !action.dimmed,
                        icon = if (action.dimmed) null else Icons.Filled.PlayArrow,
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).alpha(if (action.dimmed) 0.7f else 1f).focusRequester(playFocus),
                    )
                    SavedActions(inList, watched, onToggleList, onToggleWatched, download = download)
                    if (detail.description.isNotBlank()) {
                        KhFocusable(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth().animateContentSize(tween(KhMotion.CrossfadeMillis, easing = KhMotion.EaseOutStrong)),
                            shape = KhTheme.shapes.button,
                            scaleAndLift = false,
                        ) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    detail.description,
                                    style = KhTheme.typography.synopsis,
                                    color = KhColors.TextSecondary,
                                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (detail.description.length > 140) {
                                    Text(if (expanded) "Less" else "More", style = KhTheme.typography.caption, color = KhColors.TextPrimary, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeRef,
    progress: Float?,
    current: Boolean,
    watched: Boolean,
    onClick: () -> Unit,
    downloads: TitleDownloads,
    episodes: List<EpisodeRef>,
    onToggleWatched: () -> Unit,
    showDownload: Boolean = true,
) {
    val title = episodeTitle(episode)
    // A watched episode shows a full bar, whatever was saved while it played.
    val bar = if (watched) 1f else progress
    Row(
        Modifier.fillMaxWidth().padding(horizontal = edge() - 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KhFocusable(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = listOfNotNull(title, if (watched) "watched" else null, if (current) "continue here" else null).joinToString(", ")
                },
            shape = KhTheme.shapes.button,
            scaleAndLift = false,
        ) { focused ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (focused || current) KhColors.Surface else Color.Transparent, KhTheme.shapes.button)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(if (isTv()) 176.dp else 132.dp).aspectRatio(16f / 9f).clip(KhTheme.shapes.art)) {
                    if (episode.still != null) {
                        Artwork(episode.still, title, Modifier.fillMaxSize())
                        Box(
                            Modifier.align(Alignment.Center).size(34.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        // No still: the episode number is the picture, so no play circle on top of it.
                        Box(Modifier.fillMaxSize().background(KhColors.Elevated), contentAlignment = Alignment.Center) {
                            Text(episode.number.toString(), style = KhTheme.typography.headline, color = KhColors.TextSecondary)
                        }
                    }
                    if (bar != null) {
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.28f)))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(bar.coerceIn(0.02f, 1f)).height(3.dp).background(KhColors.Brand))
                    }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = KhTheme.typography.label, color = KhColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val marks = listOfNotNull(
                        if (watched) "Watched" else if (current) "Continue" else null,
                        if (episode.dub) "Sub · Dub" else null,
                    )
                    if (marks.isNotEmpty()) {
                        Text(marks.joinToString("  •  "), style = KhTheme.typography.badge, color = if (current && !watched) KhColors.Brand else KhColors.TextSecondary)
                    }
                }
            }
        }
        if (showDownload) DownloadButton(downloads, episode, episodes, title)
        WatchedToggle(watched, title, onToggleWatched)
    }
}

/** The 48 dp check at the end of an episode row: empty ring when unwatched, filled with a check once watched. */
@Composable
private fun WatchedToggle(watched: Boolean, title: String, onClick: () -> Unit) {
    KhFocusable(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { contentDescription = if (watched) "Mark $title as unwatched" else "Mark $title as watched" },
        shape = CircleShape,
        scaleAndLift = false,
    ) {
        val ring = if (watched) Modifier.background(KhColors.TextPrimary, CircleShape) else Modifier.border(1.5.dp, KhColors.ChipOutline, CircleShape)
        Box(Modifier.align(Alignment.Center).size(28.dp).then(ring), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (watched) KhColors.OnLight else KhColors.TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
/** "3. Pilot", or "Episode 3" when the name is only the number. */
internal fun episodeTitle(episode: EpisodeRef): String {
    val name = episode.name.trim()
    if (name.isBlank() || name.equals("Episode ${episode.number}", true) || name == episode.number.toString()) return "Episode ${episode.number}"
    if (name.startsWith("Episode", true)) return name
    return "${episode.number}. $name"
}

// ---------------------------------------------------------------- actions

/** The newest progress entry for [card] on [library]: season and episode (0 for a movie), or null. */
internal data class LastWatched(val season: Int, val episode: Int, val episodeId: String?, val resumable: Boolean, val finished: Boolean)

internal fun AppViewModel.lastWatched(library: Library, card: TitleCard): LastWatched? {
    if (library == Library.KISSKH) {
        val entry = repo.history.entry(card.id.toIntOrNull() ?: return null) ?: return null
        return LastWatched(1, entry.episodeNumber.toInt(), entry.episodeId.toString(), entry.resumable, entry.finished)
    }
    val entry = media.progress.entries().firstOrNull { it.source == library.key && it.token == card.id } ?: return null
    return LastWatched(entry.season, entry.episode, null, entry.resumable, entry.finished)
}

/** 0..1 for an episode the viewer started, null otherwise. */
internal fun AppViewModel.episodeProgress(library: Library, card: TitleCard, episode: EpisodeRef): Float? {
    if (library == Library.KISSKH) {
        val entry = repo.history.entry(card.id.toIntOrNull() ?: return null) ?: return null
        return entry.progress?.takeIf { entry.episodeId.toString() == episode.id && entry.resumable }
    }
    val movie = card.kind == MediaKind.MOVIE
    return media.progress.fraction(progressKey(library.key, if (movie) MediaKind.MOVIE else card.kind, card.id, episode.season, episode.number))
}

private fun mainAction(
    vm: AppViewModel,
    library: Library,
    card: TitleCard,
    movie: Boolean,
    season: Int,
    episodes: List<EpisodeRef>,
    last: LastWatched?,
): MainAction {
    if (movie) {
        val episode = episodes.firstOrNull { last?.episodeId != null && it.id == last.episodeId } ?: episodes.firstOrNull()
            ?: EpisodeRef(card.id, 0, 1, "Movie", card.artwork)
        val label = if (last?.resumable == true) "Resume" else "Play"
        return MainAction(label) { vm.playTitle(library, card, episode, episodes) }
    }
    val tag: (Int, Int) -> String = { s, e -> if (library == Library.KISSKH || card.kind == MediaKind.ANIME) "E$e" else "S$s E$e" }
    if (last != null && !last.finished && last.episode > 0) {
        val known = episodes.firstOrNull { (last.episodeId != null && it.id == last.episodeId) || (it.number == last.episode && it.season.coerceAtLeast(1) == last.season.coerceAtLeast(1)) }
        val label = (if (last.resumable) "Resume " else "Play ") + tag(last.season.coerceAtLeast(1), last.episode)
        return MainAction(label) {
            if (known != null) vm.playTitle(library, card, known, episodes) else vm.playNumbers(library, card, last.season, last.episode)
        }
    }
    if (last != null && last.finished) {
        val index = episodes.indexOfFirst { it.number == last.episode && it.season.coerceAtLeast(1) == last.season.coerceAtLeast(1) }
        val next = episodes.getOrNull(index + 1)?.takeIf { index >= 0 }
        if (next != null) return MainAction("Play " + tag(next.season.coerceAtLeast(1), next.number)) { vm.playTitle(library, card, next, episodes) }
    }
    val first = episodes.firstOrNull()
    return if (first != null) {
        MainAction("Play " + tag(first.season.coerceAtLeast(1), first.number)) { vm.playTitle(library, card, first, episodes) }
    } else {
        MainAction("Play " + tag(season.coerceAtLeast(1), 1)) { vm.playNumbers(library, card, season, 1) }
    }
}

internal fun AppViewModel.openCard(card: TitleCard, library: Library, autoplay: Boolean = false) {
    if (card.resumeEpisode != null && card.source == Library.KISSKH) {
        play(
            Dest.Player(
                episodeId = card.resumeEpisode.toIntOrNull() ?: return,
                dramaId = card.id.toIntOrNull() ?: return,
                title = card.name,
                label = card.meta ?: "Episode",
                episodeNumber = card.resumeNumber.toFloat(),
                thumbnail = card.poster,
            ),
        )
        return
    }
    if (card.resumeEpisode != null) {
        openWatch(
            Dest.Watch(
                source = card.source.key,
                titleId = card.id,
                kind = card.kind,
                slug = card.slug,
                season = card.resumeSeason.coerceAtLeast(1),
                episode = card.resumeNumber.coerceAtLeast(1),
                title = card.name,
                poster = card.poster,
            ),
        )
        return
    }
    if (card.source != library) selectLibrary(card.source)
    push(Dest.TitlePage(card.id, card.kind, card.slug, card.name, card.backdrop ?: card.poster, autoplay = autoplay))
}

/** Play season [season] episode [number] when the episode list is not loaded; the player looks it up. */
private fun AppViewModel.playNumbers(library: Library, card: TitleCard, season: Int, number: Int) {
    openWatch(
        Dest.Watch(
            source = library.key,
            titleId = card.id,
            kind = card.kind,
            slug = card.slug,
            season = season.coerceAtLeast(1),
            episode = number.coerceAtLeast(1),
            title = card.name,
            poster = card.poster ?: card.backdrop,
        ),
    )
}

internal fun AppViewModel.playTitle(library: Library, card: TitleCard, episode: EpisodeRef, episodes: List<EpisodeRef>) {
    shelf.rememberEpisodes(library, card.id, episode.season, episodes)
    if (library == Library.KISSKH) {
        val episodeId = episode.id.toIntOrNull() ?: return
        val dramaId = card.id.toIntOrNull() ?: return
        val label = if (card.kind == MediaKind.MOVIE) {
            if (episodes.size > 1) "Part ${episodes.indexOf(episode) + 1}" else "Movie"
        } else {
            episode.name
        }
        play(Dest.Player(episodeId, dramaId, card.name, label, episode.number.toFloat(), card.poster))
        return
    }
    openWatch(
        Dest.Watch(
            source = library.key,
            titleId = card.id,
            kind = card.kind,
            slug = card.slug,
            season = episode.season.coerceAtLeast(1),
            episode = episode.number.coerceAtLeast(1),
            episodeId = episode.id,
            episodeSlug = episode.slug,
            episodeToken = episode.token,
            episodeName = episode.name,
            title = card.name,
            poster = card.poster ?: card.backdrop,
        ),
    )
}
