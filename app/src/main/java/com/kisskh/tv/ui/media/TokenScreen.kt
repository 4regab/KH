package com.kisskh.tv.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.rememberAutoFocusAllowed
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.delay

private const val FIELD = "field"
private const val FOCUS_TRIES = 4
private const val FOCUS_STEP_MS = 120L

@Composable
fun TokenScreen(vm: AppViewModel) {
    val saved by vm.media.token.collectAsStateWithLifecycle()
    var draft by remember(saved) { mutableStateOf(saved) }
    // The field is the only reason to be here, so it takes focus. Back from the rail returns to it.
    val tracker = remember { FocusTracker() }
    val restore = rememberFocusRestore(FIELD)
    RegisterFocusSaver(tracker, restore) { }
    val field = remember { FocusRequester() }
    val autoFocus = rememberAutoFocusAllowed()
    // This screen has nothing to load, so it appears in the same frame the old screen's card leaves.
    // The system hands focus to the rail at that moment; take it back once the screen has settled.
    LaunchedEffect(Unit) {
        if (!autoFocus()) return@LaunchedEffect
        repeat(FOCUS_TRIES) {
            delay(FOCUS_STEP_MS)
            if (tracker.key == FIELD) return@LaunchedEffect
            runCatching { field.requestFocus() }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(start = screenStart(), end = 32.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Febbox token", style = KhTheme.typography.heading, color = KhColors.TextPrimary)
        Text(
            "CineJoy asks Febbox for the video. Paste the token from the site (saved in the browser as cinejoy_fem_token). Without it, Febbox refuses the stream.",
            style = KhTheme.typography.body,
            color = KhColors.TextSecondary,
        )
        KhField(
            value = draft,
            onValueChange = { draft = it },
            hint = "Febbox token",
            modifier = Modifier
                .fillMaxWidth()
                .focusIfRestoring(restore, FIELD)
                .focusRequester(field)
                .onFocusChanged { tracker.key = if (it.isFocused) FIELD else null },
        )
        KhButton(
            "Save",
            primary = true,
            enabled = draft.isNotBlank(),
            onClick = {
                vm.media.saveToken(draft)
                vm.onBack()
            },
        )
        if (saved.isNotEmpty()) {
            KhButton("Clear token", onClick = {
                draft = ""
                vm.media.saveToken("")
            })
        }
    }
}
