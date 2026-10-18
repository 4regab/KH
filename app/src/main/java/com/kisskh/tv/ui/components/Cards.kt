package com.kisskh.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kisskh.tv.api.Show
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/*
 * Title cards. Two kinds, both 16:9 artwork with a 4 dp corner:
 *
 *  - ArtCard: artwork only, no title. For rows on the TV layout, where the billboard names the focused title.
 *  - TitleCard: artwork with the name under it. For phones and for grids that have no billboard.
 *
 * Size comes from the caller: `Modifier.width(KhCardSize.TvWidth)`. The height follows (16:9).
 */

/** Card sizes. The TV card is 200x112 dp. The phone card is about 150x84 dp. */
object KhCardSize {
    val TvWidth = 200.dp
    val TvHeight = 112.dp
    val PhoneWidth = 150.dp
}

/**
 * Artwork that fills its box. While it loads the box is a plain #17171B (no shimmer). If there is no
 * address, or the image fails, it shows [title] centered on that tile (14 sp, two lines), so a card
 * is never blank and never says nothing.
 *
 * Coil sizes the request to the box (the image is decoded at the size it is drawn, not full size).
 * Landscape art is cropped to fill. With [fitPortrait], portrait art is shown whole and aligned to
 * the end instead of being zoomed in, for the big areas on Detail. Those pass
 * [titleOnFailure] false: the title is already on screen, so a failed image leaves the plain surface.
 */
@Composable
fun Artwork(
    url: String?,
    title: String?,
    modifier: Modifier = Modifier,
    fitPortrait: Boolean = false,
    fadeMillis: Int = 120,
    titleOnFailure: Boolean = true,
) {
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    var portrait by remember(url) { mutableStateOf(false) }
    Box(modifier.background(KhColors.Surface)) {
        if (failed) {
            if (titleOnFailure) {
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text(
                        title.orEmpty(),
                        style = KhTheme.typography.cardFallback,
                        color = KhColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            val context = LocalContext.current
            val request = remember(url, fadeMillis) {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(fadeMillis)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = if (fitPortrait && portrait) ContentScale.Fit else ContentScale.Crop,
                alignment = if (fitPortrait && portrait) Alignment.CenterEnd else Alignment.Center,
                onSuccess = { portrait = it.painter.intrinsicSize.let { s -> s.height > s.width } },
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Artwork-only card for TV rows. No background of its own, no title: only the picture. Focus gives
 * a 1.08 zoom, a 2 dp white border and a soft shadow over 180 ms (see [KhFocusable]).
 *
 * - If the picture is missing or fails, the card shows the title on a plain tile.
 * - The only label is the API's own `label` (for example "Unlock All Ep"), top left, 11 sp.
 * - [progress] (0..1) draws a red bar along the bottom, for titles the viewer has started.
 * - [onFocused] is called when the card gains focus. Screens use it to remember the card and to
 *   change the billboard.
 * - The spoken description is the title, because the title is not written on the card.
 */
@Composable
fun ArtCard(
    show: Show,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onFocused: () -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val title = show.title?.trim().orEmpty()
    KhFocusable(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .semantics { contentDescription = title.ifEmpty { "Untitled" } },
        shape = KhTheme.shapes.art,
        interactionSource = interactionSource,
    ) {
        CardFace(show, title, progress)
    }
}

/**
 * Card with the title under the artwork (one line, 12 sp, grey; 14 sp on TV). For phones and for
 * grids that have no billboard to name the focused title. The whole thing is the touch target.
 */
@Composable
fun TitleCard(
    show: Show,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onFocused: () -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val title = show.title?.trim().orEmpty()
    val tv = KhTheme.formFactor == FormFactor.TV
    KhFocusable(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .semantics { contentDescription = title.ifEmpty { "Untitled" } },
        shape = KhTheme.shapes.art,
        interactionSource = interactionSource,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { CardFace(show, title, progress) }
            Text(
                title.ifEmpty { "Untitled" },
                fontSize = if (tv) 14.sp else 12.sp,
                lineHeight = if (tv) 18.sp else 16.sp,
                color = KhColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

// The picture, the API label badge and the progress bar. Shared by both card kinds.
@Composable
private fun CardFace(show: Show, title: String, progress: Float?) {
    Box(Modifier.fillMaxSize()) {
        Artwork(show.thumbnail, title, Modifier.fillMaxSize())
        val badge = show.label?.trim()?.takeIf { it.isNotEmpty() }
        if (badge != null) {
            Text(
                badge,
                style = KhTheme.typography.badge,
                color = KhColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(KhColors.BadgeScrim, RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (progress != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(KhColors.TextPrimary.copy(alpha = 0.3f)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(KhColors.Brand))
        }
    }
}
