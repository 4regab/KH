package com.kisskh.tv.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.kisskh.tv.ui.theme.KhColors
import kotlinx.coroutines.delay

/**
 * Fit (the whole picture, black bars) or Fill (the picture covers the screen, edges cropped). One choice
 * for both players, kept between sessions.
 */
object PlayerZoom {
    private const val PREFS = "player"
    private const val KEY = "zoom"

    /** A pinch must change the size by more than this much before it counts. */
    private const val FILL_ABOVE = 1.1f
    private const val FIT_BELOW = 0.9f

    fun isFill(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun save(context: Context, fill: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, fill).apply()
    }

    /** The mode after a pinch that scaled by [scale] in total: out is Fill, in is Fit, a small one changes nothing. */
    fun after(fill: Boolean, scale: Float): Boolean = when {
        scale > FILL_ABOVE -> true
        scale < FIT_BELOW -> false
        else -> fill
    }

    fun hint(fill: Boolean): String = if (fill) "Fill" else "Fit"
}

private const val HINT_MS = 1_000L

/** A small centered label that shows [hint] for about a second, then fades. [onDone] clears it. */
@Composable
fun ZoomHint(hint: String?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf("") }
    LaunchedEffect(hint) {
        if (hint == null) return@LaunchedEffect
        last = hint
        delay(HINT_MS)
        onDone()
    }
    AnimatedVisibility(
        visible = hint != null,
        modifier = modifier,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(300)),
    ) {
        Text(
            last,
            color = KhColors.TextPrimary,
            fontSize = 20.sp,
            modifier = Modifier
                .background(Color(0xB3000000), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
