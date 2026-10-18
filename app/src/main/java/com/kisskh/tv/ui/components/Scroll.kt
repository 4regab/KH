@file:OptIn(ExperimentalFoundationApi::class)

package com.kisskh.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import com.kisskh.tv.ui.theme.KhTheme

/**
 * Scrolling rule for every list and grid: when focus lands on an item near the edge, scroll far
 * enough that the item has [KhSpacing.l] of free space around it. The default rule stops with the
 * item flush against the edge, and the focus zoom and outline would be cut off there.
 */
@Composable
fun KhScrollDefaults(content: @Composable () -> Unit) {
    val margin = with(LocalDensity.current) { KhTheme.spacing.l.toPx() }
    val spec = remember(margin) { EdgeMarginSpec(margin) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

private class EdgeMarginSpec(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val behindLeading = offset - margin // negative: the item is too close to (or past) the start
        val pastTrailing = offset + size - (containerSize - margin) // positive: too close to (or past) the end
        return when {
            behindLeading >= 0f && pastTrailing <= 0f -> 0f
            size + 2 * margin >= containerSize -> behindLeading // too big to fit: show its start
            behindLeading < 0f -> behindLeading
            else -> pastTrailing
        }
    }
}
