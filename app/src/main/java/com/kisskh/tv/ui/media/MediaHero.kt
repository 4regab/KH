package com.kisskh.tv.ui.media

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/** Gold for the rating star. Only used next to the score. */
private val StarGold = Color(0xFFF5C518)

/** Strong on the left (where the text sits), clear by 60%. */
private val HeroLeftFade = Brush.horizontalGradient(
    0f to KhColors.Background,
    0.22f to KhColors.Background.copy(alpha = 0.86f),
    0.6f to Color.Transparent,
)

private val HeroBottomFade = Brush.verticalGradient(
    0f to Color.Transparent,
    0.55f to KhColors.Background.copy(alpha = 0.55f),
    1f to KhColors.Background,
)

/**
 * The full-bleed key art behind the billboard and the TV title page. The picture fills [widthFraction]
 * of the box from the right; a left fade and a bottom fade keep the text readable and blend it into
 * the page. Changing [url] crossfades.
 */
@Composable
fun HeroArt(url: String?, modifier: Modifier = Modifier, widthFraction: Float = 0.78f) {
    Crossfade(targetState = url, animationSpec = KhMotion.crossfade(), label = "heroArt", modifier = modifier) { art ->
        Box(Modifier.fillMaxSize()) {
            Artwork(
                url = art,
                title = null,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(widthFraction),
                fadeMillis = 0,
                titleOnFailure = false,
            )
            Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(widthFraction + 0.02f).background(HeroLeftFade))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.55f).background(HeroBottomFade))
        }
    }
}

/**
 * The title as its logo when TMDB has one, as text until then (and when the logo fails). The box
 * keeps [height] either way, so the lines below never jump when the logo arrives.
 */
@Composable
fun TitleLogo(
    logo: String?,
    name: String,
    height: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 440.dp,
    align: Alignment = Alignment.BottomStart,
) {
    var shown by remember(logo) { mutableStateOf(false) }
    Box(
        modifier.height(height).widthIn(max = maxWidth).semantics { contentDescription = name },
        contentAlignment = align,
    ) {
        if (!shown) {
            Text(name, style = style, color = KhColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (logo != null) {
            val context = LocalContext.current
            val request = remember(logo) { ImageRequest.Builder(context).data(logo).crossfade(180).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = align,
                onSuccess = { shown = true },
                onError = { shown = false },
                modifier = Modifier
                    .heightIn(max = height)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (shown) 1f else 0f },
            )
        }
    }
}

/** "2h 19m" or "47m". */
fun runtimeText(minutes: Int?): String? {
    val m = minutes?.takeIf { it > 0 } ?: return null
    return if (m >= 60) "${m / 60}h ${m % 60}m".replace(" 0m", "") else "${m}m"
}

/**
 * The facts line under a title, Netflix style: year, the rating badge in a thin box, length, then
 * the score with a gold star. Missing facts are left out, never shown as blanks.
 */
@Composable
fun MetaLine(
    year: String?,
    certification: String?,
    length: String?,
    vote: String?,
    modifier: Modifier = Modifier,
) {
    val style = KhTheme.typography.meta
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (year != null) Text(year, style = style, color = KhColors.TextPrimary)
        if (certification != null) {
            Text(
                certification,
                style = style.copy(fontSize = style.fontSize * 0.86f),
                color = KhColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier
                    .border(1.dp, KhColors.TextSecondary.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (length != null) Text(length, style = style, color = KhColors.TextPrimary, maxLines = 1)
        if (vote != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(16.dp))
                Text(" $vote", style = style, color = KhColors.TextPrimary, modifier = Modifier.semantics { contentDescription = "Rated $vote out of 10" })
            }
        }
    }
}

/** Genres joined with a dot, in the secondary colour. */
@Composable
fun GenreLine(genres: List<String>, modifier: Modifier = Modifier) {
    if (genres.isEmpty()) return
    Text(
        genres.take(3).joinToString("  ·  "),
        style = KhTheme.typography.meta,
        color = KhColors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Big display title style for billboards: 46 sp on TV. */
@Composable
fun heroTitleStyle(): TextStyle = KhTheme.typography.display.copy(fontSize = KhTheme.typography.display.fontSize * 1.05f)
