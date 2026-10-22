@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.kisskh.tv.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kisskh.tv.api.Episode
import com.kisskh.tv.ui.EpisodeRange
import com.kisskh.tv.ui.components.KhProgressBar
import com.kisskh.tv.ui.components.formatEpisodeNumber
import com.kisskh.tv.ui.components.formatPosition
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/** Which block to open first: the one holding [episodeId], otherwise the latest block. */
fun initialRangeStart(ranges: List<EpisodeRange>, episodeId: Int?): Int {
    val match = ranges.firstOrNull { range -> range.episodes.any { it.id == episodeId } }
    return (match ?: ranges.lastOrNull())?.start ?: 1
}

@Composable
fun EpisodeRow(
    episode: Episode,
    parts: Boolean,
    partNumber: Int,
    positionMs: Long,
    progress: Float?,
    unavailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val number = if (parts) partNumber.toString() else formatEpisodeNumber(episode.number)
    val name = if (parts) "Part $partNumber" else "Episode $number"
    val spoken = when {
        unavailable -> "$name, not released yet"
        positionMs > 0L -> "$name, resume at ${formatPosition(positionMs)}"
        else -> name
    }
    val color = if (unavailable) KhColors.TextSecondary else KhColors.TextPrimary
    KhFocusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = spoken },
        shape = RoundedCornerShape(4.dp),
        scaleAndLift = false,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(if (focused) KhColors.Elevated else Color.Transparent)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                number,
                style = KhTheme.typography.episodeNumber,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(72.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(name, style = KhTheme.typography.label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    unavailable -> Text("Not released yet", style = KhTheme.typography.caption, color = KhColors.TextSecondary)
                    progress != null -> KhProgressBar(progress, Modifier.padding(top = 6.dp))
                    positionMs > 0L -> Text(
                        "At ${formatPosition(positionMs)}",
                        style = KhTheme.typography.caption,
                        color = KhColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Netflix-style episode panel: ranges on the left, episode rows on the right.
 * Focus stays inside until Back (the screen closes it).
 */
@Composable
fun TvEpisodePanel(
    ranges: List<EpisodeRange>,
    parts: Boolean,
    partNumberOf: (Episode) -> Int,
    positions: Map<Int, Long>,
    progressOf: (Episode) -> Float?,
    unavailable: Set<Int>,
    initialEpisodeId: Int?,
    onPlay: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedStart by remember { mutableIntStateOf(initialRangeStart(ranges, initialEpisodeId)) }
    val selected = ranges.firstOrNull { it.start == selectedStart } ?: ranges.first()
    val targetId = initialEpisodeId?.takeIf { id -> selected.episodes.any { it.id == id } } ?: selected.episodes.first().id
    val episodeFocus = remember(selected.start) { FocusRequester() }
    val episodeList = rememberLazyListState()
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(KhMotion.RailExpandMillis, easing = KhMotion.EaseOutStrong))
    }
    LaunchedEffect(selected.start) {
        val index = selected.episodes.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
        if (index > 0) episodeList.scrollToItem(index)
        withFrameNanos { }
        runCatching { episodeFocus.requestFocus() }
    }

    Row(
        modifier
            .fillMaxHeight()
            .fillMaxWidth(0.70f)
            .graphicsLayer {
                alpha = appear.value
                translationX = (1f - appear.value) * with(density) { 48.dp.toPx() }
            }
            .background(KhColors.Background)
            .focusGroup(),
    ) {
        LazyColumn(
            Modifier
                .width(156.dp)
                .fillMaxHeight()
                .padding(start = 16.dp, top = 24.dp, bottom = 24.dp)
                .focusProperties { left = FocusRequester.Cancel },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(ranges, key = { it.start }) { range ->
                val on = range.start == selected.start
                KhFocusable(
                    onClick = { selectedStart = range.start },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(6.dp),
                    scaleAndLift = false,
                ) { focused ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .background(if (on) KhColors.Brand else Color.Transparent),
                        )
                        Text(
                            "${range.start}-${range.end}",
                            modifier = Modifier.padding(start = 10.dp),
                            color = if (on || focused) KhColors.TextPrimary else KhColors.TextSecondary,
                            style = KhTheme.typography.label,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        LazyColumn(
            state = episodeList,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp, bottom = 16.dp, end = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(selected.episodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    parts = parts,
                    partNumber = partNumberOf(episode),
                    positionMs = positions[episode.id] ?: 0L,
                    progress = progressOf(episode),
                    unavailable = episode.id in unavailable,
                    onClick = { onPlay(episode) },
                    modifier = if (episode.id == targetId) Modifier.focusRequester(episodeFocus) else Modifier,
                )
            }
        }
    }
}
