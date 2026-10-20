package com.kisskh.tv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * KH colour tokens. The window and launcher colours in res/values/colors.xml repeat the same
 * values (XML cannot read Kotlin). Change both places together.
 *
 * Red is for the brand, progress bars and small accents only. Focus uses [Outline] (near white).
 */
object KhColors {
    val Background = Color(0xFF09090B)
    val Surface = Color(0xFF17171B)
    val Elevated = Color(0xFF232329)
    val Brand = Color(0xFFF52236)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFB5B5BF)

    /** Text on a [Brand] fill. */
    val OnBrand = Color(0xFFFFFFFF)

    /** Text on a white primary button (white fill, dark text). */
    val OnLight = Background

    /** The focus ring. */
    val Outline = TextPrimary

    /** A dim layer over artwork or a screen behind a dialog. */
    val Scrim = Color(0xCC09090B)

    /** Behind a small label on top of artwork (badges). Black at 70%. */
    val BadgeScrim = Color(0xB3000000)

    /** Secondary billboard button ("More info"): #6D6D6E at 70%. */
    val ButtonGrey = Color(0xB36D6D6E)

    /** Outline of a filter chip that is not focused. */
    val ChipOutline = Color(0xFF3A3A42)

    /** Phone bottom-nav icons and labels that are not selected. */
    val NavMuted = Color(0xFF8C8C96)

    /** Hairline on the phone hero card: white at about 13% (#FFFFFF22). */
    val HeroBorder = Color(0x22FFFFFF)
}

/** Fades that keep text readable on top of artwork. Both end in [KhColors.Background]. */
object KhGradients {
    /** Solid at the left, clear at the right. For text over right-weighted artwork. */
    /** Solid at the left edge, clear at 55% of the width. */
    val LeftFade: Brush = Brush.horizontalGradient(
        0.0f to KhColors.Background,
        0.55f to Color.Transparent,
    )

    /** Clear at the top, solid at the bottom. Blends artwork into the rows below it. */
    val BottomFade: Brush = Brush.verticalGradient(
        0.0f to Color.Transparent,
        1.0f to KhColors.Background,
    )
}
