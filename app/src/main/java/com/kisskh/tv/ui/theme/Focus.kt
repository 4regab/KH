package com.kisskh.tv.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How focus looks everywhere in KH: a small zoom, a bright outline, a soft shadow, all easing in
 * over [durationMillis] with [KhMotion.EaseOutStrong]. Red is never used for focus.
 *
 * A focused item grows by [scale] and its outline sits half outside its bounds, so leave
 * [KhSpacing.focusBleed] around it or the parent will cut it off.
 */
@Immutable
data class KhFocusSpec(
    val scale: Float = 1.08f,
    val outlineWidth: Dp = 2.dp,
    val outlineColor: Color = KhColors.Outline,
    val elevation: Dp = 8.dp,
    val durationMillis: Int = KhMotion.FocusMillis,
)

/**
 * 0 when not focused, 1 when focused, easing between. Pass `{ progress.value }` to [khFocusEffect]
 * so the animation runs without recomposing.
 */
@Composable
fun rememberKhFocusProgress(focused: Boolean, spec: KhFocusSpec = KhTheme.focus): State<Float> =
    animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(spec.durationMillis, easing = KhMotion.EaseOutStrong),
        label = "khFocus",
    )

/** 0 when not pressed, 1 while pressed (finger down or OK key down). Drives the 0.96 press scale. */
@Composable
fun rememberKhPressProgress(pressed: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = KhMotion.press(),
        label = "khPress",
    )

/**
 * Draws the KH focus look, driven by [progress] (0..1, see [rememberKhFocusProgress]):
 * zoom and shadow when [scaleAndLift] is true, plus the outline (drawn over the content).
 * [press] (0..1) shrinks the item to [KhMotion.PressScale], whatever its focus state.
 * Put it first in the modifier chain, before clip or background, so the outline is not clipped.
 */
fun Modifier.khFocusEffect(
    progress: () -> Float,
    shape: Shape,
    spec: KhFocusSpec = KhFocusSpec(),
    scaleAndLift: Boolean = true,
    press: () -> Float = { 0f },
): Modifier = this
    .graphicsLayer {
        val p = progress()
        val focusScale = if (scaleAndLift) 1f + (spec.scale - 1f) * p else 1f
        val s = focusScale * (1f - (1f - KhMotion.PressScale) * press())
        scaleX = s
        scaleY = s
        if (scaleAndLift) {
            shadowElevation = spec.elevation.toPx() * p
            this.shape = shape
            clip = false
        }
    }
    .drawWithContent {
        drawContent()
        val p = progress()
        if (p > 0f) {
            drawOutline(
                outline = shape.createOutline(size, layoutDirection, this),
                color = spec.outlineColor,
                alpha = p,
                style = Stroke(width = spec.outlineWidth.toPx()),
            )
        }
    }

/** Outline only, no zoom. For text fields, chips and rail items that must not move. */
fun Modifier.khFocusBorder(
    progress: () -> Float,
    shape: Shape,
    spec: KhFocusSpec = KhFocusSpec(),
): Modifier = khFocusEffect(progress, shape, spec, scaleAndLift = false)

/**
 * A focusable, clickable box with the KH focus look. It works with D-pad (Select) and touch (tap)
 * and fires [onClick] once either way. Use it for cards, tiles, chips and list rows.
 * Pressing it (finger or Select) shrinks it a little for 100 ms.
 *
 * [content] gets `focused` so it can change text or colours. Add size, background and semantics
 * (`contentDescription`) through [modifier]. The content is clipped to [shape].
 */
@Composable
fun KhFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = KhTheme.shapes.card,
    enabled: Boolean = true,
    scaleAndLift: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val progress = rememberKhFocusProgress(focused)
    val press = rememberKhPressProgress(pressed)
    val spec = KhTheme.focus
    Box(
        modifier = modifier
            .khFocusEffect({ progress.value }, shape, spec, scaleAndLift, { press.value })
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .clip(shape),
    ) {
        content(focused)
    }
}
