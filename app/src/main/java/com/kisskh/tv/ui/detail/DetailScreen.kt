package com.kisskh.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Episode
import com.kisskh.tv.api.suspendCatching
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.FocusKeys
import com.kisskh.tv.ui.Playable
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.KhActionButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.StatusPill
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.episodeRanges
import com.kisskh.tv.ui.errorMessage
import com.kisskh.tv.ui.isMovie
import com.kisskh.tv.ui.metaParts
import com.kisskh.tv.ui.orderedEpisodes
import com.kisskh.tv.ui.playerDest
import com.kisskh.tv.ui.primaryAction
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhGradients
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MORE_KEY = "detail:more"

/**
 * Title page. TV puts actions in a column over the artwork and opens episodes in a side panel
 * (ranges, then rows). Phone stacks a 16:9 still, a full-width play button and the same rows.
 * A movie with one part has no list. Playback is checked first; a countdown episode is "Not released yet".
 */
@Composable
fun DetailScreen(vm: AppViewModel, dest: Dest.Detail) {
    val state by remember(dest.dramaId) { vm.detail(dest.dramaId) }.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> LoadingState()
        is UiState.Error -> ErrorState(s.message, onRetry = { vm.retryDetail(dest.dramaId) }, autoFocus = true)
        is UiState.Empty -> ErrorState(s.message ?: "This title isn't available.", onRetry = { vm.retryDetail(dest.dramaId) }, autoFocus = true)
        is UiState.Success -> DetailContent(vm, dest, s.data)
    }
}

