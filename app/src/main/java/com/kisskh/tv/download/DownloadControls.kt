package com.kisskh.tv.download

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.player.SheetOption
import com.kisskh.tv.ui.components.KhSpinner
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/*
 * The Download control on a title page. It comes in two shapes that share one behaviour:
 * [DownloadButton] is a 48 dp round icon beside an episode, [DownloadActionButton] is the labelled
 * button beside My List on a movie.
 *
 * Tap: not downloaded or failed starts the download. While the stream is being found, while it
 * downloads, and when it is done, a small dialog offers Stop, Cancel or Play offline and Remove.
 */

/** What one control shows and does. Made by [rememberDownloadControl]. */
@Stable
internal class DownloadControl(
    val id: String,
    val ui: DownloadUi,
    val name: String,
    val downloads: TitleDownloads,
    val open: MutableState<Boolean>,
    val onClick: () -> Unit,
) {
    val description: String get() = ui.description(name)
}

/** The control for [episode], or null when it can't be downloaded (the caller then draws nothing). */
@Composable
internal fun rememberDownloadControl(
    downloads: TitleDownloads,
    episode: EpisodeRef,
    episodes: List<EpisodeRef>,
    name: String,
): DownloadControl? {
    val id = downloads.idFor(episode) ?: return null
    val open = remember(id) { mutableStateOf(false) }
    val ui = downloads.uiFor(id)
    return DownloadControl(id, ui, name, downloads, open) {
        when (ui.stage) {
            DownloadStage.START, DownloadStage.RETRY -> downloads.start(episode, episodes)
            else -> open.value = true
        }
    }
}

/** The 48 dp round Download icon at the end of an episode row, next to the Watched check. */
@Composable
fun DownloadButton(
    downloads: TitleDownloads,
    episode: EpisodeRef,
    episodes: List<EpisodeRef>,
    name: String,
    modifier: Modifier = Modifier,
) {
    val control = rememberDownloadControl(downloads, episode, episodes, name) ?: return
    KhFocusable(
        onClick = control.onClick,
        modifier = modifier.size(48.dp).semantics { contentDescription = control.description },
        shape = CircleShape,
        scaleAndLift = false,
    ) {
        val ring = control.ui.stage in RING_STAGES
        Box(Modifier.align(Alignment.Center).size(if (ring) 34.dp else 28.dp), contentAlignment = Alignment.Center) {
            DownloadGlyph(control.ui, if (ring) 34.dp else if (control.ui.stage == DownloadStage.DONE) 28.dp else 24.dp, showPercent = true)
        }
    }
    DownloadDialog(control)
}

/**
 * The labelled Download button of a movie: "Download", "Downloading 42%", "Downloaded". It looks
 * like My List and Watched next to it.
 */
