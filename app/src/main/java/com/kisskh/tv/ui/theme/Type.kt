package com.kisskh.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kisskh.tv.R

/** Inter (SIL Open Font License, see assets/licenses/Inter-OFL.txt): the one typeface of the app. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

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
 * - [badge]: the smallest allowed text, on card badges (11 sp on TV; phones never go under [PHONE_MIN_TEXT_SP])
 *
 * These five keep one size on every screen (they are not scaled down on a narrow phone):
 * - [heroTitle]: the title on the phone billboard and the phone title page (26 sp)
 * - [cardTitle] and [cardTitlePhone]: the name under a card in a grid (14 sp on TV, 12 sp on a phone)
 * - [navLabel]: the names in the phone bottom bar (12 sp)
 * - [episodeNumber]: the big number at the start of an episode row (22 sp)
 * - [field]: text typed into a search or token field (17 sp)
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
    val heroTitle: TextStyle,
    val cardTitle: TextStyle,
    val cardTitlePhone: TextStyle,
    val navLabel: TextStyle,
    val episodeNumber: TextStyle,
    val field: TextStyle,
)

/**
 * A [line] of 0 leaves the line height to the font. [minSize] is the smallest size in sp (0 = no
 * floor); when the floor lifts a size, the line height grows with it so the text is not clipped.
 */
private fun style(size: Int, line: Int, weight: FontWeight, scale: Float, spacing: Float = 0f, minSize: Float = 0f): TextStyle {
    val sp = maxOf(size * scale, minSize)
    return TextStyle(
        fontFamily = InterFamily,
        fontWeight = weight,
        fontSize = sp.sp,
        lineHeight = if (line > 0) maxOf(line * scale, sp * 1.2f).sp else TextUnit.Unspecified,
        letterSpacing = spacing.sp,
    )
}

/** Smallest text on a phone, in sp. */
const val PHONE_MIN_TEXT_SP = 12f

/**
 * [scale] 1 is the TV size. Phones use a smaller scale (see [KhTheme]). [minSize] is a floor in sp
 * for every style (phones pass [PHONE_MIN_TEXT_SP]; TV passes 0 because it is read from a couch).
 */
fun khTypography(scale: Float = 1f, minSize: Float = 0f) = KhTypography(
    // Inter is a little wider than Roboto, so the big sizes track slightly tighter.
    display = style(44, 48, FontWeight.Bold, scale, -1f, minSize),
    headline = style(30, 38, FontWeight.Bold, scale, -0.5f, minSize),
    title = style(22, 28, FontWeight.SemiBold, scale, -0.25f, minSize),
    body = style(18, 26, FontWeight.Normal, scale, 0f, minSize),
    label = style(16, 22, FontWeight.Medium, scale, 0.1f, minSize),
    caption = style(14, 18, FontWeight.Normal, scale, 0.1f, minSize),
    heading = style(36, 42, FontWeight.Bold, scale, -0.75f, minSize),
    rowTitle = style(20, 28, FontWeight.SemiBold, scale, -0.1f, minSize),
    synopsis = style(16, 22, FontWeight.Normal, scale, 0f, minSize),
    meta = style(15, 20, FontWeight.Medium, scale, 0.1f, minSize),
    railLabel = style(18, 24, FontWeight.SemiBold, scale, 0f, minSize),
    action = style(16, 22, FontWeight.SemiBold, scale, 0f, minSize),
    cardFallback = style(14, 18, FontWeight.Medium, scale, 0f, minSize),
    badge = style(11, 14, FontWeight.SemiBold, scale, 0.2f, minSize),
    heroTitle = style(26, 30, FontWeight.Bold, 1f, -0.5f, minSize),
    cardTitle = style(14, 18, FontWeight.Normal, 1f, 0f, minSize),
    cardTitlePhone = style(12, 16, FontWeight.Normal, 1f, 0f, minSize),
    navLabel = style(12, 0, FontWeight.Normal, 1f, 0f, minSize),
    episodeNumber = style(22, 0, FontWeight.Bold, 1f, 0f, minSize),
    field = style(17, 0, FontWeight.Normal, 1f, 0f, minSize),
)