package com.kisskh.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * D-pad [key] moves focus [direction] as usual. When Compose's focus search finds nothing that way,
 * [fallback] runs instead of the key being dropped. Only keys the focused item did not use arrive here.
 *
 * Used where the target is not "next to" the item in the geometric sense: the TV rail, whose items are
 * laid out 260 dp wide (the rail only draws 72 dp of that until it opens), and the filter bar.
 */
@Composable
fun Modifier.focusFallback(key: Key, direction: FocusDirection, fallback: () -> Unit): Modifier {
    val focusManager = LocalFocusManager.current
    return onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == key) {
            if (!focusManager.moveFocus(direction)) fallback()
            true
        } else {
            false
        }
    }
}
