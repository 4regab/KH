package com.kisskh.tv.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.kisskh.tv.player.Pip

/**
 * Which layout to draw. Both share the same view model and data. Only the presentation differs.
 *
 * - [TV]: left rail, billboard, rows. Used on Android TV, and on any screen at least 840 dp wide in landscape.
 * - [PHONE]: everything else.
 */
enum class FormFactor { TV, PHONE }

/** Window width (dp) from which a landscape screen gets the TV layout. */
const val TV_MIN_WIDTH_DP = 840

/** Read it with `LocalFormFactor.current`. [KhTheme] provides it. */
val LocalFormFactor = staticCompositionLocalOf { FormFactor.PHONE }

fun detectFormFactor(isTelevision: Boolean, widthDp: Int, landscape: Boolean): FormFactor =
    if (isTelevision || (landscape && widthDp >= TV_MIN_WIDTH_DP)) FormFactor.TV else FormFactor.PHONE

/** Detects the form factor again when the window size or rotation changes. Not while in picture-in-picture. */
@Composable
fun rememberFormFactor(): FormFactor {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val detected = detectFormFactor(
        isTelevision = isTelevision(context),
        widthDp = config.screenWidthDp,
        landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE,
    )
    // The PiP window is tiny, and the window passes through odd sizes on the way in and out. A new
    // layout would rebuild the player screen and restart the video. So while PiP is on, or while a
    // video plays and the window only shrinks from TV to PHONE, the layout from before stays.
    val last = remember { arrayOf(detected) }
    val holding = Pip.active || (Pip.player != null && last[0] == FormFactor.TV && detected == FormFactor.PHONE)
    if (!holding) last[0] = detected
    return last[0]
}
