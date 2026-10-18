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

/** True on Android TV (including Chromecast with Google TV). */
@Composable
fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) { isTelevision(context) }
}

/** True on a narrow phone in portrait. Same cut-off as [KhTheme]. */
@Composable
fun isCompactWidth(): Boolean = !rememberIsTelevision() && LocalConfiguration.current.screenWidthDp < 600

/** Left margin of screen content. On TV the rail already insets the page, so this is the extra gap. */
@Composable
fun screenStart(): Dp = if (KhTheme.formFactor == FormFactor.TV) 20.dp else 16.dp

/** Space between cards. TV cards scale up over their neighbours; phone rows use 8 dp. */
@Composable
fun cardGap(): Dp = if (KhTheme.formFactor == FormFactor.TV) 10.dp else 8.dp

/** Narrowest catalog card: about 200 dp on TV (art only) and 160 dp on a phone. */
@Composable
fun gridCardMinWidth(): Dp = if (KhTheme.formFactor == FormFactor.TV) 200.dp else 160.dp

/** Height of the big artwork area on Detail. */
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
