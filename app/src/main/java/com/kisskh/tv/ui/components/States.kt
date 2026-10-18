package com.kisskh.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

/** A turning ring. Used while something loads. */
@Composable
fun KhSpinner(modifier: Modifier = Modifier, size: Dp = 40.dp, color: Color = KhColors.TextPrimary) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spinnerAngle",
    )
    Canvas(modifier.size(size)) {
        val stroke = 4.dp.toPx()
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Full-area loading state: a spinner and, if given, a short line of text. */
@Composable
fun LoadingState(modifier: Modifier = Modifier.fillMaxSize(), message: String? = null) {
    Box(modifier.semantics { contentDescription = message ?: "Loading" }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KhTheme.spacing.m)) {
            KhSpinner()
            if (message != null) Text(message, style = KhTheme.typography.body, color = KhColors.TextSecondary)
        }
    }
}

/** Nothing to show, and that is not a failure. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.padding(KhTheme.spacing.l), contentAlignment = Alignment.Center) {
        Text(message, style = KhTheme.typography.title, color = KhColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * Something failed: say so in plain words and offer Retry. With [autoFocus] the Retry button takes
 * focus when it appears (use it for full-screen errors, not for errors below a row of filters).
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    autoFocus: Boolean = false,
    retryLabel: String = "Retry",
) {
    val requester = remember { FocusRequester() }
    val allowed = rememberAutoFocusAllowed()
    if (autoFocus) {
        LaunchedEffect(Unit) { if (allowed()) runCatching { requester.requestFocus() } }
    }
    Box(modifier.padding(KhTheme.spacing.l), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KhTheme.spacing.l)) {
            Text(message, style = KhTheme.typography.title, color = KhColors.TextPrimary, textAlign = TextAlign.Center)
            KhButton(retryLabel, onClick = onRetry, primary = true, modifier = Modifier.focusRequester(requester))
        }
    }
}
