package com.kisskh.tv.ui.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaEntry
import com.kisskh.tv.api.media.MediaKeys
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbEpisode
import com.kisskh.tv.api.media.TmdbPaths
import com.kisskh.tv.api.media.TmdbSeasonInfo
import com.kisskh.tv.api.media.TmdbTitleDetails
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.FocusRestore
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.KhActionButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.launch

/** Focus key of the main button (Play or Resume). Focus starts there, like Netflix. */
private const val START_KEY = "start"

/** Focus key of the episode the main button would play, for the Episodes button to jump to. */
private const val EPISODE_START = "ep:start"

/** List index of the season block, and of More Like This on a movie. */
private const val SECOND_ITEM = 1

/** List index of the first episode row: after the hero and the season header. */
private const val FIRST_EPISODE_ITEM = 2

private fun episodeKey(number: Int) = "ep:$number"

/**
 * A title page laid out like Netflix.
 *
 * - TV: the key art fills the screen; the logo, facts, synopsis, cast and buttons sit on its left.
 *   The seasons and episodes, then More Like This, scroll up over the art, which darkens behind them.
 * - Phone: the art on top, then the title, a full-width Play, the synopsis, episodes and more.
 *
 * Focus starts on Play / Resume. Coming back from the player restores the episode that was playing.
 */
@Composable
fun MediaDetailScreen(vm: AppViewModel, library: Library, dest: Dest.MediaDetail) {
    val state by remember(dest.kind, dest.tmdbId) { vm.media.detail(dest.kind, dest.tmdbId) }.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> LoadingState(message = "Loading")
        is UiState.Error -> ErrorState(s.message, onRetry = { vm.media.retryDetail(dest.kind, dest.tmdbId) }, autoFocus = true)
        is UiState.Empty -> EmptyState(s.message ?: "This title isn't available.")
        is UiState.Success -> DetailBody(vm, library, dest, s.data)
    }
}

