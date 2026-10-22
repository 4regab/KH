package com.kisskh.tv.ui.shelf.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.SeasonBlock
import com.kisskh.tv.download.DownloadButton
import com.kisskh.tv.download.TitleDownloads
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhProgressBar
import com.kisskh.tv.ui.components.edge
import com.kisskh.tv.ui.components.isCompactWidth
import com.kisskh.tv.ui.components.isTv
import com.kisskh.tv.ui.shelf.SkeletonBlock
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/*
 * The episode list of a title page: a heading, the season picker, then one row per episode.
 * Both layouts use the same rows. They differ only in sizes (see [EpisodeRow]).
 */

/** What the episode list needs from the screen. The screen owns marks, progress and playing. */
internal class EpisodesSection(
    val movie: Boolean,
    val seasons: List<SeasonBlock>,
    val seasonNumber: Int,
    val onSeason: (Int) -> Unit,
    val episodes: UiState<List<EpisodeRef>>,
    /** Changes whenever a Watched mark changes, so rows read their mark again. */
    val marksVersion: Any,
    val isWatched: (EpisodeRef) -> Boolean,
    val progress: (EpisodeRef) -> Float?,
    val isCurrent: (EpisodeRef) -> Boolean,
    val today: String,
    /** The show's usual episode length, for episodes whose own length is unknown. Null for a movie. */
    val fallbackMinutes: Int?,
    val onPlay: (EpisodeRef, List<EpisodeRef>) -> Unit,
    val onNotAired: (EpisodeRef) -> Unit,
    val onToggleWatched: (EpisodeRef, Boolean, List<EpisodeRef>) -> Unit,
    val onRetry: () -> Unit,
    val downloads: TitleDownloads,
    val showDownload: Boolean,
)

