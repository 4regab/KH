package com.kisskh.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

/**
 * A short message that floats over a screen, for example "EP 12 isn't released yet." It never takes
 * focus, so the user's place does not change. Screens show it at the bottom and hide it after a few seconds.
 */
@Composable
fun StatusPill(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        style = KhTheme.typography.body,
        color = KhColors.TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .shadow(8.dp, KhTheme.shapes.tile)
            .background(KhColors.Elevated, KhTheme.shapes.tile)
            .padding(horizontal = KhTheme.spacing.l, vertical = KhTheme.spacing.s + 2.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
