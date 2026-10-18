package com.kisskh.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

/*
 * Buttons and chips. The coloured fill is drawn 3 dp inside the focusable bounds, so the focus ring
 * (drawn on the bounds) floats just outside it and stays visible on the white primary button too.
 */

/**
 * A big action button. [primary] is white with dark text (Watch, Resume, Retry). The other kind is
 * dark grey (More info).
 */
@Composable
fun KhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = KhTheme.shapes.button,
        enabled = enabled,
        interactionSource = interactionSource,
    ) { focused ->
        val fill = when {
            primary && focused -> KhColors.TextPrimary
            primary -> KhColors.TextPrimary.copy(alpha = 0.86f)
            focused -> KhColors.Elevated
            else -> KhColors.Surface
        }
        val content = if (primary) KhColors.OnLight else KhColors.TextPrimary
        Row(
            Modifier
                .padding(3.dp)
                .background(fill, KhTheme.shapes.button)
                .padding(horizontal = KhTheme.spacing.l, vertical = KhTheme.spacing.s + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(KhTheme.spacing.s))
            }
            Text(text, style = KhTheme.typography.title, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A small pill for filters, tabs and shortcuts. [selected] is white with dark text. A focused chip
 * that is not selected gets a lighter fill.
 */
@Composable
fun KhChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailingIcon: ImageVector? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = KhTheme.shapes.chip,
        scaleAndLift = false,
        interactionSource = interactionSource,
    ) { focused ->
        val fill = when {
            selected -> KhColors.TextPrimary
            focused -> KhColors.Elevated
            else -> KhColors.Surface
        }
        val content = if (selected) KhColors.OnLight else KhColors.TextPrimary
        Row(
            Modifier
                .padding(3.dp)
                .background(fill, KhTheme.shapes.chip)
                .padding(horizontal = KhTheme.spacing.m, vertical = KhTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = KhTheme.typography.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            }
        }
    }
}
