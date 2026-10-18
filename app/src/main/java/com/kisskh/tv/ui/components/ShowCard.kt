package com.kisskh.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kisskh.tv.api.Show
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/**
 * Artwork that fills its box. While it loads the box is a plain dark surface. If there is no
 * address, or the image fails, it shows [title] on a lighter surface instead, so a card is never blank.
 *
 * Coil sizes the request to the box (the image is decoded at the size it is drawn, not full size).
 * Landscape art is cropped to fill. With [fitPortrait], portrait art is shown whole and aligned to
 * the end instead of being zoomed in, for the big areas on Home and Detail. Those pass
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
                Box(Modifier.fillMaxSize().background(KhColors.Elevated).padding(KhTheme.spacing.s), contentAlignment = Alignment.Center) {
                    Text(
                        title.orEmpty(),
                        style = KhTheme.typography.label,
                        color = KhColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
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
 * A title card: 16:9 artwork with the name under it (two lines, then "..."). The whole card
 * takes the KH focus look. Give it a width through [modifier]; the height follows.
 *
 * Badges are only real data: the API's own `label` (top left, for example "Coming Oct 23") and the
 * episode count (bottom right) when [showEpisodeCount] is true and there is more than one.
 * [onFocused] is called when the card gains focus (screens use it to remember the last focused card).
 */
@Composable
fun ShowCard(
    show: Show,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showEpisodeCount: Boolean = true,
    onFocused: () -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val title = show.title?.trim().orEmpty()
    KhFocusable(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = KhTheme.shapes.card,
        interactionSource = interactionSource,
    ) { focused ->
        Column(Modifier.fillMaxWidth().background(KhColors.Surface)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Artwork(show.thumbnail, title, Modifier.fillMaxSize())
                val badge = show.label?.trim()?.takeIf { it.isNotEmpty() }
                if (badge != null) {
                    Badge(badge, Modifier.align(Alignment.TopStart).padding(KhTheme.spacing.xs + 2.dp))
                }
                if (showEpisodeCount && show.episodesCount > 1) {
                    Badge("${show.episodesCount} eps", Modifier.align(Alignment.BottomEnd).padding(KhTheme.spacing.xs + 2.dp))
                }
            }
            Text(
                title.ifEmpty { "Untitled" },
                style = KhTheme.typography.label,
                color = if (focused) KhColors.TextPrimary else KhColors.TextSecondary,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = KhTheme.spacing.s + 2.dp, vertical = KhTheme.spacing.s),
            )
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = KhTheme.typography.caption,
        color = KhColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(KhColors.Scrim, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
