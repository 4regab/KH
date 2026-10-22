package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisskh.tv.api.MY_LIST_ROW
import com.kisskh.tv.api.ReleaseDates
import com.kisskh.tv.api.TitleMark
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.CONTINUE_ROW
import com.kisskh.tv.api.source.ShelfRow
import com.kisskh.tv.api.source.TitleCard
import androidx.tv.material3.Text
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.KhBadge
import com.kisskh.tv.ui.components.KhProgressBar
import com.kisskh.tv.ui.components.isTv
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme
import com.kisskh.tv.ui.theme.LocalReduceMotion

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

/**
 * What makes a card the same card in a lazy list: its source, kind and id, plus the episode it
 * resumes at. It has no list index, so a row keeps its scroll spot and animations when items move.
 */
internal fun TitleCard.listKey(): String = "${source.key}:$kind:$id:$resumeEpisode"

/** The small tag on a started title: "Movie", "E5" (anime and KissKH) or "S1 E2". Null for any other card. */
internal fun continueBadge(card: TitleCard): String? = when {
    card.resumeEpisode == null -> null
    card.kind == MediaKind.MOVIE -> "Movie"
    card.source == Library.KISSKH || card.kind == MediaKind.ANIME -> "E${card.resumeNumber.coerceAtLeast(1)}"
    else -> "S${card.resumeSeason.coerceAtLeast(1)} E${card.resumeNumber.coerceAtLeast(1)}"
}

/** The small pills a mark shows on a card: ["Coming soon", "Dec 16"] or ["Not available"]. */
internal fun markPills(mark: TitleMark?): List<String> = when (mark) {
    is TitleMark.ComingSoon -> listOfNotNull("Coming soon", ReleaseDates.short(mark.date))
    TitleMark.Unavailable -> listOf("Not available")
    null -> emptyList()
}

/** What a screen reader says for a mark. */
internal fun markSpoken(mark: TitleMark?): String? = when (mark) {
    is TitleMark.ComingSoon -> listOfNotNull("Coming soon", ReleaseDates.long(mark.date)).joinToString(", ")
    TitleMark.Unavailable -> "Not available"
    null -> null
}

/** The mark of [card] (Coming soon, Not available or none). It redraws when the learned marks change. */
@Composable
fun rememberTitleMark(vm: AppViewModel, card: TitleCard): TitleMark? {
    val version by vm.availability.version.collectAsStateWithLifecycle()
    return remember(card, version) { vm.availability.markFor(card) }
}

@Composable
fun cardWidth(shape: CardShape): Dp {
    val tv = isTv()
    return when (shape) {
        CardShape.POSTER -> if (tv) 136.dp else 112.dp
        CardShape.LANDSCAPE -> if (tv) 220.dp else 168.dp
    }
}

/**
 * One title in a row or grid. Every card has the same corner radius. Posters carry their own title
 * art, so they show no caption; landscape cards name the title underneath on one line.
 * A started title shows a red progress bar. [badge] is a small tag over the art's top left corner:
 * by default the [continueBadge] of a started title, or e.g. the source name in all-source search.
 * [mark] adds "Coming soon" or "Not available" pills under it. The title stays open either way.
 * [onFocused] is called when the card gains focus (the TV billboard follows it). [focusableModifier]
 * goes on the focusable itself, ahead of its click, for `focusRequester` / `focusIfRestoring`.
 * The picture fades in over 150 ms, a 1 dp hairline keeps dark art from melting into the page, and a
 * press shrinks the card to 0.97 (see [KhMotion.PressScale]).
 */
@Composable
fun ShelfCard(
    card: TitleCard,
    shape: CardShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: Boolean = shape == CardShape.LANDSCAPE,
    badge: String? = continueBadge(card),
    mark: TitleMark? = null,
    onFocused: (() -> Unit)? = null,
    focusableModifier: Modifier = Modifier,
) {
    // A poster card never shows a wide still cropped tall: without a poster it shows the title on a tile.
    val art = if (shape == CardShape.POSTER) card.poster else card.artwork
    val spoken = listOfNotNull(card.name, badge, markSpoken(mark)).joinToString(", ")
    val fade = if (LocalReduceMotion.current) 0 else KhMotion.CardImageFadeMillis
    val focusModifier = if (onFocused == null) Modifier else Modifier.onFocusChanged { if (it.isFocused) onFocused() }
    Column(modifier) {
        KhFocusable(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(shape.ratio())
                .then(focusableModifier)
                .then(focusModifier)
                .semantics { contentDescription = spoken },
            shape = KhTheme.shapes.art,
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(art, card.name, Modifier.fillMaxSize(), fadeMillis = fade)
                // A hairline inside the picture, so a dark poster does not melt into the page.
                Box(Modifier.fillMaxSize().border(1.dp, KhColors.CardHairline, KhTheme.shapes.art))
                val progress = card.progress
                if (progress != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, KhColors.ScrimProgress))),
                    )
                    KhProgressBar(progress, Modifier.align(Alignment.BottomStart), minFraction = 0.02f)
                }
                val pills = listOfNotNull(badge?.takeIf { it.isNotBlank() }) + markPills(mark)
                if (pills.isNotEmpty()) {
                    Column(Modifier.align(Alignment.TopStart).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        pills.forEach { KhBadge(it) }
                    }
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


/** Grey blocks in the shape of the screen that is loading. Static: no shimmer. */
@Composable
fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.background(KhColors.Surface, KhTheme.shapes.art))
}
