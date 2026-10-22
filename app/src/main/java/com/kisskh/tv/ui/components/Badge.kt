package com.kisskh.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

/**
 * The small tag over the corner of a card's artwork: "E5", "Coming soon", a source name. White
 * text on a dark pill so it reads on any picture. Place it with [modifier] (align and margin).
 */
@Composable
fun KhBadge(text: String, modifier: Modifier = Modifier) {
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