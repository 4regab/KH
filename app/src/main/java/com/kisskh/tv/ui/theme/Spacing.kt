package com.kisskh.tv.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing on a 4 dp grid, plus the screen margins.
 *
 * - [safeHorizontal] / [safeVertical]: keep content this far from the screen edge. On a TV these are
 *   48 dp and 27 dp, because many TVs crop the edges (overscan).
 * - [focusBleed]: free space to leave around anything that grows or gets an outline on focus (inside
 *   a lazy row or grid, use it as contentPadding) so the focused item is never cut off.
 */
@Immutable
data class KhSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val safeHorizontal: Dp = 48.dp,
    val safeVertical: Dp = 27.dp,
    val focusBleed: Dp = 16.dp,
) {
    /** Margins to put around a whole screen. */
    val safePadding: PaddingValues get() = PaddingValues(horizontal = safeHorizontal, vertical = safeVertical)
}

/** TV: 48/27. Tablet or phone in landscape: 24/16. Narrow phone: 16/12. */
fun khSpacing(isTv: Boolean, compact: Boolean) = when {
    isTv -> KhSpacing()
    compact -> KhSpacing(safeHorizontal = 16.dp, safeVertical = 12.dp, focusBleed = 8.dp)
    else -> KhSpacing(safeHorizontal = 24.dp, safeVertical = 16.dp, focusBleed = 12.dp)
}