@Composable
private fun DetailBody(vm: AppViewModel, library: Library, dest: Dest.MediaDetail, details: TmdbTitleDetails) {
    val tv = KhTheme.formFactor == FormFactor.TV
    val kind = dest.kind
    val title = details.title
    val saved = remember(library, title.id, vm.media.progress.entries().size) {
        vm.media.progress.entries().firstOrNull { it.source == library.key && it.id == title.id && it.resumable }
    }
    val tracker = remember { FocusTracker() }
    val restore = rememberFocusRestore(remember { dest.focusKey ?: START_KEY })
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, 0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Specials (season 0) are left out: Febbox and the embed links only take season 1 and up.
    val seasons = remember(details) { details.seasons.filter { it.number >= 1 } }
    val hasEpisodes = !kind.isMovie && seasons.isNotEmpty()
    var seasonNumber by remember(title.id) {
        mutableIntStateOf(saved?.season?.takeIf { n -> seasons.any { it.number == n } } ?: seasons.firstOrNull()?.number ?: 1)
    }
    val seasonFlow = remember(title.id, seasonNumber, hasEpisodes) { if (hasEpisodes) vm.media.season(title.id, seasonNumber) else null }
    val seasonState = seasonFlow?.collectAsStateWithLifecycle()?.value
    val episodes = (seasonState as? UiState.Success)?.data.orEmpty()
    val today = remember { TmdbPaths.todayUtc() }
    val resumeAt = if (saved != null && saved.season == seasonNumber) episodes.firstOrNull { it.number == saved.episode } else null
    val start = resumeAt ?: episodes.firstOrNull { it.aired(today) } ?: episodes.firstOrNull()
    // Episode rows are separate list items, so only the ones on screen are built. Back from the player, or the
    // Episodes button: bring the wanted row on screen first, so it exists and can take focus.
    LaunchedEffect(restore.key, restore.pending, episodes) {
        val wanted = restore.key ?: return@LaunchedEffect
        if (!restore.pending) return@LaunchedEffect
        val row = when {
            wanted == EPISODE_START -> start?.let { episodes.indexOf(it) } ?: -1
            wanted.startsWith("ep:") -> episodes.indexOfFirst { episodeKey(it.number) == wanted }
            else -> -1
        }
        when {
            row >= 0 -> listState.revealIndex(FIRST_EPISODE_ITEM + row)
            wanted.startsWith("season:") -> listState.revealIndex(SECOND_ITEM)
        }
    }
    val play: () -> Unit = {
        vm.saveFocus(START_KEY, 0)
        if (saved != null) vm.resumeEntry(saved) else vm.playMedia(library, kind, details, season = 1, episode = null)
    }
    val jump: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(SECOND_ITEM)
            restore.request(if (kind.isMovie) "rec:first" else EPISODE_START)
        }
    }
    val secondLabel = when {
        !kind.isMovie -> "Episodes"
        details.recommendations.isNotEmpty() -> "More Like This"
        else -> null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        if (tv) {
            HeroArt(title.hero, Modifier.fillMaxSize(), widthFraction = 0.80f)
            val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 80 } }
            val dim by animateFloatAsState(
                targetValue = if (scrolled) 0.82f else 0f,
                animationSpec = tween(KhMotion.CrossfadeMillis, easing = KhMotion.EaseOutStrong),
                label = "detailDim",
            )
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(KhColors.Background))
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "hero") {
                val heroModifier = if (tv) Modifier.height(screenHeight * 0.88f) else Modifier
                Box(heroModifier.fillMaxWidth()) {
                    Hero(
                        details = details,
                        kind = kind,
                        saved = saved,
                        secondLabel = secondLabel,
                        restore = restore,
                        tracker = tracker,
                        onPlay = play,
                        onSecond = jump,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
            if (!kind.isMovie) {
                item(key = "seasons") {
                    SeasonHeader(
                        seasons = seasons,
                        seasonNumber = seasonNumber,
                        onSeason = { seasonNumber = it },
                        state = seasonState,
                        onRetry = { vm.media.retrySeason(title.id, seasonNumber) },
                        restore = restore,
                        tracker = tracker,
                    )
                }
                items(episodes, key = { episodeKey(it.number) }) { episode ->
                    val rowKey = episodeKey(episode.number)
                    EpisodeRow(
                        episode = episode,
                        upcoming = if (episode.aired(today)) null else airsLabel(episode.airDate),
                        resume = episode === resumeAt,
                        progress = vm.media.progress.fraction(MediaKeys.episode(library.key, title.id, seasonNumber, episode.number)),
                        onClick = {
                            vm.saveFocus(rowKey, 0)
                            vm.playMedia(library, kind, details, seasonNumber, episode)
                        },
                        modifier = Modifier
                            .let { if (episode === start) it.focusIfRestoring(restore, EPISODE_START) else it }
                            .focusIfRestoring(restore, rowKey)
                            .onFocusChanged { if (it.isFocused) tracker.key = rowKey },
                    )
                }
            }
            if (details.recommendations.isNotEmpty()) {
                item(key = "more") { MoreLikeThis(vm, details, restore, tracker, tv) }
            }
        }
    }
}