@Composable
private fun DetailContent(vm: AppViewModel, dest: Dest.Detail, drama: Drama) {
    val tv = KhTheme.formFactor == FormFactor.TV
    val ordered = remember(drama) { drama.orderedEpisodes() }
    val parts = drama.isMovie() && ordered.size > 1
    val showList = ordered.isNotEmpty() && (!drama.isMovie() || parts)
    val ranges = remember(ordered) { episodeRanges(ordered) }
    val partNumbers = remember(ordered) { ordered.mapIndexed { i, ep -> ep.id to (i + 1) }.toMap() }
    val entry = remember(drama.id) { vm.repo.history.entry(drama.id) }
    val positions = remember(drama.id) { ordered.associate { it.id to vm.repo.loadPosition(it.id) }.filterValues { it > 0L } }
    val primary = remember(drama, entry) { primaryAction(drama, ordered, entry) }
    val progressOf: (Episode) -> Float? = { ep -> entry?.takeIf { it.episodeId == ep.id && it.resumable }?.progress }

    val tracker = remember { FocusTracker() }
    val scope = rememberCoroutineScope()
    val restore = rememberFocusRestore(
        remember {
            dest.focusKey?.takeIf { key ->
                (key == FocusKeys.WATCH && primary != null) || key == MORE_KEY ||
                    (showList && ordered.any { FocusKeys.episode(it.id) == key })
            } ?: when {
                primary != null -> FocusKeys.WATCH
                else -> MORE_KEY
            }
        },
    )
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, 0) }

    var unavailable by remember { mutableStateOf(setOf<Int>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(status, checking) {
        val shown = status
        if (shown != null && !checking) {
            delay(4_000)
            if (status == shown) status = null
        }
    }

    fun startPlayback(focusKey: String, episode: Episode?, name: String) {
        if (checking) return
        checking = true
        status = "Opening $name…"
        scope.launch {
            val found: Result<Playable?> = if (episode != null) {
                suspendCatching { vm.repo.streamUrl(episode.id)?.let { Playable(episode, it) } }
            } else {
                vm.findPlayable(drama)
            }
            checking = false
            found.fold(
                onSuccess = { playable ->
                    if (playable == null) {
                        if (episode != null) unavailable = unavailable + episode.id
                        status = if (episode != null) "$name isn't released yet." else "Nothing is released to watch yet."
                    } else {
                        status = null
                        vm.saveFocus(focusKey, 0)
                        vm.play(drama.playerDest(playable.episode), playable.url)
                    }
                },
                onFailure = { status = errorMessage(it) },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (tv) {
            TvDetail(
                drama = drama,
                primary = primary,
                showList = showList,
                parts = parts,
                ranges = ranges,
                partNumbers = partNumbers,
                positions = positions,
                progressOf = progressOf,
                unavailable = unavailable,
                restore = restore,
                tracker = tracker,
                initialEpisodeId = dest.focusedEpisodeId ?: primary?.episode?.id,
                onPlay = { focus, episode, name -> startPlayback(focus, episode, name) },
            )
        } else {
            PhoneDetail(
                dest = dest,
                drama = drama,
                primary = primary,
                showList = showList,
                parts = parts,
                ranges = ranges,
                ordered = ordered,
                partNumbers = partNumbers,
                positions = positions,
                progressOf = progressOf,
                unavailable = unavailable,
                onPlay = { focus, episode, name -> startPlayback(focus, episode, name) },
            )
        }
        status?.let { message ->
            StatusPill(
                message,
                Modifier.align(Alignment.BottomCenter).padding(bottom = KhTheme.spacing.safeVertical),
            )
        }
    }
}

@Composable
private fun TvDetail(
    drama: Drama,
    primary: com.kisskh.tv.ui.PrimaryAction?,
    showList: Boolean,
    parts: Boolean,
    ranges: List<com.kisskh.tv.ui.EpisodeRange>,
    partNumbers: Map<Int, Int>,
    positions: Map<Int, Long>,
    progressOf: (Episode) -> Float?,
    unavailable: Set<Int>,
    restore: com.kisskh.tv.ui.components.FocusRestore,
    tracker: FocusTracker,
    initialEpisodeId: Int?,
    onPlay: (String, Episode?, String) -> Unit,
) {
    var panelOpen by remember { mutableStateOf(restore.key?.startsWith("ep:") == true && showList) }
    var returnFocus by remember { mutableStateOf(false) }
    val episodesButton = remember { FocusRequester() }
    BackHandler(enabled = panelOpen) {
        panelOpen = false
        returnFocus = true
    }
    LaunchedEffect(panelOpen) {
        if (!panelOpen && returnFocus) {
            returnFocus = false
            withFrameNanos { }
            runCatching { episodesButton.requestFocus() }
        }
    }
    val listLabel = if (parts) "Parts" else "Episodes"

    Box(Modifier.fillMaxSize()) {
        Artwork(
            url = drama.thumbnail,
            title = drama.title,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.70f),
            fitPortrait = true,
            fadeMillis = 200,
            titleOnFailure = false,
        )
        Box(Modifier.fillMaxSize().background(KhGradients.LeftFade))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .background(KhGradients.BottomFade),
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = screenStart(), end = 24.dp, top = 24.dp, bottom = 24.dp)
                    .widthIn(max = 480.dp)
                    .heightIn(max = maxHeight - 48.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    drama.title?.trim().orEmpty().ifEmpty { "Untitled" },
                    fontSize = 48.sp,
                    lineHeight = 52.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = drama.metaParts(fullDate = true)
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString("  •  "), style = KhTheme.typography.meta, color = KhColors.TextSecondary, maxLines = 2)
                }
                Synopsis(drama, restore, tracker)
                if (primary != null) {
                    KhActionButton(
                        text = primary.label,
                        onClick = { onPlay(FocusKeys.WATCH, primary.episode, primary.name) },
                        primary = true,
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .focusIfRestoring(restore, FocusKeys.WATCH)
                            .onFocusChanged { if (it.isFocused) tracker.key = FocusKeys.WATCH },
                    )
                } else if (!showList) {
                    Text("No episodes yet.", style = KhTheme.typography.title, color = KhColors.TextSecondary)
                }
                if (showList) {
                    KhActionButton(
                        text = listLabel,
                        onClick = { panelOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(episodesButton)
                            .onFocusChanged { if (it.isFocused) tracker.key = listLabel },
                    )
                }
            }
        }
        if (panelOpen && ranges.isNotEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            TvEpisodePanel(
                ranges = ranges,
                parts = parts,
                partNumberOf = { partNumbers[it.id] ?: 1 },
                positions = positions,
                progressOf = progressOf,
                unavailable = unavailable,
                initialEpisodeId = initialEpisodeId,
                onPlay = { episode ->
                    val n = partNumbers[episode.id] ?: 1
                    val name = if (parts) "Part $n" else "Episode ${com.kisskh.tv.ui.components.formatEpisodeNumber(episode.number)}"
                    onPlay(FocusKeys.episode(episode.id), episode, name)
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun PhoneDetail(
    dest: Dest.Detail,
    drama: Drama,
    primary: com.kisskh.tv.ui.PrimaryAction?,
    showList: Boolean,
    parts: Boolean,
    ranges: List<com.kisskh.tv.ui.EpisodeRange>,
    ordered: List<Episode>,
    partNumbers: Map<Int, Int>,
    positions: Map<Int, Long>,
    progressOf: (Episode) -> Float?,
    unavailable: Set<Int>,
    onPlay: (String, Episode?, String) -> Unit,
) {
    var rangeStart by remember { mutableIntStateOf(initialRangeStart(ranges, dest.focusedEpisodeId ?: primary?.episode?.id)) }
    val shown = ranges.firstOrNull { it.start == rangeStart }?.episodes ?: ordered
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = dest.scrollIndex)
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val collapsed = remember(drama.id) { drama.description?.replace(Regex("\\s+"), " ")?.trim().orEmpty() }
    val full = remember(drama.id) { drama.description?.trim().orEmpty() }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "hero") {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    Artwork(drama.thumbnail, drama.title, Modifier.fillMaxSize(), titleOnFailure = false, fadeMillis = 180)
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.55f)
                            .background(KhGradients.BottomFade),
                    )
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        drama.title?.trim().orEmpty().ifEmpty { "Untitled" },
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = drama.metaParts(fullDate = true)
                    if (meta.isNotEmpty()) {
                        Text(meta.joinToString("  •  "), style = KhTheme.typography.caption, color = KhColors.TextSecondary)
                    }
                    if (primary != null) {
                        KhActionButton(
                            text = primary.label,
                            onClick = { onPlay(FocusKeys.WATCH, primary.episode, primary.name) },
                            primary = true,
                            icon = Icons.Filled.PlayArrow,
                            height = 48.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (!showList) {
                        Text("No episodes yet.", color = KhColors.TextSecondary)
                    }
                    if (collapsed.isNotEmpty()) {
                        Text(
                            if (expanded) full else collapsed,
                            style = KhTheme.typography.body,
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                        )
                        if (overflows || expanded) {
                            KhChip(text = if (expanded) "Less" else "More", onClick = { expanded = !expanded }, outlined = true)
                        }
                    }
                }
            }
        }
        if (showList && ordered.size > 50 && ranges.size > 1) {
            item(key = "ranges") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ranges, key = { it.start }) { range ->
                        KhChip(
                            text = "${range.start}-${range.end}",
                            outlined = true,
                            marked = range.start == rangeStart,
                            onClick = { rangeStart = range.start },
                        )
                    }
                }
            }
        }
        if (showList) {
            item(key = "list-title") {
                Text(
                    if (parts) "Parts" else "Episodes",
                    style = KhTheme.typography.rowTitle,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(shown, key = { it.id }) { episode ->
                val n = partNumbers[episode.id] ?: 1
                val name = if (parts) "Part $n" else "Episode ${com.kisskh.tv.ui.components.formatEpisodeNumber(episode.number)}"
                EpisodeRow(
                    episode = episode,
                    parts = parts,
                    partNumber = n,
                    positionMs = positions[episode.id] ?: 0L,
                    progress = progressOf(episode),
                    unavailable = episode.id in unavailable,
                    onClick = { onPlay(FocusKeys.episode(episode.id), episode, name) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Synopsis(
    drama: Drama,
    restore: com.kisskh.tv.ui.components.FocusRestore,
    tracker: FocusTracker,
) {
    var expanded by remember(drama.id) { mutableStateOf(false) }
    var overflows by remember(drama.id) { mutableStateOf(false) }
    val collapsed = remember(drama.id) { drama.description?.replace(Regex("\\s+"), " ")?.trim().orEmpty() }
    val full = remember(drama.id) { drama.description?.trim().orEmpty() }
    if (collapsed.isEmpty()) return
    Text(
        if (expanded) full else collapsed,
        style = KhTheme.typography.synopsis,
        color = KhColors.TextPrimary.copy(alpha = 0.92f),
        maxLines = if (expanded) 8 else 3,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
    )
    if (overflows || expanded) {
        KhChip(
            text = if (expanded) "Less" else "More",
            onClick = { expanded = !expanded },
            outlined = true,
            modifier = Modifier
                .focusIfRestoring(restore, MORE_KEY)
                .onFocusChanged { if (it.isFocused) tracker.key = MORE_KEY },
        )
    }
}