@Composable
fun DownloadActionButton(
    downloads: TitleDownloads,
    episode: EpisodeRef,
    episodes: List<EpisodeRef>,
    name: String,
    modifier: Modifier = Modifier,
    /** Phone style: icon over label, like My List and Watched beside it (see `StackedAction`). */
    stacked: Boolean = false,
) {
    val control = rememberDownloadControl(downloads, episode, episodes, name) ?: return
    if (stacked) {
        com.kisskh.tv.ui.shelf.StackedAction(
            label = control.ui.label,
            description = control.description,
            onClick = control.onClick,
            modifier = modifier,
        ) { DownloadGlyph(control.ui, 26.dp, showPercent = false) }
        DownloadDialog(control)
        return
    }
    KhFocusable(
        onClick = control.onClick,
        modifier = modifier.height(48.dp).semantics { contentDescription = control.description },
        shape = KhTheme.shapes.action,
    ) {
        Row(
            Modifier.fillMaxSize().background(KhColors.ButtonGrey, KhTheme.shapes.action).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                DownloadGlyph(control.ui, 22.dp, showPercent = false)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                control.ui.label,
                style = KhTheme.typography.action,
                color = KhColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    DownloadDialog(control)
}

private val RING_STAGES = setOf(DownloadStage.QUEUED, DownloadStage.DOWNLOADING, DownloadStage.WAITING, DownloadStage.PAUSED)

/** The picture for one stage: an arrow, a spinner, a progress ring, a retry arrow or a check. */
@Composable
private fun DownloadGlyph(ui: DownloadUi, size: Dp, showPercent: Boolean) {
    when (ui.stage) {
        DownloadStage.START -> Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = KhColors.TextPrimary, modifier = Modifier.size(size))
        DownloadStage.FINDING -> KhSpinner(size = size)
        DownloadStage.RETRY -> Icon(Icons.Filled.Refresh, contentDescription = null, tint = KhColors.TextPrimary, modifier = Modifier.size(size))
        // Red like the progress ring, so it can't be mistaken for the white Watched check beside it.
        DownloadStage.DONE -> Box(Modifier.size(size).background(KhColors.Brand, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = KhColors.OnBrand, modifier = Modifier.size(size * 0.65f))
        }
        else -> ProgressRing(ui, size, showPercent)
    }
}

/** A ring that fills clockwise with the percent in the middle. Red while bytes move, grey while paused or waiting. */
@Composable
private fun ProgressRing(ui: DownloadUi, size: Dp, showPercent: Boolean) {
    if (ui.stage == DownloadStage.DOWNLOADING && ui.percent < 0) {
        KhSpinner(size = size, color = KhColors.Brand)
        return
    }
    val moving = ui.stage == DownloadStage.DOWNLOADING || ui.stage == DownloadStage.QUEUED
    val color = if (moving) KhColors.Brand else KhColors.NavMuted
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = (this.size.minDimension * 0.1f).coerceAtLeast(2.dp.toPx())
            val inset = stroke / 2
            val box = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
            if (ui.percent > 0) {
                drawArc(color, -90f, 360f * ui.percent / 100f, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        if (showPercent) {
            val text = when {
                ui.stage == DownloadStage.DOWNLOADING -> "${ui.percent}%"
                ui.stage == DownloadStage.PAUSED -> "II"
                else -> "\u2026"
            }
            Text(text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = KhColors.TextPrimary, maxLines = 1, softWrap = false)
        }
    }
}

/** The small panel opened by tapping a control that is busy or done. Focus starts on its first choice. */
@Composable
internal fun DownloadDialog(control: DownloadControl) {
    if (!control.open.value) return
    val close = { control.open.value = false }
    val downloads = control.downloads
    val stage = control.ui.stage
    // The download was removed or failed while the panel was open: nothing left to ask about.
    LaunchedEffect(stage) { if (stage == DownloadStage.START || stage == DownloadStage.RETRY) close() }
    val first = remember { FocusRequester() }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Asked from inside the dialog, once its first choice exists.
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .background(KhColors.Surface, KhTheme.shapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(control.name, style = KhTheme.typography.caption, color = KhColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when (stage) {
                        DownloadStage.FINDING -> "Finding the stream"
                        DownloadStage.DONE -> "Downloaded"
                        else -> "Downloading"
                    },
                    style = KhTheme.typography.rowTitle,
                    color = KhColors.TextPrimary,
                )
                Text(
                    downloads.itemFor(control.id)?.status ?: "This can take a few seconds.",
                    style = KhTheme.typography.caption,
                    color = KhColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                when (stage) {
                    DownloadStage.FINDING -> SheetOption("Stop", selected = false, modifier = Modifier.focusRequester(first)) {
                        close()
                        downloads.stopFinding()
                    }
                    DownloadStage.DONE -> {
                        SheetOption("Play offline", selected = false, modifier = Modifier.focusRequester(first)) {
                            close()
                            downloads.playOffline(control.id)
                        }
                        SheetOption("Remove download", selected = false) {
                            close()
                            downloads.remove(control.id)
                            downloads.toast("Download removed")
                        }
                    }
                    else -> SheetOption("Cancel download", selected = false, modifier = Modifier.focusRequester(first)) {
                        close()
                        downloads.remove(control.id)
                        downloads.toast("Download cancelled")
                    }
                }
            }
        }
    }
}
