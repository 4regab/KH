package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kisskh.tv.ui.components.isTv

/*
 * Sizes and margins the shelf screens (Home, Movies / TV / Anime, Search and the title page) share.
 * Phones get a top bar with the source switch; on TV the rail does that job.
 */

internal val TopBarHeight = 56.dp

/** The row of category chips under the phone Home top bar (48 dp chips plus 4 dp below). */
internal val ChipRowHeight = 52.dp

/** Space above a row's title: the same for every row. TV rows add their own focus room below. */
@Composable
internal fun rowTop(): Dp = if (isTv()) 12.dp else 20.dp

/** Top of the hero on a phone: under the status bar and the top bar. */
@Composable
internal fun phoneHeroTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight + 8.dp

/** Top of the phone Home hero, which also clears the category chips. */
@Composable
internal fun phoneHomeHeroTop(): Dp = phoneHeroTop() + ChipRowHeight

/** Height of the TV billboard card for a card [width]. */
internal fun tvHeroHeight(width: Dp): Dp = (width * 0.40f).coerceIn(280.dp, 440.dp)

/**
 * Height of the phone Home hero for a card [width]: tall for posters, wide for stills, but never more
 * than 60% of the [screenHeight], so a 600-839 dp tablet does not get a hero taller than its screen.
 */
internal fun phoneHeroHeight(width: Dp, screenHeight: Dp, shape: CardShape): Dp {
    val ratio = if (shape == CardShape.POSTER) 4f / 5f else 16f / 10f
    return minOf(width / ratio, screenHeight * 0.6f)
}

// ---------------------------------------------------------------------------------------------
// TV Home: a full-bleed billboard on top, rows underneath. The focused row is pinned just under the
// billboard text, so the billboard picture reaches a little behind the first row.

/** Space above a TV row's title (matches [rowTop] on TV). */
private val TvRowTitleTop = 12.dp

/** Height of a row title line plus the gap under it. */
private val TvRowTitleBlock = 28.dp + 4.dp

/** Room above and below the cards of a TV row for the focus zoom and outline. */
private val TvRowFocusRoom = 12.dp

/** Height of the card caption under a landscape card (6 dp gap plus one 18 sp line). */
private val TvCaptionBlock = 24.dp

/** Card width of a TV row (the same numbers as `cardWidth`). */
private fun tvCardWidth(shape: CardShape): Dp = if (shape == CardShape.POSTER) 136.dp else 220.dp

/** Height of a whole focused TV row: title, cards, caption and focus room. It must fit under the billboard text. */
internal fun tvRowFocusHeight(shape: CardShape): Dp {
    val card = tvCardWidth(shape) / shape.ratio() // ratio() is width over height, as aspectRatio() wants
    val caption = if (shape == CardShape.LANDSCAPE) TvCaptionBlock else 0.dp
    return TvRowTitleTop + TvRowTitleBlock + TvRowFocusRoom + card + caption + TvRowFocusRoom
}

/** Distance from the top of a TV row to the top of its cards. The pin scroll aligns row tops, so this is only used for tests. */
internal fun tvRowCardTop(): Dp = TvRowTitleTop + TvRowTitleBlock + TvRowFocusRoom

/**
 * Where things go on the TV Home for a screen [screenHeight] tall.
 * - [rowsTop]: the top of the row list (and so of the pinned row). About half the screen, but never
 *   so low that a whole focused row (see [tvRowFocusHeight]) would be cut off at the bottom.
 * - [billboardHeight]: the billboard picture, about 58% of the screen, and always reaching a little
 *   behind the first row so the picture fades into the rows.
 * - [textHeight]: the room for the billboard text, between the top safe margin and the first row.
 */
internal data class TvHomeGeometry(val billboardHeight: Dp, val rowsTop: Dp, val textHeight: Dp)

internal fun tvHomeGeometry(screenHeight: Dp, shape: CardShape, safeTop: Dp = 27.dp): TvHomeGeometry {
    val rowsTop = minOf(screenHeight * 0.50f, screenHeight - tvRowFocusHeight(shape)).coerceAtLeast(screenHeight * 0.25f)
    val billboard = maxOf(screenHeight * 0.58f, rowsTop + 48.dp).coerceAtMost(screenHeight)
    val text = (rowsTop - safeTop - 8.dp).coerceAtLeast(0.dp)
    return TvHomeGeometry(billboard, rowsTop, text)
}

/** How many lines of overview fit in a billboard text area [textHeight] tall. 0 hides it. */
internal fun billboardOverviewLines(textHeight: Dp): Int = when {
    textHeight >= 220.dp -> 3
    textHeight >= 170.dp -> 2
    textHeight >= 120.dp -> 1
    else -> 0
}

/** How many lines of title fit in a billboard text area [textHeight] tall. */
internal fun billboardTitleLines(textHeight: Dp): Int = if (textHeight >= 170.dp) 2 else 1
