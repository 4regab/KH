package com.kisskh.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kisskh.tv.ui.theme.KhColors

/**
 * The thin "watched so far" bar: [fraction] (0..1) of the width is red, the rest is [track].
 * It fills the width it is given. On artwork, align it to the bottom. [minFraction] keeps a
 * sliver visible for a title that was only just started.
 */
@Composable
fun KhProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    track: Color = KhColors.TrackOnArt,
    minFraction: Float = 0f,
) {
    Box(modifier.fillMaxWidth().height(height).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(minFraction, 1f)).fillMaxHeight().background(KhColors.Brand))
    }
}