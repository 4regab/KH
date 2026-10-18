package com.kisskh.tv.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kisskh.tv.ui.theme.KhTheme
import com.kisskh.tv.ui.theme.isTelevision

/** True on Android TV (including Chromecast with Google TV). */
@Composable
fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) { isTelevision(context) }
}

/** True on a narrow phone in portrait. Same cut-off as [KhTheme]. */
@Composable
fun isCompactWidth(): Boolean = !rememberIsTelevision() && LocalConfiguration.current.screenWidthDp < 600

/** Left margin of screen content. The nav rail sits to the left of it. */
@Composable
fun screenStart(): Dp = minOf(KhTheme.spacing.safeHorizontal, KhTheme.spacing.l)

/** Space between cards in a row or grid (more than the focus zoom grows, so neighbours never touch). */
@Composable
fun cardGap(): Dp = if (isCompactWidth()) 12.dp else KhTheme.spacing.m

/** Narrowest a card in a catalog grid may get: about 5 columns at the 960 dp TV width, 2 on a phone. */
@Composable
fun gridCardMinWidth(): Dp = if (isCompactWidth()) 120.dp else 140.dp

/** Card width on Home rails. */
@Composable
fun rowCardWidth(): Dp = if (isCompactWidth()) 150.dp else 184.dp

/** Height of the big artwork area on Home and Detail. */
@Composable
fun heroHeight(): Dp = (LocalConfiguration.current.screenHeightDp.dp * 0.62f).coerceIn(280.dp, 440.dp)

/** Margins for a screen's lazy list or grid. Keeps the focus zoom of the edge items inside the list. */
@Composable
fun screenContentPadding(top: Dp = KhTheme.spacing.focusBleed): PaddingValues = PaddingValues(
    start = screenStart(),
    end = KhTheme.spacing.safeHorizontal,
    top = top,
    bottom = KhTheme.spacing.safeVertical + KhTheme.spacing.focusBleed,
)
