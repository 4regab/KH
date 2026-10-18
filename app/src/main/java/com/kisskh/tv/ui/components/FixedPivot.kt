@file:OptIn(ExperimentalFoundationApi::class)

package com.kisskh.tv.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import com.kisskh.tv.ui.theme.KhMotion

/**
 * Fixed-pivot scrolling, like the Netflix TV app. Whatever gets focus inside the list is scrolled to
 * sit exactly [pivotPx] from the start edge of the list (left for a row, top for a column).
 * The focus stays where it is and the content slides under it.
 *
 * Provide it with `CompositionLocalProvider(LocalBringIntoViewSpec provides FixedPivotSpec(...))`
 * around one lazy list. The list can only scroll as far as its content goes, so give it end padding
 * of (list size - item size) if the last items must reach the pivot too.
 */
class FixedPivotSpec(
    private val pivotPx: Float,
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(KhMotion.PivotScrollMillis, easing = KhMotion.EaseOutStrong),
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - pivotPx
}
