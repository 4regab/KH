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
 * - [display]: billboard and title page names (44 sp, tight tracking: big type wants less space)
 * - [heading]: catalog screen headings
 * - [headline]: smaller screen headings
 * - [title]: card titles, button text
 * - [rowTitle]: the name above a row of cards
 * - [body]: paragraphs
 * - [synopsis]: the short description on the billboard
 * - [meta]: the facts line under a title (year, type, country)
 * - [label]: chips, filters
 * - [railLabel]: nav rail names
 * - [action]: text inside Play / More info
 * - [cardFallback]: the title shown on a card whose artwork failed
 * - [caption]: small hints
 * - [badge]: the smallest allowed text, on card badges
 */
@Immutable
data class KhTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val heading: TextStyle,
    val rowTitle: TextStyle,
    val synopsis: TextStyle,
    val meta: TextStyle,
    val railLabel: TextStyle,
    val action: TextStyle,
    val cardFallback: TextStyle,
    val badge: TextStyle,
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
    display = style(44, 48, FontWeight.Bold, scale, -1f),
    headline = style(30, 38, FontWeight.Bold, scale),
    title = style(22, 28, FontWeight.SemiBold, scale),
    body = style(18, 26, FontWeight.Normal, scale),
    label = style(16, 22, FontWeight.Medium, scale, 0.1f),
    caption = style(14, 18, FontWeight.Normal, scale, 0.1f),
    heading = style(36, 42, FontWeight.Bold, scale, -0.5f),
    rowTitle = style(20, 28, FontWeight.SemiBold, scale),
    synopsis = style(16, 22, FontWeight.Normal, scale),
    meta = style(15, 20, FontWeight.Medium, scale, 0.1f),
    railLabel = style(18, 24, FontWeight.SemiBold, scale),
    action = style(16, 22, FontWeight.SemiBold, scale),
    cardFallback = style(14, 18, FontWeight.Medium, scale),
    badge = style(11, 14, FontWeight.SemiBold, scale, 0.2f),
)
