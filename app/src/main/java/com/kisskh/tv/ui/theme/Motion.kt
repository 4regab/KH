package com.kisskh.tv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * Motion tokens. Every animation in KH takes its curve and time from here.
 *
 * - [EaseOutStrong]: things that appear or respond. Starts fast, so the UI feels instant.
 * - [EaseInOutStrong]: things that move across the screen (the Home pin scroll, the row pivot scroll).
 * - Animate opacity and transform only (use graphicsLayer lambdas, so nothing recomposes per frame).
 * - No bounce, nothing appears from scale 0, and nothing loops except the spinner.
 * - UI motion stays under 300 ms: press 100, popover 150, screen enter 180, focus 180, rail 200,
 *   crossfade 250. Exits are faster than enters. Keys the user repeats (D-pad moves) get the shortest times.
 * - The system "Remove animations" setting is honoured: see [LocalReduceMotion].
 */
object KhMotion {
    val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOutStrong = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** Focus ring, zoom and shadow on a card, button or chip. */
    const val FocusMillis = 180

    /** Press feedback (the item shrinks to [PressScale]). */
    const val PressMillis = 100
    const val PressScale = 0.97f

    /** Small menus and option lists. */
    const val PopoverMillis = 150

    /** A new screen fading in. The old screen is removed at once, so there is no overlap. */
    const val ScreenEnterMillis = 180

    /** A pushed screen (a title page opened from a card) also rises this far (dp) while it fades in. */
    const val ScreenRiseDp = 8

    /** The nav rail widening over the content. Closing is faster than opening. */
    const val RailExpandMillis = 200
    const val RailCollapseMillis = 150

    /** Billboard artwork and text changing to another title. */
    const val CrossfadeMillis = 250

    /** Billboard waits this long after focus stops moving before it changes title. */
    const val BillboardSettleMillis = 300L

    /** The billboard stops waiting for the next picture to load after this long and changes anyway. */
    const val BillboardPreloadMaxMillis = 1_200L

    /** A card's picture fading in once it has loaded. */
    const val CardImageFadeMillis = 150

    /** Rows sliding to keep the focused card at the pivot. */
    const val PivotScrollMillis = 200

    /** The focused row sliding to its fixed spot under the billboard. */
    const val RowPinMillis = 240

    /** The curve of on-screen scrolling: the pin scroll and the row pivot scroll. One place to change both. */
    val PivotEasing = EaseInOutStrong

    fun <T> focus(): TweenSpec<T> = tween(FocusMillis, easing = EaseOutStrong)
    fun <T> press(): TweenSpec<T> = tween(PressMillis, easing = EaseOutStrong)
    fun <T> crossfade(): TweenSpec<T> = tween(CrossfadeMillis, easing = EaseOutStrong)
    fun <T> screenEnter(): TweenSpec<T> = tween(ScreenEnterMillis, easing = EaseOutStrong)
    fun <T> move(millis: Int): TweenSpec<T> = tween(millis, easing = EaseInOutStrong)
}
