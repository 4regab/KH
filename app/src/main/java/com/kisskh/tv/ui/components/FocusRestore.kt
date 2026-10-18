@file:OptIn(ExperimentalComposeUiApi::class)

package com.kisskh.tv.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import kotlinx.coroutines.flow.first

/*
 * Focus bookkeeping shared by all screens.
 *
 * - A screen opened from the rail, or reached with Back, puts focus on one item by itself:
 *   the item it had when the user left (the `focusKey` saved in the Dest), or a default.
 * - [FocusTracker] remembers the last focused item while the screen is up, so the shell can save it
 *   when the rail switches screens.
 *
 * Phones in touch mode skip the automatic focus: a ring and a keyboard popping up after a tap look wrong.
 */

/** The last focused item key of a screen. A plain field on purpose: writing it redraws nothing. */
@Stable
class FocusTracker {
    var key: String? = null
}

/**
 * Set by the current screen, for the shell:
 * - [save] runs just before the rail switches to another screen, so this screen can be restored later.
 * - [enter] runs when the user leaves the rail for the content: it puts focus back on the last focused item.
 *   It returns false if the screen has nothing to focus.
 */
class FocusSaver {
    var save: (() -> Unit)? = null
    var enter: (() -> Boolean)? = null
}

val LocalFocusSaver = staticCompositionLocalOf { FocusSaver() }

/**
 * Call from a screen. [save] runs when the user picks another rail item. When the user comes back from
 * the rail, focus goes to the item [tracker] last saw (or the screen's first choice if none yet).
 */
@Composable
fun RegisterFocusSaver(tracker: FocusTracker, restore: FocusRestore, save: () -> Unit) {
    val saver = LocalFocusSaver.current
    val latest = rememberUpdatedState(save)
    DisposableEffect(saver) {
        val mySave: () -> Unit = { latest.value() }
        val myEnter: () -> Boolean = {
            restore.request(tracker.key ?: restore.firstChoice)
            true
        }
        saver.save = mySave
        saver.enter = myEnter
        onDispose {
            if (saver.save === mySave) saver.save = null
            if (saver.enter === myEnter) saver.enter = null
        }
    }
}

/** True when a screen may move focus by itself: always on TV, on a phone only after a key press. */
@Composable
fun rememberAutoFocusAllowed(): () -> Boolean {
    val tv = rememberIsTelevision()
    val input = LocalInputModeManager.current
    return { tv || input.inputMode == InputMode.Keyboard }
}

/**
 * "Put focus on the item with this key once." Starts with the screen's first choice ([firstChoice]).
 * [request] asks for another item later. [pending] turns false after the item took focus, so
 * recomposing or scrolling the item back in never takes focus again.
 */
@Stable
class FocusRestore(val firstChoice: String?) {
    var key: String? by mutableStateOf(firstChoice)
        private set
    var pending: Boolean by mutableStateOf(firstChoice != null)
        private set

    fun request(target: String?) {
        if (target == null) return
        key = target
        pending = true
    }

    fun done() {
        pending = false
    }
}

@Composable
fun rememberFocusRestore(key: String?): FocusRestore = remember { FocusRestore(key) }

/**
 * Give this item focus once if it is the one [restore] is waiting for. Add it to the item's modifier
 * (before its clickable). Costs nothing for every other item.
 */
@Composable
fun Modifier.focusIfRestoring(restore: FocusRestore, itemKey: String): Modifier {
    if (!restore.pending || restore.key != itemKey) return this
    val requester = remember { FocusRequester() }
    val allowed = rememberAutoFocusAllowed()
    LaunchedEffect(Unit) {
        withFrameNanos { } // let the item be laid out first
        if (allowed()) runCatching { requester.requestFocus() }
        restore.done()
    }
    return this.focusRequester(requester)
}

/** Make sure item [index] is on screen (and so composed) so it can take focus. Scrolls only if needed. */
suspend fun LazyGridState.revealIndex(index: Int) {
    snapshotFlow { layoutInfo.visibleItemsInfo.isNotEmpty() || layoutInfo.totalItemsCount == 0 }.first { it }
    if (index in 0 until layoutInfo.totalItemsCount && layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

suspend fun LazyListState.revealIndex(index: Int) {
    snapshotFlow { layoutInfo.visibleItemsInfo.isNotEmpty() || layoutInfo.totalItemsCount == 0 }.first { it }
    if (index in 0 until layoutInfo.totalItemsCount && layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}
