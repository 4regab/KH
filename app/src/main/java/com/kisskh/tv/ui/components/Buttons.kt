package com.kisskh.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
 * Play / More info. Brand red primary (white text), grey secondary, 44 dp tall, 6 dp corners.
 * The label stays on one line. Pass [modifier] width (for example `weight(1f)`) from the caller.
 */
@Composable
fun KhActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = KhTheme.shapes.action,
        interactionSource = interactionSource,
    ) {
        val fill = if (primary) KhColors.BrandFill else KhColors.ButtonGrey
        val content = if (primary) KhColors.OnBrand else KhColors.TextPrimary
        // The fill matches the button's size. With no width from the caller the label sets it
        // (a fillMaxSize row would stretch across the whole screen).
        Box(Modifier.matchParentSize().background(fill, KhTheme.shapes.action))
        Row(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = KhTheme.typography.action,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    outlined: Boolean = false,
    marked: Boolean = false,
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
        if (outlined) {
            val fill = if (focused) KhColors.TextPrimary else KhColors.Background.copy(alpha = 0f)
            val content = if (focused) KhColors.OnLight else KhColors.TextPrimary
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier
                        .border(1.dp, if (focused) KhColors.TextPrimary else KhColors.ChipOutline, KhTheme.shapes.chip)
                        .background(fill, KhTheme.shapes.chip)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, style = KhTheme.typography.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (trailingIcon != null) {
                        Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                    }
                }
                Box(Modifier.height(6.dp), contentAlignment = Alignment.Center) {
                    if (marked && !focused) {
                        Box(Modifier.size(4.dp).background(KhColors.Brand, CircleShape))
                    }
                }
            }
        } else {
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
}
