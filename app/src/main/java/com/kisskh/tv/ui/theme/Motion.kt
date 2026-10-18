package com.kisskh.tv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * Motion tokens. Every animation in KH takes its curve and time from here.
 *
 * - [EaseOutStrong]: things that appear or respond. Starts fast, so the UI feels instant.
 * - [EaseInOutStrong]: things that move across the screen.
 * - No bounce, no looping, nothing over 250 ms. Keys the user repeats (D-pad moves) get the shortest times.
 */
object KhMotion {
    val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOutStrong = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** Focus ring, zoom and shadow on a card, button or chip. */
    const val FocusMillis = 180

    /** Press feedback (the item shrinks to [PressScale]). */
    const val PressMillis = 100
    const val PressScale = 0.96f

    /** Small menus and option lists. */
    const val PopoverMillis = 150

    /** The nav rail widening over the content. Closing is faster than opening. */
    const val RailExpandMillis = 200
    const val RailCollapseMillis = 150

    /** Billboard artwork and text changing to another title. */
    const val CrossfadeMillis = 250

    /** Billboard waits this long after focus stops moving before it changes title. */
    const val BillboardSettleMillis = 300L

    /** Rows sliding to keep the focused card at the pivot. */
    const val PivotScrollMillis = 200

    fun <T> focus(): TweenSpec<T> = tween(FocusMillis, easing = EaseOutStrong)
    fun <T> press(): TweenSpec<T> = tween(PressMillis, easing = EaseOutStrong)
    fun <T> crossfade(): TweenSpec<T> = tween(CrossfadeMillis, easing = EaseOutStrong)
}
