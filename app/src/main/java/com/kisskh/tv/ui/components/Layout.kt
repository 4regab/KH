package com.kisskh.tv.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhTheme
import com.kisskh.tv.ui.theme.isTelevision

/**
 * True when the screen uses the TV layout ([KhTheme.formFactor]). That is an Android TV, or a wide
 * landscape screen. Use this to choose a layout. To ask whether the device itself is a TV, use [rememberIsTelevision].
 */
@Composable
fun isTv(): Boolean = KhTheme.formFactor == FormFactor.TV

/** True on Android TV (including Chromecast with Google TV), whatever the window size. For remote-control behaviour. */
@Composable
fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) { isTelevision(context) }
}

/** True on a narrow phone in portrait. Same cut-off as [KhTheme]. */
@Composable
fun isCompactWidth(): Boolean = !rememberIsTelevision() && LocalConfiguration.current.screenWidthDp < 600

/** Left and right margin of every screen's content: 24 dp on TV, 16 dp on a phone. */
@Composable
fun edge(): Dp = if (isTv()) 24.dp else 16.dp

/** Space between cards, in rows and in grids: 12 dp on TV, 8 dp on a phone. */
@Composable
fun cardGap(): Dp = if (isTv()) 12.dp else 8.dp

/** Height of the big artwork area on Detail. */
@Composable
fun heroHeight(): Dp = (LocalConfiguration.current.screenHeightDp.dp * 0.62f).coerceIn(280.dp, 440.dp)

/** Margins for a screen's lazy list or grid. Keeps the focus zoom of the edge items inside the list. */
@Composable
fun screenContentPadding(top: Dp = KhTheme.spacing.focusBleed): PaddingValues = PaddingValues(
    start = edge(),
    end = KhTheme.spacing.safeHorizontal,
    top = top,
    bottom = KhTheme.spacing.safeVertical + KhTheme.spacing.focusBleed,
)