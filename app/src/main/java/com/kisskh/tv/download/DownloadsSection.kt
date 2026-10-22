package com.kisskh.tv.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

private const val EMPTY_TEXT = "Movies and episodes you download appear here. Start one from the download button on a title page."

/** The Downloads list on the My KH screen: one row per download, newest first. */
@androidx.annotation.OptIn(UnstableApi::class)
fun LazyListScope.downloadsSection(vm: AppViewModel) {
    item(key = "downloads") {
        val loaded by vm.downloads.loaded.collectAsStateWithLifecycle()
        val items by vm.downloads.items.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Downloads",
                style = KhTheme.typography.rowTitle,
                color = KhColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            )
            when {
                !loaded -> Unit
                items.isEmpty() -> Text(
                    EMPTY_TEXT,
                    style = KhTheme.typography.body,
                    color = KhColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                else -> items.forEach { item ->
                    DownloadRow(
                        item = item,
                        onPlay = { vm.push(Dest.OfflinePlayer(item.id)) },
                        onRemove = { vm.downloads.remove(item.id) },
                    )
                }
            }
        }
    }
}

/**
 * Artwork, title and state on the left; the whole block plays a finished download. Remove (Cancel
 * while it is still running) sits on the right. Both are at least 48 dp tall.
 */
@Composable
private fun DownloadRow(item: DownloadItem, onPlay: () -> Unit, onRemove: () -> Unit) {
    val finished = item.phase == DownloadPhase.COMPLETED
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KhFocusable(
            onClick = onPlay,
            enabled = finished,
            modifier = Modifier.weight(1f),
            shape = KhTheme.shapes.card,
            scaleAndLift = false,
        ) { focused ->
            Row(
                Modifier
                    .fillMaxWidth()
                    // No box behind the row (Netflix style); a fill shows only for remote focus.
                    .background(if (focused) KhColors.Elevated else androidx.compose.ui.graphics.Color.Transparent, KhTheme.shapes.card)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = item.meta.poster,
                    title = item.meta.title,
                    // Crop, never letterbox: a portrait poster used to sit in a grey rectangle here.
                    modifier = Modifier.width(132.dp).aspectRatio(16f / 9f).clip(KhTheme.shapes.button),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.meta.title,
                        style = KhTheme.typography.label,
                        color = KhColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.meta.label.isNotBlank()) {
                        Text(
                            item.meta.label,
                            style = KhTheme.typography.caption,
                            color = KhColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        item.status,
                        style = KhTheme.typography.caption,
                        color = if (item.phase == DownloadPhase.FAILED) KhColors.Brand else KhColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.phase == DownloadPhase.DOWNLOADING) ProgressBar(item.percent)
                }
            }
        }
        KhButton(if (finished) "Remove" else "Cancel", onClick = onRemove)
    }
}

/** A thin bar, red for the part done. An unknown size shows an empty bar. */
@Composable
private fun ProgressBar(percent: Float) {
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).background(KhColors.ChipOutline)) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(KhColors.Brand))
    }
}
