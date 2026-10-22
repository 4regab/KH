package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kisskh.tv.api.MY_LIST_ROW
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.CONTINUE_ROW
import com.kisskh.tv.api.source.ShelfRow
import com.kisskh.tv.api.source.TitleCard
import androidx.tv.material3.Text
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/**
 * Card shapes. Sources that have real posters (TMDB, Reelix, Anisuge, Anime Nexus) get tall 2:3
 * cards, like Netflix. KissKH only has landscape stills, so it stays 16:9. A Home picks one shape
 * for all of its rows (see [homeShape]).
 */
enum class CardShape { POSTER, LANDSCAPE }

/** A poster that is its own picture, not the same landscape still stored twice. */
fun TitleCard.hasPoster(): Boolean = !poster.isNullOrBlank() && poster != backdrop

/** Posters when most of the cards have one; otherwise landscape, so a list never mixes shapes. */
fun shapeFor(cards: List<TitleCard>): CardShape =
    if (cards.isNotEmpty() && cards.count { it.hasPoster() } * 2 >= cards.size) CardShape.POSTER else CardShape.LANDSCAPE

/**
 * The one card shape for a whole Home. Decided from the source's own rows: Continue Watching and
 * My List are left out because their cards store one picture twice and would say "landscape".
 */
internal fun homeShape(rows: List<ShelfRow>): CardShape {
    val own = rows.filter { it.title != CONTINUE_ROW && it.title != MY_LIST_ROW }.ifEmpty { rows }
    return shapeFor(own.flatMap { it.items })
}

/** Best guess of a source's [homeShape] before its rows have loaded, for the skeleton. */
// ponytail: a hint only. Only KissKH is landscape today; the real shape always comes from the loaded rows.
internal fun Library.shapeHint(): CardShape = if (this == Library.KISSKH) CardShape.LANDSCAPE else CardShape.POSTER

/** Height over width of a card's artwork. */
internal fun CardShape.ratio(): Float = if (this == CardShape.POSTER) 2f / 3f else 16f / 9f

/** The small tag on a started title: "Movie", "E5" (anime and KissKH) or "S1 E2". Null for any other card. */
internal fun continueBadge(card: TitleCard): String? = when {
    card.resumeEpisode == null -> null
    card.kind == MediaKind.MOVIE -> "Movie"
    card.source == Library.KISSKH || card.kind == MediaKind.ANIME -> "E${card.resumeNumber.coerceAtLeast(1)}"
    else -> "S${card.resumeSeason.coerceAtLeast(1)} E${card.resumeNumber.coerceAtLeast(1)}"
}

@Composable
fun cardWidth(shape: CardShape): Dp {
    val tv = KhTheme.formFactor == FormFactor.TV
    return when (shape) {
        CardShape.POSTER -> if (tv) 136.dp else 112.dp
        CardShape.LANDSCAPE -> if (tv) 220.dp else 168.dp
    }
}

/** Space between cards, in rows and in grids. */
@Composable
fun cardGap(): Dp = if (KhTheme.formFactor == FormFactor.TV) 12.dp else 8.dp

/**
 * One title in a row or grid. Every card has the same corner radius. Posters carry their own title
 * art, so they show no caption; landscape cards name the title underneath on one line.
 * A started title shows a red progress bar. [badge] is a small tag over the art's top left corner:
 * by default the [continueBadge] of a started title, or e.g. the source name in all-source search.
 */
@Composable
fun ShelfCard(
    card: TitleCard,
    shape: CardShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: Boolean = shape == CardShape.LANDSCAPE,
    badge: String? = continueBadge(card),
) {
    // A poster card never shows a wide still cropped tall: without a poster it shows the title on a tile.
    val art = if (shape == CardShape.POSTER) card.poster else card.artwork
    val spoken = listOfNotNull(card.name, badge).joinToString(", ")
    Column(modifier) {
        KhFocusable(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(shape.ratio()).semantics { contentDescription = spoken },
            shape = KhTheme.shapes.art,
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(art, card.name, Modifier.fillMaxSize())
                val progress = card.progress
                if (progress != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
                    )
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.28f)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(3.dp).background(KhColors.Brand))
                }
                if (!badge.isNullOrBlank()) {
                    Badge(badge, Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
        }
        if (caption) {
            Text(
                card.name,
                style = KhTheme.typography.caption,
                color = KhColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = KhTheme.typography.badge,
        color = KhColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(KhColors.BadgeScrim, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** Grey blocks in the shape of the screen that is loading. Static: no shimmer. */
@Composable
fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.background(KhColors.Surface, KhTheme.shapes.art))
}
