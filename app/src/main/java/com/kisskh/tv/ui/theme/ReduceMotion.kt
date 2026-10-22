package com.kisskh.tv.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user turned animations off in system settings ("Remove animations", which sets
 * `Settings.Global.ANIMATOR_DURATION_SCALE` to 0). [KhTheme] provides it.
 *
 * What Compose already does with that setting (Compose UI 1.7.6, BOM 2024.12.01): every Compose
 * animation (`animate*AsState`, `Animatable`, `Transition`, `Crossfade`, `AnimatedVisibility`,
 * `animateScrollBy`, `InfiniteTransition`) reads the scale from its coroutine and jumps to its end
 * value when it is 0. So tweens and the spinner need no code.
 *
 * What it does not cover, and why this exists:
 * - Coil's image fade is an Android drawable animation. Pass `if (reduce) 0 else fadeMillis`.
 * - Movement that is a state, not an animation: a zoom or a rise that is part of the resting look.
 *   Skip it, and keep opacity only.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** The current value of the system setting. Updates while the app runs when the user changes it. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(animationsRemoved(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = animationsRemoved(context)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

private fun animationsRemoved(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