internal fun LazyListScope.episodesSection(section: EpisodesSection) {
    item(key = "episodes-title") {
        val count = (section.episodes as? UiState.Success)?.data?.size ?: 0
        SectionTitle(if (section.movie) "Parts" else "Episodes", count = count.takeIf { it > 0 })
    }
    if (!section.movie && seasonPickerFor(section.seasons.size) != SeasonPicker.NONE) {
        item(key = "seasons") {
            SeasonPickerRow(section.seasons, section.seasonNumber, section.onSeason)
        }
    }
    when (val eps = section.episodes) {
        UiState.Loading -> items(3, key = { "eps-loading:$it" }) { EpisodeRowSkeleton() }
        is UiState.Error -> item(key = "eps-error") {
            ErrorState(
                "Couldn't load the episodes. ${eps.message}",
                onRetry = section.onRetry,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }
        is UiState.Empty -> item(key = "eps-empty") {
            EmptyState(eps.message ?: "No episodes in this season yet.", modifier = Modifier.fillMaxWidth().height(120.dp))
        }
        is UiState.Success -> items(eps.data.distinctBy(::episodeKey), key = ::episodeKey) { episode ->
            val watched = remember(section.marksVersion, episode.season, episode.number) { section.isWatched(episode) }
            EpisodeRow(
                episode = episode,
                aired = episode.aired(section.today),
                progress = section.progress(episode),
                current = section.isCurrent(episode),
                watched = watched,
                fallbackMinutes = section.fallbackMinutes,
                onClick = { if (episode.aired(section.today)) section.onPlay(episode, eps.data) else section.onNotAired(episode) },
                showDownload = section.showDownload,
                downloads = section.downloads,
                episodes = eps.data,
                onToggleWatched = { section.onToggleWatched(episode, !watched, eps.data) },
            )
        }
    }
}

/** A row heading ("Episodes", "Cast", "More like this") with an optional count at the end. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier, count: Int? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = edge(), end = edge(), top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = KhTheme.typography.rowTitle,
            color = KhColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (count != null) Text("$count", style = detailCaption(), color = KhColors.TextSecondary)
    }
}

// ---- Episode row ----------------------------------------------------------------------------

/**
 * One episode: a 16:9 still with the progress bar along its bottom and a small play circle, then
 * "3. Name", the length (or "Coming Mar 5"), and the overview. The Download button and the Watched
 * check sit beside it. An episode that has not aired is dimmed, says when it comes, and cannot play.
 *
 * Sizes: the still is 220 dp wide on TV, 168 dp on a tablet and 132 dp on a phone. On a narrow phone
 * the overview goes under the still, across the whole row: beside the still it would be about 90 dp wide.
 */
@Composable
private fun EpisodeRow(
    episode: EpisodeRef,
    aired: Boolean,
    progress: Float?,
    current: Boolean,
    watched: Boolean,
    fallbackMinutes: Int?,
    onClick: () -> Unit,
    downloads: TitleDownloads,
    episodes: List<EpisodeRef>,
    onToggleWatched: () -> Unit,
    showDownload: Boolean,
) {
    val tv = isTv()
    val compact = isCompactWidth()
    val title = episodeTitle(episode)
    val overview = episodeOverview(episode)
    // A watched episode shows a full bar, whatever was saved while it played.
    val bar = if (aired) (if (watched) 1f else progress) else null
    val metaLine = joinDots(episodeMetaParts(episode, aired, watched, current, fallbackMinutes))
    val spoken = listOfNotNull(
        title,
        if (!aired) comingText(episode.airDate) else null,
        if (watched) "watched" else null,
        if (current) "continue here" else null,
    ).joinToString(", ")
    val stillWidth = when {
        tv -> 220.dp
        compact -> 132.dp
        else -> 168.dp
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = edge() - 6.dp, vertical = 2.dp),
        verticalAlignment = if (compact) Alignment.Top else Alignment.CenterVertically,
    ) {
        KhFocusable(
            onClick = onClick,
            modifier = Modifier.weight(1f).semantics { contentDescription = spoken },
            shape = KhTheme.shapes.button,
            scaleAndLift = false,
        ) { focused ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (aired) 1f else 0.5f }
                    .background(if (focused || current) KhColors.Surface else Color.Transparent, KhTheme.shapes.button)
                    .padding(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(stillWidth).aspectRatio(16f / 9f).clip(KhTheme.shapes.art)) {
                        Still(episode, title, showPlay = aired)
                        if (bar != null) KhProgressBar(bar, Modifier.align(Alignment.BottomStart), minFraction = 0.02f)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = KhTheme.typography.label, color = KhColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (metaLine.isNotEmpty()) {
                            Text(
                                metaLine,
                                style = detailCaption(),
                                color = if (current && !watched && aired) KhColors.TextPrimary else KhColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (overview != null && !compact) {
                            Text(overview, style = detailCaption(), color = KhColors.TextSecondary, maxLines = if (tv) 2 else 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (overview != null && compact) {
                    Text(
                        overview,
                        style = detailCaption(),
                        color = KhColors.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        // Nothing to download or mark until the episode is out.
        if (aired) {
            // On a narrow phone the buttons line up with the still, not the middle of the taller row.
            val buttons = if (compact) Modifier.padding(top = 19.dp) else Modifier
            if (showDownload) DownloadButton(downloads, episode, episodes, title, buttons)
            WatchedToggle(watched, title, onToggleWatched, buttons)
        }
    }
}

/** The episode picture, with a play circle over it. With no picture the episode number fills the tile. */
@Composable
private fun BoxScope.Still(episode: EpisodeRef, title: String, showPlay: Boolean) {
    if (episode.still != null) {
        Artwork(episode.still, title, Modifier.fillMaxSize())
        if (showPlay) {
            Box(
                Modifier.align(Alignment.Center).size(34.dp).background(KhColors.ScrimLight, CircleShape).border(1.dp, KhColors.OnArtBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = KhColors.OnArt, modifier = Modifier.size(20.dp))
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(KhColors.Elevated), contentAlignment = Alignment.Center) {
            Text(episode.number.toString(), style = KhTheme.typography.headline, color = KhColors.TextSecondary)
        }
    }
}

/** The 48 dp check at the end of an episode row: empty ring when unwatched, filled with a check once watched. */
@Composable
private fun WatchedToggle(watched: Boolean, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.size(48.dp).semantics { contentDescription = if (watched) "Mark $title as unwatched" else "Mark $title as watched" },
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

/** Grey blocks in the shape of an episode row, while the episodes load. Static: no shimmer. */
@Composable
internal fun EpisodeRowSkeleton() {
    val width = when {
        isTv() -> 220.dp
        isCompactWidth() -> 132.dp
        else -> 168.dp
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = edge(), vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.width(width).aspectRatio(16f / 9f))
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.width(150.dp).height(16.dp))
            SkeletonBlock(Modifier.width(90.dp).height(12.dp))
        }
    }
}

// ---- Season picker --------------------------------------------------------------------------

/** Chips for up to six seasons, one "Season 3 ▾" dropdown chip for more. */
@Composable
private fun SeasonPickerRow(seasons: List<SeasonBlock>, selected: Int, onSelect: (Int) -> Unit) {
    when (seasonPickerFor(seasons.size)) {
        SeasonPicker.NONE -> Unit
        SeasonPicker.CHIPS -> LazyRow(
            contentPadding = PaddingValues(horizontal = edge(), vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(seasons, key = { it.number }) { season ->
                SeasonChip(season.name, selected = season.number == selected, onClick = { onSelect(season.number) })
            }
        }
        SeasonPicker.DROPDOWN -> Box(Modifier.padding(horizontal = edge(), vertical = 6.dp)) {
            SeasonDropdown(seasons, selected, onSelect)
        }
    }
}

/** A pill like KhChip, at least 48 dp tall so a thumb can hit it. */
@Composable
private fun SeasonChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: Boolean = false) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
        shape = KhTheme.shapes.chip,
        scaleAndLift = false,
    ) { focused ->
        val fill = when {
            selected -> KhColors.TextPrimary
            focused -> KhColors.Elevated
            else -> KhColors.Surface
        }
        val content = if (selected) KhColors.OnLight else KhColors.TextPrimary
        Row(
            Modifier
                .align(Alignment.Center)
                .padding(3.dp)
                .background(fill, KhTheme.shapes.chip)
                .padding(horizontal = KhTheme.spacing.m, vertical = KhTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = KhTheme.typography.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing) Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The season chip for a long series. Select opens a list under it with focus on the current season.
 * Back, a tap outside or a pick closes it, and focus returns to the chip.
 */
@Composable
private fun SeasonDropdown(seasons: List<SeasonBlock>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var wasOpen by remember { mutableStateOf(false) }
    val chipRequester = remember { FocusRequester() }
    LaunchedEffect(open) {
        if (open) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            runCatching { chipRequester.requestFocus() }
        }
    }
    val current = seasons.firstOrNull { it.number == selected } ?: seasons.first()
    Box {
        SeasonChip(
            text = current.name,
            selected = false,
            onClick = { open = true },
            trailing = true,
            modifier = Modifier.focusRequester(chipRequester).semantics { contentDescription = "Season, ${current.name}. Opens a list." },
        )
        if (open) {
            Popup(
                popupPositionProvider = BelowAnchor,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                BackHandler { open = false }
                val appear = remember { Animatable(0f) }
                LaunchedEffect(Unit) { appear.animateTo(1f, tween(KhMotion.PopoverMillis, easing = KhMotion.EaseOutStrong)) }
                Box(Modifier.graphicsLayer { alpha = appear.value }) {
                    SeasonList(seasons, selected) { picked ->
                        open = false
                        onSelect(picked)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonList(seasons: List<SeasonBlock>, selected: Int, onPick: (Int) -> Unit) {
    val selectedRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedRequester.requestFocus() } }
    Column(
        Modifier
            .padding(top = KhTheme.spacing.xs, bottom = KhTheme.spacing.l) // room for the shadow
            .shadow(12.dp, KhTheme.shapes.tile)
            .background(KhColors.Elevated, KhTheme.shapes.tile)
            .width(240.dp)
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(KhTheme.spacing.s)
            .focusGroup(),
    ) {
        seasons.forEach { season ->
            val isSelected = season.number == selected
            KhFocusable(
                onClick = { onPick(season.number) },
                shape = KhTheme.shapes.button,
                scaleAndLift = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { this.selected = isSelected; contentDescription = season.name }
                    .then(if (isSelected) Modifier.focusRequester(selectedRequester) else Modifier),
            ) { focused ->
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .background(if (focused) KhColors.Surface else KhColors.Elevated)
                        .padding(horizontal = KhTheme.spacing.m, vertical = KhTheme.spacing.s + 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(season.name, style = KhTheme.typography.label, color = KhColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = KhColors.TextPrimary)
                }
            }
        }
    }
}

/** Puts the list just below the chip. If it does not fit, above it. Always inside the window. */
private object BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
