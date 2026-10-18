package com.kisskh.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * KH text styles, sized to read from a couch (TV layout is about 960x540 dp).
 * Colour is not set here: text takes the content colour ([KhColors.TextPrimary] by default).
 * Use [KhColors.TextSecondary] for less important text.
 *
 * - [display]: big hero titles
 * - [headline]: screen headings
 * - [title]: row titles, card titles, button text
 * - [body]: synopsis and paragraphs
 * - [label]: chips, filters, metadata
 * - [caption]: smallest allowed text (badges, hints)
 */
@Immutable
data class KhTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

private fun style(size: Int, line: Int, weight: FontWeight, scale: Float, spacing: Float = 0f) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = (size * scale).sp,
    lineHeight = (line * scale).sp,
    letterSpacing = spacing.sp,
)

/** [scale] 1 is the TV size. Phones use a smaller scale (see [KhTheme]). */
fun khTypography(scale: Float = 1f) = KhTypography(
    display = style(44, 52, FontWeight.Bold, scale, -0.5f),
    headline = style(30, 38, FontWeight.Bold, scale),
    title = style(22, 28, FontWeight.SemiBold, scale),
    body = style(18, 26, FontWeight.Normal, scale),
    label = style(16, 22, FontWeight.Medium, scale, 0.1f),
    caption = style(14, 18, FontWeight.Normal, scale, 0.1f),
)
