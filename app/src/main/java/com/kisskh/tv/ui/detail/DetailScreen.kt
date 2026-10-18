package com.kisskh.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Episode
import com.kisskh.tv.api.WatchEntry
import com.kisskh.tv.api.suspendCatching
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.FocusKeys
import com.kisskh.tv.ui.Playable
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.FocusRestore
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.HeroBackdrop
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.StatusPill
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.formatEpisodeNumber
import com.kisskh.tv.ui.components.formatPosition
import com.kisskh.tv.ui.components.heroHeight
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.screenContentPadding
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.errorMessage
import com.kisskh.tv.ui.isMovie
import com.kisskh.tv.ui.metaParts
import com.kisskh.tv.ui.orderedEpisodes
import com.kisskh.tv.ui.playerDest
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MORE_KEY = "detail:more"

/** The big button: what it says, the episode it plays, and how the status line names it. A null episode means "the first one that is out". */
private data class PrimaryAction(val label: String, val episode: Episode?, val name: String)

/**
 * A title page: artwork, facts, a synopsis that can expand, the main button, and the episodes (or
 * the parts of a movie that comes in several).
 *
 * - The button says "Resume EP 5" when this title has saved progress, otherwise "Play EP 1" for
 *   a series or "Watch movie" for a movie.
 * - Before the player opens, the episode is checked. One that is not out yet shows a message here
 *   and focus stays where it was.
 * - After playback, Back lands on the same button or episode.
 */
@Composable
fun DetailScreen(vm: AppViewModel, dest: Dest.Detail) {
    val state by remember(dest.dramaId) { vm.detail(dest.dramaId) }.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> LoadingState()
        is UiState.Error -> ErrorState(s.message, onRetry = { vm.retryDetail(dest.dramaId) }, autoFocus = true)
        is UiState.Empty -> ErrorState(
            s.message ?: "This title isn't available.",
            onRetry = { vm.retryDetail(dest.dramaId) },
            autoFocus = true,
        )
        is UiState.Success -> DetailContent(vm, dest, s.data)
    }
}

@Composable
private fun DetailContent(vm: AppViewModel, dest: Dest.Detail, drama: Drama) {
    val repo = vm.repo
    val movie = drama.isMovie()
    val ordered = remember(drama) { drama.orderedEpisodes() }
    // Read once when the page opens. Coming back from the player builds the page again, so it is fresh.
    val entry = remember(drama.id) { repo.history.entry(drama.id) }
    val positions = remember(drama.id) { ordered.associate { it.id to repo.loadPosition(it.id) }.filterValues { it > 0L } }
    val primary = remember(drama, entry) { primaryAction(drama, ordered, entry) }
    val partsMode = movie && ordered.size > 1
    val showList = ordered.isNotEmpty() && (!movie || partsMode)

    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = dest.scrollIndex)
    val tracker = remember { FocusTracker() }
    val scope = rememberCoroutineScope()
    val restore = rememberFocusRestore(
        remember {
            dest.focusKey?.takeIf { key ->
                (key == FocusKeys.WATCH && primary != null) || key == MORE_KEY ||
                    (showList && ordered.any { FocusKeys.episode(it.id) == key })
            } ?: when {
                primary != null -> FocusKeys.WATCH
                showList -> FocusKeys.episode(ordered.first().id)
                else -> MORE_KEY
            }
        },
    )
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, gridState.firstVisibleItemIndex) }

    // Back from the player: put the remembered episode on screen so it can take focus. Items before
    // the tiles: the hero and the section heading.
    LaunchedEffect(Unit) {
        val index = ordered.indexOfFirst { FocusKeys.episode(it.id) == restore.key }
        if (showList && index >= 0) gridState.revealIndex(2 + index)
    }

    // A short message at the bottom. "Opening..." stays until the check is done; others fade after a few seconds.
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(status) {
        val shown = status
        if (shown != null && !checking) {
            delay(4_000)
            if (status == shown) status = null
        }
    }

    fun startPlayback(focusKey: String, episode: Episode?, name: String) {
        if (checking) return
        checking = true
        status = "Opening $name\u2026"
        scope.launch {
            val found: Result<Playable?> =
                if (episode != null) suspendCatching { repo.streamUrl(episode.id)?.let { Playable(episode, it) } }
                else vm.findPlayable(drama)
            checking = false
            found.fold(
                onSuccess = { playable ->
                    if (playable == null) {
                        status = if (episode != null) "${name.replaceFirstChar { it.uppercase() }} isn't released yet." else "Nothing is released to watch yet."
                    } else {
                        status = null
                        vm.saveFocus(focusKey, gridState.firstVisibleItemIndex)
                        vm.play(drama.playerDest(playable.episode), playable.url)
                    }
                },
                onFailure = { status = errorMessage(it) },
            )
        }
    }

    val gap = cardGap()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(top = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }, contentType = "hero") {
                DetailHero(
                    drama = drama,
                    primary = primary,
                    hasEpisodes = ordered.isNotEmpty(),
                    restore = restore,
                    tracker = tracker,
                    onFocusTop = { scope.launch { gridState.animateScrollToItem(0) } },
                    onPlay = { action ->
                        startPlayback(FocusKeys.WATCH, action.episode, action.name)
                    },
                )
            }
            if (showList) {
                item(key = "list-title", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
                    Text(
                        if (partsMode) "Parts (${ordered.size})" else "Episodes (${ordered.size})",
                        style = KhTheme.typography.title,
                        modifier = Modifier.padding(top = KhTheme.spacing.m),
                    )
                }
                itemsIndexed(ordered, key = { _, ep -> ep.id }, contentType = { _, _ -> "episode" }) { index, ep ->
                    val key = FocusKeys.episode(ep.id)
                    val label = if (partsMode) "Part ${index + 1}" else "EP ${formatEpisodeNumber(ep.number)}"
                    val savedProgress = entry?.takeIf { it.episodeId == ep.id && it.resumable }?.progress
                    EpisodeTile(
                        label = label,
                        positionMs = positions[ep.id] ?: 0L,
                        progress = savedProgress,
                        onClick = { startPlayback(key, ep, label) },
                        modifier = Modifier
                            .focusIfRestoring(restore, key)
                            .onFocusChanged { if (it.isFocused) tracker.key = key },
                    )
                }
            }
        }
        status?.let { message ->
            StatusPill(
                message,
                Modifier.align(Alignment.BottomCenter).padding(bottom = KhTheme.spacing.safeVertical + KhTheme.spacing.s),
            )
        }
    }
}