@Composable
private fun Hero(
    details: TmdbTitleDetails,
    kind: MediaKind,
    saved: MediaEntry?,
    secondLabel: String?,
    restore: FocusRestore,
    tracker: FocusTracker,
    onPlay: () -> Unit,
    onSecond: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = KhTheme.formFactor == FormFactor.TV
    val title = details.title
    Column(modifier.fillMaxWidth()) {
        if (!tv) {
            Artwork(title.hero, title.name, Modifier.fillMaxWidth().aspectRatio(16f / 9f), titleOnFailure = false)
        }
        Column(
            Modifier
                .padding(start = screenStart(), end = if (tv) 0.dp else 16.dp, top = if (tv) 0.dp else 12.dp, bottom = if (tv) 8.dp else 0.dp)
                .then(if (tv) Modifier.widthIn(max = 560.dp) else Modifier.fillMaxWidth()),
            verticalArrangement = Arrangement.spacedBy(if (tv) 10.dp else 8.dp),
        ) {
            TitleLogo(
                logo = details.logo,
                name = title.name,
                height = if (tv) 110.dp else 72.dp,
                style = if (tv) heroTitleStyle() else KhTheme.typography.headline,
                maxWidth = if (tv) 460.dp else 300.dp,
            )
            MetaLine(
                year = title.year,
                certification = details.certification,
                length = lengthOf(details),
                vote = title.voteText(),
            )
            GenreLine(details.genres)
            if (!tv) {
                PlayButtons(kind, saved, secondLabel = null, restore, tracker, onPlay, onSecond, fullWidth = true)
            }
            if (title.overview.isNotBlank()) {
                Text(
                    title.overview,
                    style = if (tv) KhTheme.typography.synopsis else KhTheme.typography.body,
                    color = KhColors.TextPrimary.copy(alpha = 0.9f),
                    maxLines = if (tv) 4 else 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (details.cast.isNotEmpty()) {
                Text(
                    "Starring: " + details.cast.joinToString(", "),
                    style = KhTheme.typography.caption,
                    color = KhColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tv) {
                PlayButtons(kind, saved, secondLabel, restore, tracker, onPlay, onSecond, fullWidth = false)
            }
        }
    }
}

@Composable
private fun PlayButtons(
    kind: MediaKind,
    saved: MediaEntry?,
    secondLabel: String?,
    restore: FocusRestore,
    tracker: FocusTracker,
    onPlay: () -> Unit,
    onSecond: () -> Unit,
    fullWidth: Boolean,
) {
    Row(
        Modifier.padding(top = 6.dp).then(if (fullWidth) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KhActionButton(
            text = playLabel(saved, kind.isMovie),
            onClick = onPlay,
            primary = true,
            icon = Icons.Filled.PlayArrow,
            modifier = Modifier
                .then(if (fullWidth) Modifier.weight(1f) else Modifier.widthIn(min = 150.dp).width(IntrinsicSize.Max))
                .focusIfRestoring(restore, START_KEY)
                .onFocusChanged { if (it.isFocused) tracker.key = START_KEY },
        )
        if (secondLabel != null) {
            KhActionButton(
                text = secondLabel,
                onClick = onSecond,
                icon = Icons.Filled.List,
                modifier = Modifier.widthIn(min = 150.dp).width(IntrinsicSize.Max),
            )
        }
    }
}

/**
 * The top of the episode list: the heading, the season chips, and the loading, error or empty message.
 * The rows are separate list items (a long show has hundreds), so only the ones on screen get built.
 */
@Composable
private fun SeasonHeader(
    seasons: List<TmdbSeasonInfo>,
    seasonNumber: Int,
    onSeason: (Int) -> Unit,
    state: UiState<List<TmdbEpisode>>?,
    onRetry: () -> Unit,
    restore: FocusRestore,
    tracker: FocusTracker,
) {
    val tv = KhTheme.formFactor == FormFactor.TV
    Column(Modifier.fillMaxWidth().padding(top = if (tv) 8.dp else 20.dp)) {
        Text(
            "Episodes",
            style = KhTheme.typography.rowTitle,
            color = KhColors.TextPrimary,
            modifier = Modifier.padding(start = screenStart(), bottom = 8.dp).semantics { heading() },
        )
        if (seasons.isEmpty()) {
            EmptyState("No episodes yet.", modifier = Modifier.fillMaxWidth().height(120.dp))
            return
        }
        if (seasons.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(start = screenStart(), end = 24.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(seasons, key = { it.number }) { season ->
                    val key = "season:${season.number}"
                    KhChip(
                        text = season.name,
                        selected = season.number == seasonNumber,
                        onClick = { onSeason(season.number) },
                        modifier = Modifier
                            .focusIfRestoring(restore, key)
                            .onFocusChanged { if (it.isFocused) tracker.key = key },
                    )
                }
            }
        }
        when (val s = state) {
            null, UiState.Loading -> LoadingState(message = "Loading episodes", modifier = Modifier.fillMaxWidth().height(160.dp))
            is UiState.Error -> ErrorState(s.message, onRetry = onRetry, modifier = Modifier.fillMaxWidth().height(180.dp))
            is UiState.Empty -> EmptyState(s.message ?: "No episodes in this season.", modifier = Modifier.fillMaxWidth().height(120.dp))
            is UiState.Success -> Unit
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: TmdbEpisode,
    upcoming: String?,
    resume: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = KhTheme.formFactor == FormFactor.TV
    val stillWidth: Dp = if (tv) 200.dp else 132.dp
    // Not out yet: shown, dimmed and not selectable, so the remote skips it instead of opening a player
    // that can never load.
    KhFocusable(
        onClick = onClick,
        enabled = upcoming == null,
        modifier = modifier
            .graphicsLayer { alpha = if (upcoming == null) 1f else 0.5f }
            .padding(start = screenStart() - 8.dp, end = if (tv) KhTheme.spacing.safeHorizontal else 8.dp, top = 2.dp, bottom = 2.dp)
            .then(if (tv) Modifier.widthIn(max = 880.dp) else Modifier)
            .fillMaxWidth(),
        shape = KhTheme.shapes.button,
        scaleAndLift = false,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) KhColors.Elevated else Color.Transparent, KhTheme.shapes.button)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(stillWidth).aspectRatio(16f / 9f)) {
                Artwork(episode.still, "Episode ${episode.number}", Modifier.fillMaxSize())
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(if (tv) 40.dp else 32.dp)
                        .graphicsLayer { alpha = if (upcoming == null && (focused || !tv)) 1f else 0f }
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (tv) 26.dp else 22.dp))
                }
                if (progress != null) ProgressBar(progress, Modifier.align(Alignment.BottomStart))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${episode.number}. ${episode.name}",
                        style = KhTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                        color = KhColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val runtime = runtimeText(episode.runtime)
                    if (runtime != null) {
                        Text(runtime, style = KhTheme.typography.caption, color = KhColors.TextSecondary, modifier = Modifier.padding(start = 10.dp))
                    }
                    if (upcoming != null) {
                        Text(
                            upcoming,
                            style = KhTheme.typography.caption,
                            color = KhColors.TextPrimary,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                    if (resume) {
                        Text(
                            "RESUME",
                            style = KhTheme.typography.badge.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                            color = KhColors.Brand,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
                if (episode.overview.isNotBlank()) {
                    Text(
                        episode.overview,
                        style = KhTheme.typography.caption,
                        color = KhColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreLikeThis(vm: AppViewModel, details: TmdbTitleDetails, restore: FocusRestore, tracker: FocusTracker, tv: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            "More Like This",
            style = KhTheme.typography.rowTitle,
            color = KhColors.TextPrimary,
            modifier = Modifier.padding(start = screenStart(), bottom = 8.dp).semantics { heading() },
        )
        LazyRow(
            contentPadding = PaddingValues(start = screenStart(), end = 32.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(details.recommendations.size, key = { "${details.recommendations[it].mediaType}:${details.recommendations[it].id}" }) { index ->
                val item = details.recommendations[index]
                val key = "rec:${item.mediaType}:${item.id}"
                MediaCard(
                    title = item,
                    poster = !tv,
                    onClick = {
                        vm.saveFocus(key, 0)
                        vm.openTitle(item, if (item.mediaType == "tv") MediaKind.TV else MediaKind.MOVIE)
                    },
                    modifier = Modifier
                        .width(if (tv) 224.dp else 112.dp)
                        .let { if (index == 0) it.focusIfRestoring(restore, "rec:first") else it }
                        .focusIfRestoring(restore, key)
                        .onFocusChanged { if (it.isFocused) tracker.key = key },
                )
            }
        }
    }
}

/** "Airs Oct 23" for an episode that is not out yet. */
private fun airsLabel(date: String?): String {
    val parts = date?.split("-")?.mapNotNull { it.toIntOrNull() } ?: return "Coming soon"
    if (parts.size != 3) return "Coming soon"
    val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").getOrNull(parts[1] - 1)
        ?: return "Coming soon"
    return "Airs $month ${parts[2]}"
}
