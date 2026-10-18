package com.kisskh.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/**
 * A filter chip that opens a short list of choices ("Country: All" -> South Korea, China, ...).
 *
 * - Select opens the list and moves focus into it, onto the current choice.
 * - Back or a tap outside closes it. So does picking a choice.
 * - When the list closes, focus goes back to the chip, so the user keeps their place.
 *
 * [chipKey] and [restore] let the screen give this chip the first focus (see [focusIfRestoring]).
 */
@Composable
fun <T> FilterDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    chipKey: String? = null,
    restore: FocusRestore? = null,
    onFocused: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    var wasOpen by remember { mutableStateOf(false) }
    val chipRequester = remember { FocusRequester() }
    // The list is its own window. Once it is gone, hand focus back to the chip.
    LaunchedEffect(open) {
        if (open) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            runCatching { chipRequester.requestFocus() }
        }
    }
    Box(modifier) {
        KhChip(
            text = "$label: ${optionLabel(selected)}",
            onClick = { open = true },
            outlined = true,
            marked = options.isNotEmpty() && selected != options.first(),
            trailingIcon = Icons.Filled.ArrowDropDown,
            modifier = Modifier
                .then(if (restore != null && chipKey != null) Modifier.focusIfRestoring(restore, chipKey) else Modifier)
                .focusRequester(chipRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .semantics { contentDescription = "$label, ${optionLabel(selected)}" },
        )
        if (open) {
            Popup(
                popupPositionProvider = BelowAnchor,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                // Back closes the list and leaves the screen where it was. Registered inside the
                // popup so it runs before the screen's own Back.
                BackHandler { open = false }
                val appear = remember { Animatable(0f) }
                LaunchedEffect(Unit) { appear.animateTo(1f, tween(KhMotion.PopoverMillis, easing = KhMotion.EaseOutStrong)) }
                Box(Modifier.graphicsLayer { alpha = appear.value }) {
                    OptionList(
                        options = options,
                        selected = selected,
                        optionLabel = optionLabel,
                        onPick = { picked ->
                            open = false
                            onSelect(picked)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> OptionList(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
) {
    val selectedRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedRequester.requestFocus() } }
    Column(
        Modifier
            .padding(top = KhTheme.spacing.xs, bottom = KhTheme.spacing.l) // room for the shadow
            .shadow(12.dp, KhTheme.shapes.tile)
            .background(KhColors.Elevated, KhTheme.shapes.tile)
            .width(240.dp)
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(KhTheme.spacing.s)
            .focusGroup(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            KhFocusable(
                onClick = { onPick(option) },
                shape = KhTheme.shapes.button,
                scaleAndLift = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isSelected) Modifier.focusRequester(selectedRequester) else Modifier),
            ) { focused ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (focused) KhColors.Surface else KhColors.Elevated)
                        .padding(horizontal = KhTheme.spacing.m, vertical = KhTheme.spacing.s + 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(optionLabel(option), style = KhTheme.typography.label, color = KhColors.TextPrimary, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(20.dp), tint = KhColors.TextPrimary)
                    }
                }
            }
        }
    }
}

/** Puts the list just below the chip. If it does not fit, above it. Always inside the window. */
private object BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