/**
 * Which button to show, and what it plays.
 * Saved progress on an unfinished episode: resume it. Finished one: the next episode. Nothing saved:
 * the first (lowest-numbered) episode, found when pressed because later ones may not be out yet.
 */
private fun primaryAction(drama: Drama, ordered: List<Episode>, entry: WatchEntry?): PrimaryAction? {
    if (ordered.isEmpty()) return null
    val movie = drama.isMovie()
    val saved = entry?.let { e -> ordered.firstOrNull { it.id == e.episodeId } }
    if (entry != null && saved != null) {
        if (entry.resumable) {
            return when {
                !movie -> "EP ${formatEpisodeNumber(saved.number)}".let { PrimaryAction("Resume $it", saved, it) }
                ordered.size > 1 -> "Part ${ordered.indexOf(saved) + 1}".let { PrimaryAction("Resume $it", saved, it) }
                else -> PrimaryAction("Resume", saved, "the movie")
            }
        }
        if (!movie) {
            ordered.firstOrNull { it.number > saved.number }?.let {
                return "EP ${formatEpisodeNumber(it.number)}".let { n -> PrimaryAction("Play $n", it, n) }
            }
        }
    }
    if (movie) return PrimaryAction("Watch movie", null, "the movie")
    val first = "EP ${formatEpisodeNumber(ordered.first().number)}"
    return PrimaryAction("Play $first", null, first)
}

@Composable
private fun DetailHero(
    drama: Drama,
    primary: PrimaryAction?,
    hasEpisodes: Boolean,
    restore: FocusRestore,
    tracker: FocusTracker,
    onFocusTop: () -> Unit,
    onPlay: (PrimaryAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val collapsedText = remember(drama.id) { drama.description?.replace(Regex("\\s+"), " ")?.trim().orEmpty() }
    val fullText = remember(drama.id) { drama.description?.trim().orEmpty() }

    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = heroHeight())) {
        val narrow = maxWidth < 600.dp
        HeroBackdrop(url = drama.thumbnail, title = drama.title, modifier = Modifier.matchParentSize())
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (narrow) 1f else 0.55f)
                .padding(
                    top = KhTheme.spacing.xxl,
                    start = screenStart(),
                    end = if (narrow) KhTheme.spacing.safeHorizontal else KhTheme.spacing.l,
                    bottom = KhTheme.spacing.m,
                ),
            verticalArrangement = Arrangement.spacedBy(KhTheme.spacing.s),
        ) {
            Text(
                drama.title?.trim().orEmpty(),
                style = if (narrow) KhTheme.typography.headline else KhTheme.typography.display,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = drama.metaParts(fullDate = true)
            if (meta.isNotEmpty()) {
                Text(meta.joinToString("  •  "), style = KhTheme.typography.label, color = KhColors.TextSecondary, maxLines = 2)
            }
            if (collapsedText.isNotEmpty()) {
                Text(
                    if (expanded) fullText else collapsedText,
                    style = KhTheme.typography.body,
                    color = KhColors.TextPrimary.copy(alpha = 0.9f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                )
                if (overflows || expanded) {
                    KhChip(
                        text = if (expanded) "Less" else "More",
                        onClick = { expanded = !expanded },
                        modifier = Modifier
                            .focusIfRestoring(restore, MORE_KEY)
                            .onFocusChanged { if (it.isFocused) { tracker.key = MORE_KEY; onFocusTop() } },
                    )
                }
            }
            Row(Modifier.padding(top = KhTheme.spacing.s), verticalAlignment = Alignment.CenterVertically) {
                if (primary != null) {
                    KhButton(
                        primary.label,
                        onClick = { onPlay(primary) },
                        primary = true,
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier
                            .focusIfRestoring(restore, FocusKeys.WATCH)
                            .onFocusChanged { if (it.isFocused) { tracker.key = FocusKeys.WATCH; onFocusTop() } },
                    )
                } else if (!hasEpisodes) {
                    Text("No episodes yet.", style = KhTheme.typography.title, color = KhColors.TextSecondary)
                }
            }
        }
    }
}

/** One episode (or movie part). The red bar is how far the viewer got, when that is known. */
@Composable
private fun EpisodeTile(
    label: String,
    positionMs: Long,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(64.dp),
        shape = KhTheme.shapes.tile,
    ) { focused ->
        Box(Modifier.fillMaxSize().background(if (focused) KhColors.Elevated else KhColors.Surface)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = KhTheme.typography.label, color = KhColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Only the last watched episode has a known length. The others show where the viewer stopped.
                if (progress == null && positionMs > 0L) {
                    Text(
                        "At ${formatPosition(positionMs)}",
                        style = KhTheme.typography.caption,
                        color = KhColors.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(KhColors.TextPrimary.copy(alpha = 0.2f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(4.dp).background(KhColors.Brand))
            }
        }
    }
}
