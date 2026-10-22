package com.kisskh.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme
import com.kisskh.tv.ui.theme.khFocusEffect
import com.kisskh.tv.ui.theme.rememberKhFocusProgress
import com.kisskh.tv.ui.theme.rememberKhPressProgress
import kotlinx.coroutines.flow.first

/** Closing is quicker than opening ([KhMotion.PopoverMillis]): the user has already decided. */
private const val CloseMillis = 100

/** The list starts a little smaller than its final size, never from nothing. */
private const val StartScale = 0.95f

/**
 * A filter chip that opens a list of choices ("Country: All" -> South Korea, China, ...).
 *
 * - Select (or a tap) opens the list and moves focus into it, onto the current choice. A long list
 *   (Year, Genre) scrolls, and starts with the current choice in view.
 * - Back, a tap outside, or picking a choice closes it. The chosen row shows a check.
 * - The list grows from the chip (95% to 100% and fades in, 150 ms) and leaves faster (100 ms).
 * - When the list closes, focus goes back to the chip, so the user keeps their place.
 *
 * The chip is highlighted ([active]) when [selected] is not the first option, which is the "no filter"
 * choice. [chipText] is what the chip says; by default "Label: choice".
 *
 * [chipKey] and [restore] let the screen give this chip the first focus (see [focusIfRestoring]).
 * Pass [chipRequester] to move focus to the chip from the screen.
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
    chipText: String = "$label: ${optionLabel(selected)}",
    active: Boolean = options.isNotEmpty() && selected != options.first(),
    chipRequester: FocusRequester? = null,
) {
    // `open`: the list is on screen (also while it fades out). `shown`: the list should be visible.
    var open by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    var wasOpen by remember { mutableStateOf(false) }
    val requester = chipRequester ?: remember { FocusRequester() }
    val position = remember { BelowAnchor() }
    // The list is its own window. Once it is gone, hand focus back to the chip.
    LaunchedEffect(open) {
        if (open) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            runCatching { requester.requestFocus() }
        }
    }
    Box(modifier) {
        FilterChip(
            text = chipText,
            onClick = {
                open = true
                shown = true
            },
            active = active,
            trailingIcon = Icons.Filled.ArrowDropDown,
            modifier = Modifier
                .then(if (restore != null && chipKey != null) Modifier.focusIfRestoring(restore, chipKey) else Modifier)
                .focusRequester(requester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .semantics { contentDescription = "$label, ${optionLabel(selected)}" },
        )
        if (open) {
            Popup(
                popupPositionProvider = position,
                onDismissRequest = { shown = false },
                properties = PopupProperties(focusable = true),
            ) {
                // Back closes the list and leaves the screen where it was. Registered inside the
                // popup so it runs before the screen's own Back.
                BackHandler { shown = false }
                val appear = remember { Animatable(0f) }
                LaunchedEffect(shown) {
                    if (shown) {
                        appear.animateTo(1f, tween(KhMotion.PopoverMillis, easing = KhMotion.EaseOutStrong))
                    } else {
                        appear.animateTo(0f, tween(CloseMillis, easing = KhMotion.EaseOutStrong))
                        open = false
                    }
                }
                Box(
                    Modifier.graphicsLayer {
                        val t = appear.value
                        alpha = t
                        scaleX = StartScale + (1f - StartScale) * t
                        scaleY = scaleX
                        transformOrigin = position.origin
                    },
                ) {
                    OptionList(
                        options = options,
                        selected = selected,
                        optionLabel = optionLabel,
                        onPick = { picked ->
                            shown = false
                            onSelect(picked)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The pill every filter uses. Outlined; [active] (a filter is set) turns the outline brand red with a
 * faint red fill; focus turns it white, like every chip. The touch area is 48 dp tall on a phone,
 * even though the pill itself is slimmer.
 */
@Composable
fun FilterChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    trailingIcon: ImageVector? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val tv = isTv()
    val touchHeight = if (tv) 40.dp else 48.dp
    val pillHeight = if (tv) 40.dp else 36.dp
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focus = rememberKhFocusProgress(focused)
    val press = rememberKhPressProgress(pressed)
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .defaultMinSize(minHeight = touchHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = (touchHeight - pillHeight) / 2),
        contentAlignment = Alignment.Center,
    ) {
        val shape = KhTheme.shapes.chip
        val fill = when {
            focused -> KhColors.TextPrimary
            active -> KhColors.Brand.copy(alpha = 0.14f)
            else -> KhColors.Background.copy(alpha = 0f)
        }
        val outline = when {
            focused -> KhColors.TextPrimary
            active -> KhColors.Brand
            else -> KhColors.ChipOutline
        }
        val content = if (focused) KhColors.OnLight else KhColors.TextPrimary
        Row(
            Modifier
                .height(pillHeight)
                .khFocusEffect({ focus.value }, shape, KhTheme.focus, scaleAndLift = false, press = { press.value })
                .clip(shape)
                .background(fill, shape)
                .border(1.dp, outline, shape)
                .padding(start = 14.dp, end = if (trailingIcon != null) 6.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = KhTheme.typography.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
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
    // Focus starts on the chosen row (or the first when none is chosen).
    val selectedIndex = remember(options, selected) { options.indexOf(selected) }
    val focusIndex = selectedIndex.coerceAtLeast(0)
    // The list opens with the chosen row already in view, so a long list (Year) never scrolls on open.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (focusIndex - 2).coerceAtLeast(0))
    val focusRow = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Lazy rows exist only after the first layout.
        snapshotFlow { list.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        runCatching { focusRow.requestFocus() }
    }
    val rowHeight = if (isTv()) 44.dp else 48.dp
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp.coerceIn(160.dp, 420.dp)
    LazyColumn(
        state = list,
        modifier = Modifier
            .padding(top = KhTheme.spacing.xs, bottom = KhTheme.spacing.l) // room for the shadow
            .shadow(12.dp, KhTheme.shapes.tile)
            .background(KhColors.Elevated, KhTheme.shapes.tile)
            .width(240.dp)
            .heightIn(max = maxHeight)
            .focusGroup(),
        contentPadding = PaddingValues(KhTheme.spacing.s),
    ) {
        items(options.size) { index ->
            val option = options[index]
            val isSelected = index == selectedIndex
            KhFocusable(
                onClick = { onPick(option) },
                shape = KhTheme.shapes.button,
                scaleAndLift = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { this.selected = isSelected }
                    .then(if (index == focusIndex) Modifier.focusRequester(focusRow) else Modifier),
            ) { focused ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = rowHeight)
                        .background(if (focused) KhColors.Surface else KhColors.Elevated)
                        .padding(horizontal = KhTheme.spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        optionLabel(option),
                        style = KhTheme.typography.label,
                        color = KhColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(20.dp), tint = KhColors.TextPrimary)
                    }
                }
            }
        }
    }
}

/**
 * Puts the list just below the chip. If it does not fit, above it. Always inside the window.
 * [origin] is the chip's centre in the list's own box, so the list grows out of the chip.
 */
private class BelowAnchor : PopupPositionProvider {
    var origin by mutableStateOf(TransformOrigin(0.5f, 0f))

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom
        val fitsBelow = below + popupContentSize.height <= windowSize.height
        val y = if (fitsBelow) below else (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        if (popupContentSize.width > 0) {
            origin = TransformOrigin(
                pivotFractionX = ((anchorBounds.center.x - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f),
                pivotFractionY = if (fitsBelow) 0f else 1f,
            )
        }
        return IntOffset(x, y)
    }
}
