package com.kisskh.tv.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * tv-material3 Card/Button only fire onClick from a remote's OK key, never from a touch tap.
 * This adds tap support for phones/tablets without changing focus or remote behaviour on TV.
 * Drags past touch slop cancel the tap, so list scrolling still works.
 */
fun Modifier.onTap(onClick: () -> Unit): Modifier =
    pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
